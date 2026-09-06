package com.example.netshadow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.data.model.AlertEvent
import com.example.netshadow.data.model.BaselineSummary
import androidx.compose.foundation.layout.Spacer

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netshadow.ui.AlertsViewModel
import com.example.netshadow.ui.CtrlViewModel
import com.example.netshadow.ui.IntelViewModel
import com.example.netshadow.ui.StatsViewModel

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    showDenial: Boolean,
    onStartCapture: () -> Unit,
    onRetry: () -> Unit,
    onExport: () -> Unit
) {
    val summaries by viewModel.appSummaries.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (showDenial) {
                Button(onClick = onRetry) {
                    Text("Retry VPN")
                }
            } else {
                Button(onClick = onStartCapture) {
                    Text("Start Capture")
                }
            }
            
            Button(onClick = { 
                // In a real app we'd get the dir from the activity/context
                onExport() 
            }) {
                Text("Export CSV")
            }
        }

        exportStatus?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "NETSHADOW_STATS", style = MaterialTheme.typography.headlineSmall)
        
        summaries.forEach { summary ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = summary.packageName, style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Sent: ${summary.totalBytesSent} bytes | Received: ${summary.totalBytesReceived} bytes")
                    Text(text = "Alerts: ${summary.alertCount}", color = if (summary.alertCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun IntelScreen(viewModel: IntelViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(horizontalAlignment = Alignment.Start) {
            Text("NETSHADOW_INTEL", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (uiState.selectedAppPackage == null) {
                Text("Select an app from STATS to see details.")
            } else {
                Text("App: ${uiState.selectedAppPackage}", style = MaterialTheme.typography.titleLarge)
                
                uiState.baseline?.let { baseline ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Baseline Summary", style = MaterialTheme.typography.titleMedium)
                            Text(baseline.summaryText)
                            Text("Trust Score: ${(baseline.trustScore * 100).toInt()}%")
                            Text("Allowed Countries: ${baseline.allowedCountries.joinToString()}")
                        }
                    }
                }
                
                if (uiState.recentAlerts.isNotEmpty()) {
                    Text("Recent Alerts", style = MaterialTheme.typography.titleMedium)
                    uiState.recentAlerts.forEach { alert ->
                        Text("! ${alert.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsScreen(viewModel: AlertsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column {
            Text("NETSHADOW_ALERTS", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Unread: ${uiState.unreadCount}", style = MaterialTheme.typography.titleMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (uiState.alerts.isEmpty()) {
                Text("No active threats detected.")
            } else {
                uiState.alerts.forEach { alert ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = alert.packageName, style = MaterialTheme.typography.labelSmall)
                            Text(text = alert.message, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "Severity: ${alert.severity}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CtrlScreen(viewModel: CtrlViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NETSHADOW_CTRL", style = MaterialTheme.typography.headlineMedium)
            Text("VPN Status: ${if (uiState.vpnEnabled) "ACTIVE" else "INACTIVE"}")
        }
    }
}
