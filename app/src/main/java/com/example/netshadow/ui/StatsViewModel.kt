package com.example.netshadow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.data.repository.TrafficRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File

data class StatsUiState(
    val summaries: List<AppSummary> = emptyList(),
    val isCapturing: Boolean = false,
    val totalSentBytes: Long = 0,
    val totalReceivedBytes: Long = 0,
    val exportStatus: String? = null
)

class StatsViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {

    val appSummaries: StateFlow<List<AppSummary>> = trafficRepository.getAppSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus

    fun exportLog(directory: File) {
        // Implementation stub
        _exportStatus.value = "Exporting to ${directory.absolutePath}..."
    }

    class Factory(private val repository: TrafficRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StatsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
