package com.example.smartdl.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdl.data.source.OrphanCache
import com.example.smartdl.domain.usecase.ClearIncompleteCacheUseCase
import com.example.smartdl.domain.usecase.GetOrphanTempFilesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getOrphanTempFiles: GetOrphanTempFilesUseCase,
    private val clearIncompleteCache: ClearIncompleteCacheUseCase
) : ViewModel() {

    data class UiState(
        val orphans: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        refresh()
    }

    fun clearCache() {
        viewModelScope.launch {
            clearIncompleteCache()
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val files = getOrphanTempFiles()
            OrphanCache.lastScan = files
            _state.update { it.copy(orphans = files) }
        }
    }
}
