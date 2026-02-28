package com.example.smartdl.domain.usecase

import com.example.smartdl.domain.repository.DownloadRepository

class ResumeIncompleteUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(autoResume: Boolean) {
        repository.resumeIncomplete(autoResume)
    }
}
