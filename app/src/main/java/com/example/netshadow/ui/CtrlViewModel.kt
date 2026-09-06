package com.example.netshadow.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CtrlUiState(
    val vpnEnabled: Boolean = false,
    val dnsFilteringEnabled: Boolean = true,
    val darkTheme: Boolean = true
)

class CtrlViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CtrlUiState())
    val uiState: StateFlow<CtrlUiState> = _uiState.asStateFlow()

    fun toggleVpn(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(vpnEnabled = enabled)
    }
}
