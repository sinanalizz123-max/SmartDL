package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class StopQueueUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke() = repository.stopQueue()
}
