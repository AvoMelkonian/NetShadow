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

@Composable
fun StatsScreen(
    showDenial: Boolean,
    summaries: List<AppSummary>,
    exportStatus: String?,
    onStartCapture: () -> Unit,
    onRetry: () -> Unit,
    onExport: () -> Unit
) {
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
            
            Button(onClick = onExport) {
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

        Text(text = "TODO: STATS (Dashboard)", style = MaterialTheme.typography.headlineSmall)
        
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
fun IntelScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: INTEL (Per-App Detail)", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun AlertsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: ALERTS", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun CtrlScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: CTRL (Settings)", style = MaterialTheme.typography.headlineMedium)
    }
}
