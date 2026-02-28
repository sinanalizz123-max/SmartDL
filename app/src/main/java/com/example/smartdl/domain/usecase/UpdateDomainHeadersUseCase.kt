package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class UpdateDomainHeadersUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(domain: String, userAgent: String?, referer: String?, cookies: String?) {
        repository.updateDomainHeaders(domain, userAgent, referer, cookies)
    }
}
