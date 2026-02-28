package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class ObserveHistoryUseCase(
    private val repository: DownloadRepository
) {
    operator fun invoke(limit: Int = 200) = repository.observeHistory(limit)
}
