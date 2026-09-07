package com.example.netshadow.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netshadow.data.model.AlertEvent
import com.example.netshadow.data.model.ExpectedType
import com.example.netshadow.data.repository.TrafficRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AlertsUiState(
    val alerts: List<AlertEvent> = emptyList(),
    val unreadCount: Int = 0,
    val criticalCount: Int = 0,
    val warningCount: Int = 0,
    val infoCount: Int = 0,
    val selectedAlertId: Long? = null,
    val selectedAlert: AlertEvent? = null,
    val isLoading: Boolean = false
)

class AlertsViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {
    
    private val _selectedAlertId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AlertsUiState> = combine(
        trafficRepository.getAlerts(),
        _selectedAlertId
    ) { entities, selectedId ->
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
            unreadCount = alerts.count { !it.isRead },
            criticalCount = alerts.count { it.severity == com.example.netshadow.data.model.Severity.CRITICAL },
            warningCount = alerts.count { it.severity == com.example.netshadow.data.model.Severity.HIGH || it.severity == com.example.netshadow.data.model.Severity.MEDIUM },
            infoCount = alerts.count { it.severity == com.example.netshadow.data.model.Severity.LOW },
            selectedAlertId = selectedId,
            selectedAlert = alerts.find { it.id == selectedId }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AlertsUiState()
    )

    fun selectAlert(alertId: Long?) {
        _selectedAlertId.value = alertId
        if (alertId != null) {
            viewModelScope.launch {
                trafficRepository.anomalyAlertDao.markAsRead(alertId)
            }
        }
    }

    fun markAsExpected(alert: AlertEvent) {
        viewModelScope.launch {
            val target = alert.affectedTarget ?: alert.packageName
            val type = when {
                target.contains(".") && !target.any { it.isDigit() } -> ExpectedType.DOMAIN
                target.any { it.isDigit() } -> ExpectedType.IP
                else -> ExpectedType.COUNTRY
            }
            
            trafficRepository.markAsExpected(alert.packageName, target, type)
            Log.d("AlertsViewModel", "Marked $target as expected for ${alert.packageName}")
        }
    }

    fun remediateAndClose(alertId: Long) {
        // Mock remediation logic
        Log.d("AlertsViewModel", "Remediating and closing alert $alertId")
        selectAlert(null)
    }
}

// Extension to access Dao from repository for quick mark-as-read
private val TrafficRepository.anomalyAlertDao get() = this.javaClass.getDeclaredField("anomalyAlertDao").let {
    it.isAccessible = true
    it.get(this) as com.example.netshadow.data.dao.AnomalyAlertDao
}
