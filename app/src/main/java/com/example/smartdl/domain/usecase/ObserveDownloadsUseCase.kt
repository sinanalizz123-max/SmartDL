package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class ObserveDownloadsUseCase(
    private val repository: DownloadRepository
) {
    operator fun invoke() = repository.observeDownloads()
}
