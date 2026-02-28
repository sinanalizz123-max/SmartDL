package com.example.smartdl.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import com.example.smartdl.data.db.DownloadDao
import com.example.smartdl.data.db.DownloadEntity
import com.example.smartdl.data.source.ParallelDownloader
import com.example.smartdl.data.source.YtDlpRunner
import com.example.smartdl.domain.model.DownloadStatus
import com.example.smartdl.domain.model.DownloadTask
import com.example.smartdl.domain.repository.DownloadRepository
import com.example.smartdl.domain.util.isVideoUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class DownloadRepositoryImpl(
    private val context: Context,
    private val dao: DownloadDao,
    private val downloader: ParallelDownloader,
    private val ytDlpRunner: YtDlpRunner
) : DownloadRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private var semaphore = Semaphore(3)

    override fun observeDownloads(): Flow<List<DownloadTask>> {
        return dao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun startDownloads(urls: List<String>, parallelism: Int): Result<Unit> = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope Result.failure(IllegalArgumentException("No URLs"))
        if (!hasNetwork()) return@coroutineScope Result.failure(IllegalStateException("No internet connection"))

        semaphore = Semaphore(parallelism.coerceAtLeast(1))
        saveParallelism(parallelism)

        val now = System.currentTimeMillis()
        val entities = mutableListOf<DownloadEntity>()
        for (url in urls) {
            if (dao.findActiveByUrl(url) != null) continue
            val isVideo = url.isVideoUrl()
            entities.add(
                DownloadEntity(
                    url = url,
                    fileName = fileNameFromUrl(url, isVideo),
                    status = DownloadStatus.QUEUED.name,
                    bytesDownloaded = 0L,
                    totalBytes = if (isVideo) 100L else -1L,
                    speedBytesPerSec = 0L,
                    isVideo = isVideo,
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        if (entities.isEmpty()) return@coroutineScope Result.success(Unit)

        val ids = dao.insertDownloads(entities)
        val tasks = entities.zip(ids) { entity, id -> entity.copy(id = id) }
        tasks.forEach { enqueueDownload(it) }
        Result.success(Unit)
    }

    override suspend fun pauseDownload(id: Long) {
        activeJobs.remove(id)?.cancel()
        ytDlpRunner.cancel(id)
        dao.updateStatus(id, DownloadStatus.PAUSED.name, System.currentTimeMillis())
    }

    override suspend fun resumeDownload(id: Long) {
        val item = dao.getById(id) ?: return
        if (activeJobs.containsKey(id)) return
        enqueueDownload(item)
    }

    override suspend fun cancelDownload(id: Long) {
        activeJobs.remove(id)?.cancel()
        ytDlpRunner.cancel(id)
        dao.updateError(id, "Cancelled", DownloadStatus.FAILED.name, System.currentTimeMillis())
        deleteOutputFile(id)
    }

    override suspend fun resumeIncomplete(autoResume: Boolean) {
        if (!autoResume || !isAutoResumeEnabled()) return
        val parallelism = readParallelism()
        semaphore = Semaphore(parallelism.coerceAtLeast(1))
        val items = dao.getIncomplete()
        items.forEach { item ->
            if (!activeJobs.containsKey(item.id)) {
                enqueueDownload(item)
            }
        }
    }

    private fun enqueueDownload(item: DownloadEntity) {
        val job = scope.launch {
            semaphore.withPermit {
                try {
                    dao.updateStatus(item.id, DownloadStatus.DOWNLOADING.name, System.currentTimeMillis())
                    val result = if (item.isVideo) {
                        startYtDlp(item)
                    } else {
                        startHttp(item)
                    }
                    if (result.isSuccess) {
                        dao.updateStatus(item.id, DownloadStatus.COMPLETED.name, System.currentTimeMillis())
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Download failed"
                        dao.updateError(item.id, error, DownloadStatus.FAILED.name, System.currentTimeMillis())
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    dao.updateStatus(item.id, DownloadStatus.PAUSED.name, System.currentTimeMillis())
                } finally {
                    activeJobs.remove(item.id)
                }
            }
        }
        activeJobs[item.id] = job
    }

    private suspend fun startHttp(item: DownloadEntity): Result<Unit> {
        val dest = File(outputDir(), item.fileName)
        return try {
            downloader.download(item.id, item.url, dest) { downloaded, total, speed ->
                dao.updateProgress(
                    id = item.id,
                    bytesDownloaded = downloaded,
                    totalBytes = if (total > 0) total else item.totalBytes,
                    speed = speed,
                    status = DownloadStatus.DOWNLOADING.name,
                    updatedAt = System.currentTimeMillis()
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    private suspend fun startYtDlp(item: DownloadEntity): Result<Unit> {
        return ytDlpRunner.download(item.id, item.url, outputDir()) { percent ->
            scope.launch {
                dao.updateProgress(
                    id = item.id,
                    bytesDownloaded = percent.toLong(),
                    totalBytes = 100L,
                    speed = 0L,
                    status = DownloadStatus.DOWNLOADING.name,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    private fun outputDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!base.exists()) base.mkdirs()
        return base
    }

    private fun deleteOutputFile(id: Long) {
        scope.launch {
            val entity = dao.getById(id) ?: return@launch
            val file = File(outputDir(), entity.fileName)
            if (file.exists()) file.delete()
            dao.deleteChunks(id)
        }
    }

    private fun fileNameFromUrl(url: String, isVideo: Boolean): String {
        if (isVideo) return "video_${System.currentTimeMillis()}"
        return try {
            val path = URI(url).path
            val segment = path.substringAfterLast('/', "")
            if (segment.isBlank()) "download_${System.currentTimeMillis()}" else sanitize(segment)
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
        }
    }

    private fun sanitize(input: String): String {
        return input.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun saveParallelism(value: Int) {
        val prefs = context.getSharedPreferences("smartdl_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("parallelism", value).apply()
    }

    private fun readParallelism(): Int {
        val prefs = context.getSharedPreferences("smartdl_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("parallelism", 3)
    }

    private fun isAutoResumeEnabled(): Boolean {
        val prefs = context.getSharedPreferences("smartdl_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("auto_resume", true)
    }

    private fun mapError(e: Exception): Exception {
        if (e is IOException && e.message?.contains("ENOSPC") == true) {
            return IOException("Storage full")
        }
        if (e is java.net.UnknownHostException) {
            return IOException("No internet connection")
        }
        if (e is java.io.FileNotFoundException) {
            return IOException("Permission denied or path not found")
        }
        return e
    }
}
