package com.example.smartdl.presentation.ui

import com.example.smartdl.domain.model.DownloadStatus


data class DownloadUiModel(
    val id: Long,
    val fileName: String,
    val status: DownloadStatus,
    val percent: Int,
    val speedText: String,
    val isVideo: Boolean
)
