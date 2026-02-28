package com.example.smartdl.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.smartdl.data.db.DomainHeaderEntity
import com.example.smartdl.data.db.DownloadDao
import com.example.smartdl.data.db.DownloadEntity
import com.example.smartdl.data.db.HistoryEntity
import com.example.smartdl.data.source.CookieExporter
import com.example.smartdl.data.source.MediaStoreWriter
import com.example.smartdl.data.source.ParallelDownloader
import com.example.smartdl.data.source.TempFileManager
import com.example.smartdl.data.source.YtDlpRunner
import com.example.smartdl.domain.model.DownloadStatus
import com.example.smartdl.domain.model.DownloadTask
import com.example.smartdl.domain.model.HistoryItem
import com.example.smartdl.domain.repository.DownloadRepository
import com.example.smartdl.domain.util.hostOrNull
import com.example.smartdl.domain.util.isVideoUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class DownloadRepositoryImpl(
    private val context: Context,
    private val dao: DownloadDao,
    private val downloader: ParallelDownloader,
    private val ytDlpRunner: YtDlpRunner,
    private val tempFileManager: TempFileManager,
    private val mediaStoreWriter: MediaStoreWriter,
    private val cookieExporter: CookieExporter
) : DownloadRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private var queueJob: Job? = null
    private var maxParallel = 3

    override fun observeDownloads(): Flow<List<DownloadTask>> {
        return dao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    override fun observeHistory(limit: Int): Flow<List<HistoryItem>> {
        return dao.observeHistory(limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun enqueueDownload(
        url: String,
        fileName: String?,
        mimeType: String?,
        userAgent: String?,
        referer: String?,
        cookies: String?
    ): Result<Long> {
        if (!hasNetwork()) return Result.failure(IllegalStateException("No internet connection"))
        if (dao.findActiveByUrl(url) != null) return Result.failure(IllegalStateException("Already queued"))

        val domain = url.hostOrNull() ?: ""
        val isVideo = url.isVideoUrl()
        val now = System.currentTimeMillis()
        val defaults = dao.getDomainHeader(domain)

        val resolvedUa = userAgent ?: defaults?.userAgent
        val resolvedRef = referer ?: defaults?.referer
        val resolvedCookies = cookies ?: defaults?.cookies

        val entity = DownloadEntity(
            url = url,
            domain = domain,
            fileName = fileName ?: fileNameFromUrl(url, isVideo),
            mimeType = mimeType,
            status = DownloadStatus.QUEUED.name,
            bytesDownloaded = 0L,
            totalBytes = if (isVideo) 100L else -1L,
            speedBytesPerSec = 0L,
            isVideo = isVideo,
            userAgent = resolvedUa,
            referer = resolvedRef,
            cookies = resolvedCookies,
            tempPath = null,
            outputUri = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now
        )
        val id = dao.insertDownload(entity)
        return Result.success(id)
    }

    override suspend fun updateDomainHeaders(domain: String, userAgent: String?, referer: String?, cookies: String?) {
        val now = System.currentTimeMillis()
        dao.upsertDomainHeader(
            DomainHeaderEntity(
                domain = domain,
                userAgent = userAgent,
                referer = referer,
                cookies = cookies,
                updatedAt = now
            )
        )
    }

    override suspend fun addHistory(url: String, title: String?) {
        dao.insertHistory(
            HistoryEntity(
                url = url,
                title = title,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun pauseDownload(id: Long) {
        activeJobs.remove(id)?.cancel()
        ytDlpRunner.cancel(id)
        dao.updateStatus(id, DownloadStatus.PAUSED.name, System.currentTimeMillis())
    }

    override suspend fun resumeDownload(id: Long) {
        dao.updateStatus(id, DownloadStatus.QUEUED.name, System.currentTimeMillis())
    }

    override suspend fun cancelDownload(id: Long) {
        activeJobs.remove(id)?.cancel()
        ytDlpRunner.cancel(id)
        dao.updateError(id, "Cancelled", DownloadStatus.CANCELED.name, System.currentTimeMillis())
    }

    override suspend fun startQueue(maxParallel: Int) {
        this.maxParallel = maxParallel.coerceAtLeast(1)
        if (queueJob?.isActive == true) return
        queueJob = scope.launch {
            while (true) {
                tryStartNext()
                delay(500)
            }
        }
    }

    override suspend fun stopQueue() {
        queueJob?.cancel()
        queueJob = null
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        scope.cancel()
    }

    override suspend fun resumeIncomplete(autoResume: Boolean) {
        if (!autoResume) return
        val incomplete = dao.getIncomplete()
        incomplete.forEach { item ->
            if (item.status == DownloadStatus.PAUSED.name || item.status == DownloadStatus.DOWNLOADING.name) {
                dao.updateStatus(item.id, DownloadStatus.QUEUED.name, System.currentTimeMillis())
            }
        }
    }

    override suspend fun getOrphanTempFiles(): List<String> {
        val tempFiles = tempFileManager.listTempFiles()
        val incomplete = dao.getIncomplete()
        val referenced = incomplete.mapNotNull { it.tempPath }.toSet()
        val prefixes = incomplete.map { "dl_${it.id}_" }
        return tempFiles.map { it.absolutePath }.filter { path ->
            if (path in referenced) return@filter false
            val name = File(path).name
            prefixes.none { name.startsWith(it) }
        }
    }

    override suspend fun clearIncompleteCache(): Int {
        val incomplete = dao.getIncomplete()
        var deleted = 0
        incomplete.forEach { item ->
            val path = item.tempPath
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.delete()) {
                    deleted++
                }
            }
            dao.deleteChunks(item.id)
        }
        return deleted
    }

    private suspend fun tryStartNext() {
        val active = activeJobs.size
        val capacity = maxParallel - active
        if (capacity <= 0) return
        val next = dao.getNextQueued(capacity)
        next.forEach { item ->
            if (!activeJobs.containsKey(item.id)) {
                startDownload(item)
            }
        }
    }

    private fun startDownload(item: DownloadEntity) {
        val job = scope.launch {
            try {
                dao.updateStatus(item.id, DownloadStatus.DOWNLOADING.name, System.currentTimeMillis())
                val result = if (item.isVideo) {
                    startYtDlp(item)
                } else {
                    startHttp(item)
                }

                if (result.isSuccess) {
                    val outputFile = result.getOrNull()
                    if (outputFile == null || !outputFile.exists()) {
                        dao.updateError(item.id, "Output file missing", DownloadStatus.FAILED.name, System.currentTimeMillis())
                        return@launch
                    }
                    val displayName = if (item.isVideo) {
                        outputFile.name.removePrefix("dl_${item.id}_")
                    } else {
                        item.fileName
                    }
                    val outputUri = mediaStoreWriter.copyToDownloads(
                        file = outputFile,
                        displayName = displayName,
                        mimeType = item.mimeType
                    )
                    if (outputUri == null) {
                        dao.updateError(item.id, "Failed to save to Downloads", DownloadStatus.FAILED.name, System.currentTimeMillis())
                    } else {
                        dao.updateOutputUri(item.id, outputUri, System.currentTimeMillis())
                        dao.updateStatus(item.id, DownloadStatus.COMPLETED.name, System.currentTimeMillis())
                        outputFile.delete()
                    }
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
        activeJobs[item.id] = job
    }

    private suspend fun startHttp(item: DownloadEntity): Result<File> {
        val tempFile = ensureTempFile(item)
        val headers = buildHeaders(item)
        return try {
            downloader.download(item.id, item.url, tempFile, headers) { downloaded, total, speed ->
                dao.updateProgress(
                    id = item.id,
                    bytesDownloaded = downloaded,
                    totalBytes = if (total > 0) total else item.totalBytes,
                    speed = speed,
                    status = DownloadStatus.DOWNLOADING.name,
                    updatedAt = System.currentTimeMillis()
                )
            }
            Result.success(tempFile)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    private suspend fun startYtDlp(item: DownloadEntity): Result<File> {
        val dir = tempFileManager.ensureDir()
        val prefix = "dl_${item.id}_"
        val cookiesFile = File(dir, "cookies_${item.id}.txt")
        cookieExporter.exportDomainCookies(item.domain, item.cookies, cookiesFile)
        dao.updateTempPath(item.id, dir.absolutePath, System.currentTimeMillis())
        val result = ytDlpRunner.download(item.id, item.url, dir, cookiesFile) { percent ->
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
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        val output = dir.listFiles()?.filter { it.name.startsWith(prefix) }?.maxByOrNull { it.lastModified() }
        return if (output != null) Result.success(output) else Result.failure(IOException("yt-dlp output not found"))
    }

    private suspend fun ensureTempFile(item: DownloadEntity): File {
        val file = tempFileManager.tempFile(item.id, item.fileName)
        dao.updateTempPath(item.id, file.absolutePath, System.currentTimeMillis())
        return file
    }

    private fun buildHeaders(item: DownloadEntity): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        item.userAgent?.let { headers["User-Agent"] = it }
        item.referer?.let { headers["Referer"] = it }
        item.cookies?.let { headers["Cookie"] = it }
        return headers
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
