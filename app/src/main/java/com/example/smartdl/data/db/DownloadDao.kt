package com.example.smartdl.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloads(items: List<DownloadEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(items: List<DownloadChunkEntity>)

    @Query("SELECT * FROM downloads ORDER BY id DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status != 'COMPLETED' AND status != 'FAILED'")
    suspend fun getIncomplete(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE url = :url AND status != 'COMPLETED' AND status != 'FAILED' LIMIT 1")
    suspend fun findActiveByUrl(url: String): DownloadEntity?

    @Query("UPDATE downloads SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long)

    @Query("UPDATE downloads SET bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, speedBytesPerSec = :speed, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(
        id: Long,
        bytesDownloaded: Long,
        totalBytes: Long,
        speed: Long,
        status: String,
        updatedAt: Long
    )

    @Query("UPDATE downloads SET errorMessage = :error, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateError(id: Long, error: String?, status: String, updatedAt: Long)

    @Query("SELECT * FROM download_chunks WHERE downloadId = :downloadId ORDER BY chunkIndex ASC")
    suspend fun getChunks(downloadId: Long): List<DownloadChunkEntity>

    @Query("UPDATE download_chunks SET downloadedBytes = :downloadedBytes, status = :status WHERE downloadId = :downloadId AND chunkIndex = :chunkIndex")
    suspend fun updateChunkProgress(
        downloadId: Long,
        chunkIndex: Int,
        downloadedBytes: Long,
        status: String
    )

    @Query("DELETE FROM download_chunks WHERE downloadId = :downloadId")
    suspend fun deleteChunks(downloadId: Long)

    @Transaction
    suspend fun replaceChunks(downloadId: Long, chunks: List<DownloadChunkEntity>) {
        deleteChunks(downloadId)
        insertChunks(chunks)
    }
}
