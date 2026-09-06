package com.example.netshadow.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.netshadow.data.entity.AnomalyAlertEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.data.repository.TrafficRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class StatsUiState(
    val summaries: List<AppSummary> = emptyList(),
    val activeConnections: Int = 0,
    val throughputMbps: Double = 0.0,
    val pendingAlerts: Int = 0,
    val recentEvents: List<ConnectionEventEntity> = emptyList(),
    val isCapturing: Boolean = false,
    val vpnStartTime: Long? = null,
    val exportStatus: String? = null
)

class StatsViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {

    private val _mockThroughput = MutableStateFlow(0.0)
    private val _vpnStartTime = MutableStateFlow<Long?>(null)
    private val _isCapturing = MutableStateFlow(false)
    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus

    val uiState: StateFlow<StatsUiState> = combine(
        trafficRepository.getAppSummaries(),
        trafficRepository.getAlerts(),
        trafficRepository.enrichedEvents.scan(emptyList<ConnectionEventEntity>()) { acc, event ->
            (listOf(event) + acc).take(50)
        }.onStart { emit(emptyList()) },
        _mockThroughput,
        _vpnStartTime,
        _isCapturing
    ) { args: Array<*> ->
        val summaries = args[0] as List<AppSummary>
        val alerts = args[1] as List<AnomalyAlertEntity>
        val recentEvents = args[2] as List<ConnectionEventEntity>
        val throughput = args[3] as Double
        val startTime = args[4] as Long?
        val capturing = args[5] as Boolean

        StatsUiState(
            summaries = summaries,
            activeConnections = summaries.sumOf { it.liveConnectionCount },
            throughputMbps = throughput,
            pendingAlerts = alerts.count { !it.isRead },
            recentEvents = recentEvents,
            isCapturing = capturing,
            vpnStartTime = startTime,
            exportStatus = _exportStatus.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )

    init {
        trafficRepository.realtimeEvents
            .onEach { event ->
                Log.d("StatsViewModel", "Real-time event: ${event.packageName} -> ${event.dstIp}:${event.dstPort} (${event.bytesSent} bytes)")
            }
            .launchIn(viewModelScope)
    }

    val appSummaries: StateFlow<List<AppSummary>> = trafficRepository.getAppSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateMockThroughput(value: Double) {
        _mockThroughput.value = value
    }

    fun setVpnStartTime(time: Long?) {
        _vpnStartTime.value = time
    }

    fun exportLog(directory: File) {
        _exportStatus.value = "Exporting to ${directory.absolutePath}..."
    }

    private var mockJob: kotlinx.coroutines.Job? = null

    fun startMockFeed() {
        _isCapturing.value = true
        mockJob?.cancel()
        mockJob = viewModelScope.launch {
            setVpnStartTime(System.currentTimeMillis())
            
            // Fluctuating throughput job
            launch {
                while (true) {
                    val base = 1.2
                    val fluctuation = (kotlin.random.Random.nextDouble() - 0.5) * 0.5
                    updateMockThroughput(base + fluctuation)
                    delay(2000)
                }
            }
            
            // Generate a burst of events
            val burst = com.example.netshadow.ui.preview.PreviewConnectionEventFactory.generateChaoticBurst(count = 20)
            burst.forEach { entity ->
                // Map Entity back to ConnectionEvent for the repository flow
                val event = com.example.netshadow.capture.model.ConnectionEvent(
                    connectionId = entity.connectionId,
                    uid = entity.uid,
                    packageName = entity.packageName,
                    protocol = when (entity.protocol) {
                        is com.example.netshadow.data.model.Protocol.TCP -> com.example.netshadow.capture.model.NetworkProtocol.TCP
                        is com.example.netshadow.data.model.Protocol.UDP -> com.example.netshadow.capture.model.NetworkProtocol.UDP
                        else -> com.example.netshadow.capture.model.NetworkProtocol.OTHER
                    },
                    srcPort = entity.localPort,
                    dstIp = entity.remoteAddress,
                    dstPort = entity.remotePort,
                    resolvedDomain = entity.resolvedDomain,
                    bytesSent = entity.bytesSent,
                    bytesReceived = entity.bytesReceived,
                    direction = com.example.netshadow.capture.model.TrafficDirection.OUTBOUND
                )
                
                trafficRepository.logConnection(event)
                delay(500) // Staggered entry for animation
            }
        }
    }

    fun stopMockFeed() {
        _isCapturing.value = false
        _mockThroughput.value = 0.0
        setVpnStartTime(null)
        mockJob?.cancel()
        mockJob = null
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
