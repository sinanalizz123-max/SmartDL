package com.example.smartdl.data.source

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class YtDlpRunner(
    private val context: Context
) {

    private val processes = ConcurrentHashMap<Long, Process>()
    private val progressRegex = Regex("(\\d{1,3}(?:\\.\\d+)?)%")

    suspend fun download(
        downloadId: Long,
        url: String,
        outputDir: File,
        onProgress: (percent: Int) -> Unit
    ): Result<Unit> {
        return try {
            val bin = ensureBinary()
            val process = ProcessBuilder(
                bin.absolutePath,
                "--no-playlist",
                "-o",
                File(outputDir, "%(title)s.%(ext)s").absolutePath,
                url
            )
                .redirectErrorStream(true)
                .start()

            processes[downloadId] = process
            val ctx = currentCoroutineContext()

            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    if (!ctx.isActive) {
                        process.destroyForcibly()
                        throw CancellationException("yt-dlp cancelled")
                    }
                    val line = reader.readLine() ?: break
                    val match = progressRegex.find(line)
                    val percent = match?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    if (percent != null) {
                        onProgress(percent.coerceIn(0f, 100f).toInt())
                    }
                }
            }

            val code = process.waitFor()
            processes.remove(downloadId)
            if (code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("yt-dlp failed with code $code"))
            }
        } catch (e: CancellationException) {
            processes.remove(downloadId)
            throw e
        } catch (e: Exception) {
            processes.remove(downloadId)
            Result.failure(e)
        }
    }

    fun cancel(downloadId: Long) {
        processes.remove(downloadId)?.destroyForcibly()
    }

    private fun ensureBinary(): File {
        val bin = File(context.filesDir, "yt-dlp")
        if (!bin.exists()) {
            context.assets.open("yt-dlp").use { input ->
                bin.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            try {
                ProcessBuilder("chmod", "755", bin.absolutePath).start().waitFor()
            } catch (_: Exception) {
                // Fallback to Java executable flag.
            }
            bin.setExecutable(true)
        }
        return bin
    }
}
