package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class PauseDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(id: Long) = repository.pauseDownload(id)
}
