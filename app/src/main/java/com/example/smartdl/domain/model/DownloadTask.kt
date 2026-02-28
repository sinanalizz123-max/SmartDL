package com.example.smartdl.domain.model

data class DownloadTask(
    val id: Long,
    val url: String,
    val domain: String,
    val fileName: String,
    val mimeType: String?,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val isVideo: Boolean,
    val outputUri: String?,
    val errorMessage: String?
)
