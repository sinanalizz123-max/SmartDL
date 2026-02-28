package com.example.smartdl.domain.repository

import com.example.smartdl.domain.model.DownloadTask
import com.example.smartdl.domain.model.HistoryItem
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadTask>>
    fun observeHistory(limit: Int = 200): Flow<List<HistoryItem>>

    suspend fun enqueueDownload(
        url: String,
        fileName: String?,
        mimeType: String?,
        userAgent: String?,
        referer: String?,
        cookies: String?
    ): Result<Long>

    suspend fun updateDomainHeaders(
        domain: String,
        userAgent: String?,
        referer: String?,
        cookies: String?
    )

    suspend fun addHistory(url: String, title: String?)

    suspend fun pauseDownload(id: Long)
    suspend fun resumeDownload(id: Long)
    suspend fun cancelDownload(id: Long)

    suspend fun startQueue(maxParallel: Int)
    suspend fun stopQueue()

    suspend fun resumeIncomplete(autoResume: Boolean)

    suspend fun getOrphanTempFiles(): List<String>
    suspend fun clearIncompleteCache(): Int
}
