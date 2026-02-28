package com.example.smartdl.data.repository

import com.example.smartdl.data.db.DownloadEntity
import com.example.smartdl.domain.model.DownloadStatus
import com.example.smartdl.domain.model.DownloadTask

fun DownloadEntity.toDomain(): DownloadTask {
    return DownloadTask(
        id = id,
        url = url,
        fileName = fileName,
        status = DownloadStatus.valueOf(status),
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        speedBytesPerSec = speedBytesPerSec,
        isVideo = isVideo,
        errorMessage = errorMessage
    )
}
