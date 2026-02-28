package com.example.smartdl.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdl.domain.model.DownloadStatus
import com.example.smartdl.domain.usecase.ObserveDownloadsUseCase
import com.example.smartdl.domain.usecase.ResumeIncompleteUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    observeDownloads: ObserveDownloadsUseCase,
    private val resumeIncomplete: ResumeIncompleteUseCase
) : ViewModel() {

    data class UiState(
        val status: String,
        val downloads: List<DownloadUiModel>
    )

    val state: StateFlow<UiState> = observeDownloads()
        .map { items ->
            val uiItems = items.map { task ->
                val percent = if (task.totalBytes > 0) {
                    ((task.bytesDownloaded * 100) / task.totalBytes).toInt().coerceIn(0, 100)
                } else if (task.isVideo) {
                    task.bytesDownloaded.toInt().coerceIn(0, 100)
                } else {
                    0
                }

                DownloadUiModel(
                    id = task.id,
                    fileName = task.fileName,
                    status = task.status,
                    percent = percent,
                    speedText = formatSpeed(task.speedBytesPerSec),
                    isVideo = task.isVideo
                )
            }

            val running = items.count { it.status == DownloadStatus.DOWNLOADING }
            val queued = items.count { it.status == DownloadStatus.QUEUED }
            val paused = items.count { it.status == DownloadStatus.PAUSED }
            val completed = items.count { it.status == DownloadStatus.COMPLETED }
            val failed = items.count { it.status == DownloadStatus.FAILED }

            val status = "Queued: $queued  Running: $running  Paused: $paused  Completed: $completed  Failed: $failed"
            UiState(status = status, downloads = uiItems)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState("Idle", emptyList()))

    init {
        viewModelScope.launch {
            resumeIncomplete(autoResume = true)
        }
    }
}
