package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class GetOrphanTempFilesUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(): List<String> = repository.getOrphanTempFiles()
}
