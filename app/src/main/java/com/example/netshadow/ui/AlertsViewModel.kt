package com.example.netshadow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netshadow.data.model.AlertEvent
import com.example.netshadow.data.repository.TrafficRepository
import android.util.Log
import kotlinx.coroutines.flow.*

data class AlertsUiState(
    val alerts: List<AlertEvent> = emptyList(),
    val unreadCount: Int = 0
)

class AlertsViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {
    
    val uiState: StateFlow<AlertsUiState> = trafficRepository.getAlerts()
        .map { entities ->
            Log.d("AlertsViewModel", "Real-time alerts update: ${entities.size} alerts")
            val alerts = entities.map { entity ->
                AlertEvent(
                    id = entity.id,
                    timestamp = entity.timestamp,
                    type = entity.type,
                    severity = entity.severity,
                    message = entity.message,
                    packageName = entity.packageName,
                    isRead = entity.isRead,
                    affectedTarget = entity.target
                )
            }
            AlertsUiState(
                alerts = alerts,
                unreadCount = alerts.count { !it.isRead }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AlertsUiState()
        )

    fun dismissAlert(alertId: Long) {
        // Implementation stub
        Log.d("AlertsViewModel", "Dismissing alert $alertId")
    }
}
