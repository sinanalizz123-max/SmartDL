package com.example.smartdl.domain.repository

import com.example.smartdl.domain.model.DownloadTask
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadTask>>
    suspend fun startDownloads(urls: List<String>, parallelism: Int): Result<Unit>
    suspend fun pauseDownload(id: Long)
    suspend fun resumeDownload(id: Long)
    suspend fun cancelDownload(id: Long)
    suspend fun resumeIncomplete(autoResume: Boolean)
}
