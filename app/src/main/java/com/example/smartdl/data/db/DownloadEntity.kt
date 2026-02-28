package com.example.smartdl.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val domain: String,
    val fileName: String,
    val mimeType: String?,
    val status: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val isVideo: Boolean,
    val userAgent: String?,
    val referer: String?,
    val cookies: String?,
    val tempPath: String?,
    val outputUri: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)
