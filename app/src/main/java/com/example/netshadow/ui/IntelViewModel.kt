package com.example.netshadow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netshadow.data.repository.TrafficRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IntelUiState(
    val selectedAppPackage: String? = null,
    val connections: List<String> = emptyList(), // Stub for real ConnectionKey
    val isLoading: Boolean = false
)

class IntelViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(IntelUiState())
    val uiState: StateFlow<IntelUiState> = _uiState.asStateFlow()

    fun selectApp(packageName: String) {
        _uiState.value = _uiState.value.copy(selectedAppPackage = packageName)
    }
}
