package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class StartQueueUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(maxParallel: Int) = repository.startQueue(maxParallel)
}
