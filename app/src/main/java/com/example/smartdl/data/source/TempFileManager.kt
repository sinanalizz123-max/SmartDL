package com.example.smartdl.data.source

import android.content.Context
import java.io.File

class TempFileManager(
    private val context: Context
) {
    private val cacheDir: File = File(context.cacheDir, "downloads")

    fun ensureDir(): File {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    fun tempFile(downloadId: Long, fileName: String): File {
        ensureDir()
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(cacheDir, "dl_${downloadId}_$safeName.part")
    }

    fun listTempFiles(): List<File> {
        if (!cacheDir.exists()) return emptyList()
        return cacheDir.listFiles()?.toList() ?: emptyList()
    }
}
