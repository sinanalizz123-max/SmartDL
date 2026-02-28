package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class ResumeDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(id: Long) = repository.resumeDownload(id)
}
