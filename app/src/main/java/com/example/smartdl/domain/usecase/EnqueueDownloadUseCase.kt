package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class EnqueueDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(
        url: String,
        fileName: String?,
        mimeType: String?,
        userAgent: String?,
        referer: String?,
        cookies: String?
    ) = repository.enqueueDownload(url, fileName, mimeType, userAgent, referer, cookies)
}
