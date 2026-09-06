package com.example.netshadow.ui

import androidx.lifecycle.ViewModel
import com.example.netshadow.data.repository.TrafficRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AlertsUiState(
    val alerts: List<String> = emptyList(), // Stub for real AnomalyAlertEntity
    val unreadCount: Int = 0
)

class AlertsViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    fun dismissAlert(alertId: String) {
        // Implementation stub
    }
}
