package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class AddHistoryUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(url: String, title: String?) = repository.addHistory(url, title)
}
