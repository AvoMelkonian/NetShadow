package com.example.netshadow.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.ui.AlertsViewModel
import com.example.netshadow.ui.CtrlViewModel
import com.example.netshadow.ui.IntelViewModel
import com.example.netshadow.ui.StatsViewModel
import com.example.netshadow.ui.theme.AmberWarning
import com.example.netshadow.ui.theme.NeonGreen
import com.example.netshadow.ui.theme.SurfaceCard
import kotlinx.coroutines.delay

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    showDenial: Boolean,
    onToggleCapture: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onExport: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MissionControlHeader(
                startTime = uiState.vpnStartTime,
                isCapturing = uiState.isCapturing,
                onToggle = onToggleCapture
            )
        }

        item {
            StatCards(
                activeConnections = uiState.activeConnections,
                throughput = uiState.throughputMbps,
                pendingAlerts = uiState.pendingAlerts
            )
        }

        item {
            TopApplicationSignatures(uiState.summaries)
        }

        item {
            LiveNetworkStream(uiState.recentEvents)
        }
    }
}

@Composable
fun TopApplicationSignatures(summaries: List<AppSummary>) {
    val sortedApps = summaries.sortedByDescending { it.liveConnectionCount }.take(10)
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "TOP APPLICATION SIGNATURES",
            style = MaterialTheme.typography.labelLarge,
            color = Color.Gray
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(sortedApps) { app ->
                AppSignatureTile(app)
            }
        }
    }
}

@Composable
fun AppSignatureTile(app: AppSummary) {
    Card(
        modifier = Modifier.width(100.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Color.DarkGray),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon stub
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.DarkGray, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.packageName.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = (app.appName ?: app.packageName).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            
            Text(
                text = "${app.liveConnectionCount} CONN",
                style = MaterialTheme.typography.labelSmall,
                color = NeonGreen
            )
        }
    }
}

@Composable
fun LiveNetworkStream(events: List<ConnectionEventEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LIVE NETWORK STREAM",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.width(8.dp))
                PulseIndicator()
            }
            
            IconButton(onClick = { /* Stub */ }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.Gray)
            }
        }

        // Table Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("TARGET DOMAIN / IP", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray, modifier = Modifier.weight(1f))
            Text("LOC", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray, modifier = Modifier.width(40.dp))
            Text("STATUS", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray, modifier = Modifier.width(50.dp))
        }

        // Column of events with top-in animation
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            events.take(10).forEach { event ->
                key(event.connectionId) {
                    NetworkEventRow(event)
                }
            }
        }
    }
}

@Composable
fun NetworkEventRow(event: ConnectionEventEntity) {
    val isWarning = event.remoteCountry == "RU" || event.remoteCountry == "KP" // Example logic
    val accent = if (isWarning) AmberWarning else NeonGreen

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.resolvedDomain ?: event.remoteAddress,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (isWarning) AmberWarning else Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (event.resolvedDomain != null) {
                    Text(
                        text = event.remoteAddress,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                        color = Color.Gray
                    )
                }
            }
            
            Text(
                text = event.remoteCountry ?: "--",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (isWarning) AmberWarning else Color.Gray,
                modifier = Modifier.width(40.dp)
            )
            
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
    }
}

@Composable
fun PulseIndicator() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            visible = !visible
        }
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (visible) NeonGreen else NeonGreen.copy(alpha = 0.2f))
    )
}

@Composable
fun MissionControlHeader(
    startTime: Long?,
    isCapturing: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var uptimeMillis by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(startTime) {
        if (startTime != null) {
            while (true) {
                uptimeMillis = System.currentTimeMillis() - startTime
                delay(1000)
            }
        } else {
            uptimeMillis = 0L
        }
    }

    val uptimeText = formatUptime(uptimeMillis)
    val statusText = if (isCapturing) "ONLINE" else "OFFLINE"
    val statusColor = if (isCapturing) NeonGreen else Color.Gray

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MISSION CONTROL",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Switch(
                checked = isCapturing,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonGreen,
                    checkedTrackColor = NeonGreen.copy(alpha = 0.5f)
                )
            )
        }
        Text(
            text = "SYS_STATUS: $statusText // UPTIME: $uptimeText",
            style = MaterialTheme.typography.labelSmall,
            color = statusColor
        )
    }
}

@Composable
fun StatCards(
    activeConnections: Int,
    throughput: Double,
    pendingAlerts: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            label = "ACTIVE CONNECTIONS",
            value = activeConnections.toString(),
            icon = Icons.Default.Wifi,
            accentColor = NeonGreen
        )
        StatCard(
            label = "TOTAL THROUGHPUT",
            value = "%.2f MB/s".format(throughput),
            icon = Icons.Default.Speed,
            accentColor = NeonGreen
        )
        StatCard(
            label = "PENDING ALERTS",
            value = pendingAlerts.toString(),
            icon = Icons.Default.Notifications,
            accentColor = AmberWarning
        )
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(accentColor.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f) // Progress stub
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }
        }
    }
}

private fun formatUptime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
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
