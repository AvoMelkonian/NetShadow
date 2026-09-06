package com.example.netshadow.ui

import androidx.lifecycle.ViewModel
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.BaselineSummary
import com.example.netshadow.data.model.AlertEvent
import com.example.netshadow.data.repository.TrafficRepository
import com.example.netshadow.ui.preview.PreviewData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IntelUiState(
    val selectedAppPackage: String? = null,
    val baseline: BaselineSummary? = null,
    val recentAlerts: List<AlertEvent> = emptyList(),
    val connections: List<ConnectionEventEntity> = emptyList(),
    val isLoading: Boolean = false
)

class IntelViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(IntelUiState())
    val uiState: StateFlow<IntelUiState> = _uiState.asStateFlow()

    fun selectApp(packageName: String) {
        // For now, populate with mock data if a package is selected
        _uiState.value = IntelUiState(
            selectedAppPackage = packageName,
            baseline = PreviewData.sampleBaselineSummary,
            recentAlerts = listOf(PreviewData.sampleAlertEvent),
            connections = PreviewData.chaoticEvents.take(10)
        )
    }
}
