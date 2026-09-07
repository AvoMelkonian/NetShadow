package com.example.netshadow.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.data.repository.TrafficRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CtrlUiState(
    val vpnEnabled: Boolean = false,
    val engineStatus: String = "INACTIVE",
    val enginePid: Int? = null,
    val excludedApps: List<String> = emptyList(),
    val appSummaries: List<AppSummary> = emptyList(),
    val exportProgress: Float? = null,
    val exportUri: String? = null
)

class CtrlViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {

    private val _vpnEnabled = MutableStateFlow(false)
    private val _excludedApps = MutableStateFlow<List<String>>(emptyList())
    
    val uiState: StateFlow<CtrlUiState> = combine(
        _vpnEnabled,
        _excludedApps,
        trafficRepository.getAppSummaries()
    ) { enabled, excluded, summaries ->
        CtrlUiState(
            vpnEnabled = enabled,
            engineStatus = if (enabled) "ACTIVE" else "INACTIVE",
            enginePid = if (enabled) 4921 else null, // Mock PID
            excludedApps = excluded,
            appSummaries = summaries
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CtrlUiState()
    )

    fun toggleVpn(enabled: Boolean) {
        _vpnEnabled.value = enabled
    }

    fun toggleExclusion(packageName: String, exclude: Boolean) {
        val current = _excludedApps.value.toMutableList()
        if (exclude) {
            if (!current.contains(packageName)) current.add(packageName)
        } else {
            current.remove(packageName)
        }
        _excludedApps.value = current
    }

    fun resetBaseline(packageName: String) {
        viewModelScope.launch {
            trafficRepository.resetBaseline(packageName)
        }
    }

    suspend fun getTrafficCsv(): String {
        return trafficRepository.exportTrafficLogAsCsv()
    }

    fun exportLog() {
        // Logic for export will be handled by the UI calling repository and sharing
    }

    class Factory(private val repository: TrafficRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CtrlViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CtrlViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
