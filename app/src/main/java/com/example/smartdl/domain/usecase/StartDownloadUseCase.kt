package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class StartDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(urls: List<String>, parallelism: Int): Result<Unit> {
        return repository.startDownloads(urls, parallelism)
    }
}
