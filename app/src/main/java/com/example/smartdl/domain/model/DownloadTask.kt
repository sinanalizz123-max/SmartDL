package com.example.smartdl.domain.model

data class DownloadTask(
    val id: Long,
    val url: String,
    val fileName: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val isVideo: Boolean,
    val errorMessage: String?
)
