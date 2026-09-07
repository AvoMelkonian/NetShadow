package com.example.netshadow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.BaselineSummary
import com.example.netshadow.data.model.AlertEvent
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.data.repository.TrafficRepository
import com.example.netshadow.ui.preview.PreviewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class IntelUiState(
    val selectedAppPackage: String? = null,
    val appName: String? = null,
    val processId: Int? = null,
    val version: String? = "v1.0.0",
    val dataSentBytes: Long = 0,
    val dataReceivedBytes: Long = 0,
    val baseline: BaselineSummary? = null,
    val recentAlerts: List<AlertEvent> = emptyList(),
    val connections: List<ConnectionEventEntity> = emptyList(),
    val isLoading: Boolean = false
)

class IntelViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {
    private val _selectedPackage = MutableStateFlow<String?>(null)
    
    val allAppSummaries: StateFlow<List<AppSummary>> = trafficRepository.getAppSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<IntelUiState> = _selectedPackage
        .flatMapLatest { packageName ->
            if (packageName == null) {
                flowOf(IntelUiState())
            } else {
                combine(
                    trafficRepository.getAppDetail(packageName),
                    allAppSummaries
                ) { detail, summaries ->
                    val summary = summaries.find { it.packageName == packageName }
                    IntelUiState(
                        selectedAppPackage = packageName,
                        appName = summary?.appName ?: packageName,
                        processId = (summary?.liveConnectionCount ?: 0) * 123 + 4000,
                        version = "v120.0.6099.109",
                        dataSentBytes = summary?.totalBytesSent ?: 0,
                        dataReceivedBytes = summary?.totalBytesReceived ?: 0,
                        baseline = PreviewData.sampleBaselineSummary.copy(packageName = packageName),
                        recentAlerts = detail.alerts.map { entity ->
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
                        },
                        connections = detail.recentEvents,
                        isLoading = false
                    )
                }.onStart { emit(IntelUiState(selectedAppPackage = packageName, isLoading = true)) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = IntelUiState()
        )

    fun selectApp(packageName: String) {
        _selectedPackage.value = packageName
    }
}
