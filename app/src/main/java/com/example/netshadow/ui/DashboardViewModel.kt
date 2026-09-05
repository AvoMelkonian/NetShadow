package com.example.netshadow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.data.repository.TrafficRepository
import kotlinx.coroutines.flow.*

class DashboardViewModel(
    private val trafficRepository: TrafficRepository
) : ViewModel() {

    val appSummaries: StateFlow<List<AppSummary>> = trafficRepository.getAppSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
