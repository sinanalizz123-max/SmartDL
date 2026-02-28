package com.example.smartdl.data.repository

import com.example.smartdl.data.db.DownloadEntity
import com.example.smartdl.data.db.HistoryEntity
import com.example.smartdl.domain.model.DownloadStatus
import com.example.smartdl.domain.model.DownloadTask
import com.example.smartdl.domain.model.HistoryItem

fun DownloadEntity.toDomain(): DownloadTask {
    return DownloadTask(
        id = id,
        url = url,
        domain = domain,
        fileName = fileName,
        mimeType = mimeType,
        status = DownloadStatus.valueOf(status),
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        speedBytesPerSec = speedBytesPerSec,
        isVideo = isVideo,
        outputUri = outputUri,
        errorMessage = errorMessage
    )
}

fun HistoryEntity.toDomain(): HistoryItem {
    return HistoryItem(
        url = url,
        title = title,
        timestamp = timestamp
    )
}
