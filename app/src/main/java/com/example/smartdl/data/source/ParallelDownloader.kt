package com.example.smartdl.data.source

import com.example.smartdl.data.db.DownloadChunkEntity
import com.example.smartdl.data.db.DownloadDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class ParallelDownloader(
    private val client: OkHttpClient,
    private val dao: DownloadDao
) {

    data class ProbeResult(
        val totalBytes: Long,
        val supportsRanges: Boolean
    )

    suspend fun download(
        downloadId: Long,
        url: String,
        destFile: File,
        headers: Map<String, String>,
        onProgress: suspend (downloaded: Long, total: Long, speedBytesPerSec: Long) -> Unit
    ): Result<Unit> = try {
        val probe = probe(url, headers)
        val chunks = prepareChunks(downloadId, probe)
        if (probe.totalBytes > 0) {
            RandomAccessFile(destFile, "rw").use { raf ->
                raf.setLength(probe.totalBytes)
            }
        }

        val speedTracker = SpeedTracker()
        val totalDownloaded = AtomicLong(chunks.sumOf { it.downloadedBytes })

        coroutineScope {
            chunks.forEach { chunk ->
                launch {
                    downloadChunk(url, destFile, chunk, probe, headers, totalDownloaded, speedTracker, onProgress)
                }
            }
        }

        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun probe(url: String, headers: Map<String, String>): ProbeResult {
        val head = Request.Builder().url(url).head().headers(headers.toHeaders()).build()
        try {
            client.newCall(head).execute().use { response ->
                if (response.isSuccessful) {
                    val total = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    val acceptRanges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    if (acceptRanges) {
                        val rangeProbe = rangeProbe(url, headers)
                        return rangeProbe ?: ProbeResult(totalBytes = total, supportsRanges = false)
                    }
                    return ProbeResult(totalBytes = total, supportsRanges = false)
                }
            }
        } catch (_: Exception) {
            // Fall through to range probe.
        }

        return rangeProbe(url, headers) ?: ProbeResult(totalBytes = -1L, supportsRanges = false)
    }

    private fun rangeProbe(url: String, headers: Map<String, String>): ProbeResult? {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toHeaders())
            .header("Range", "bytes=0-0")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code != 206) return null
                val contentRange = response.header("Content-Range")
                val total = contentRange?.substringAfter("/")?.toLongOrNull() ?: -1L
                ProbeResult(totalBytes = total, supportsRanges = true)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun prepareChunks(downloadId: Long, probe: ProbeResult): List<DownloadChunkEntity> {
        val existing = dao.getChunks(downloadId)
        if (existing.isNotEmpty()) {
            return existing
        }

        val total = probe.totalBytes
        val supportsRanges = probe.supportsRanges && total > 0
        if (!supportsRanges) {
            val single = listOf(
                DownloadChunkEntity(
                    downloadId = downloadId,
                    chunkIndex = 0,
                    startByte = 0L,
                    endByte = max(total - 1, 0),
                    downloadedBytes = 0L,
                    status = "PENDING"
                )
            )
            dao.replaceChunks(downloadId, single)
            return single
        }

        val chunkCount = chooseChunkCount(total)
        val chunkSize = total / chunkCount
        val chunks = (0 until chunkCount).map { index ->
            val start = index * chunkSize
            val end = if (index == chunkCount - 1) total - 1 else (start + chunkSize - 1)
            DownloadChunkEntity(
                downloadId = downloadId,
                chunkIndex = index,
                startByte = start,
                endByte = end,
                downloadedBytes = 0L,
                status = "PENDING"
            )
        }
        dao.replaceChunks(downloadId, chunks)
        return chunks
    }

    private fun chooseChunkCount(totalBytes: Long): Int {
        val mb = totalBytes / (1024 * 1024)
        return when {
            mb <= 0 -> 1
            mb < 20 -> 4
            mb < 200 -> 6
            else -> 8
        }
    }

    private suspend fun downloadChunk(
        url: String,
        destFile: File,
        chunk: DownloadChunkEntity,
        probe: ProbeResult,
        headers: Map<String, String>,
        totalDownloaded: AtomicLong,
        speedTracker: SpeedTracker,
        onProgress: suspend (downloaded: Long, total: Long, speedBytesPerSec: Long) -> Unit
    ) {
        if (chunk.status == "COMPLETED") return

        var attempt = 0
        val maxRetries = 3
        var localChunk = chunk

        while (attempt < maxRetries) {
            try {
                val rangeStart = localChunk.startByte + localChunk.downloadedBytes
                val rangeEnd = if (probe.supportsRanges) localChunk.endByte else -1L
                if (probe.supportsRanges && rangeStart > localChunk.endByte) {
                    dao.updateChunkProgress(localChunk.downloadId, localChunk.chunkIndex, localChunk.downloadedBytes, "COMPLETED")
                    return
                }

                val requestBuilder = Request.Builder().url(url).headers(headers.toHeaders())
                if (probe.supportsRanges) {
                    requestBuilder.header("Range", "bytes=$rangeStart-$rangeEnd")
                }
                val request = requestBuilder.build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 416) {
                        dao.updateChunkProgress(localChunk.downloadId, localChunk.chunkIndex, localChunk.downloadedBytes, "COMPLETED")
                        return
                    }
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Empty body")
                    val input = body.byteStream()
                    RandomAccessFile(destFile, "rw").use { raf ->
                        raf.seek(rangeStart)
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var chunkDownloaded = localChunk.downloadedBytes

                        while (input.read(buffer).also { bytesRead = it } >= 0) {
                            currentCoroutineContext().ensureActive()
                            raf.write(buffer, 0, bytesRead)
                            chunkDownloaded += bytesRead
                            val total = totalDownloaded.addAndGet(bytesRead.toLong())

                            if (shouldEmitProgress()) {
                                dao.updateChunkProgress(localChunk.downloadId, localChunk.chunkIndex, chunkDownloaded, "DOWNLOADING")
                                val speed = speedTracker.update(total)
                                onProgress(total, probe.totalBytes, speed)
                            }

                            if (probe.supportsRanges && (localChunk.startByte + chunkDownloaded) > localChunk.endByte) {
                                break
                            }
                        }

                        localChunk = localChunk.copy(downloadedBytes = chunkDownloaded)
                        dao.updateChunkProgress(localChunk.downloadId, localChunk.chunkIndex, chunkDownloaded, "COMPLETED")
                        val speed = speedTracker.update(totalDownloaded.get())
                        onProgress(totalDownloaded.get(), probe.totalBytes, speed)
                        return
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxRetries) {
                    throw e
                }
            }
        }
    }

    private val lastProgressEmit = AtomicLong(0L)

    private fun shouldEmitProgress(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastProgressEmit.get()
        return if (now - last > 500) {
            lastProgressEmit.set(now)
            true
        } else {
            false
        }
    }
}
