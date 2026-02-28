package com.example.smartdl.data.db

import androidx.room.Entity

@Entity(
    tableName = "download_chunks",
    primaryKeys = ["downloadId", "chunkIndex"]
)
data class DownloadChunkEntity(
    val downloadId: Long,
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val status: String
)
