package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class ClearIncompleteCacheUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(): Int = repository.clearIncompleteCache()
}
