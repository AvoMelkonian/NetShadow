package com.example.netshadow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.data.repository.TrafficRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class DashboardViewModel(
    private val trafficRepository: TrafficRepository
) : ViewModel() {

    val appSummaries: StateFlow<List<AppSummary>> = trafficRepository.getAppSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    fun exportLog(filesDir: File) {
        viewModelScope.launch {
            try {
                val csv = trafficRepository.exportTrafficLogAsCsv()
                val file = File(filesDir, "traffic_log.csv")
                file.writeText(csv)
                _exportStatus.value = "Exported to ${file.absolutePath}"
            } catch (e: Exception) {
                _exportStatus.value = "Export failed: ${e.message}"
            }
        }
    }

    class Factory(private val repository: TrafficRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
