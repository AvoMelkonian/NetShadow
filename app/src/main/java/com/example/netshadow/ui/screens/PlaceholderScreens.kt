package com.example.netshadow.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.AlertEvent
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.data.model.BaselineSummary
import com.example.netshadow.data.model.Severity
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
    onExport: () -> Unit,
    onAppClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item(key = "header") {
            MissionControlHeader(
                startTime = uiState.vpnStartTime,
                isCapturing = uiState.isCapturing,
                showDenial = showDenial,
                onToggle = onToggleCapture,
                onRetry = onRetry
            )
        }

        item(key = "stat_cards") {
            StatCards(
                activeConnections = uiState.activeConnections,
                throughput = uiState.throughputMbps,
                pendingAlerts = uiState.pendingAlerts
            )
        }

        item(key = "top_apps_header") {
            Text(
                text = "TOP APPLICATION SIGNATURES",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray
            )
        }

        item(key = "top_apps_row") {
            TopApplicationSignatures(
                summaries = uiState.summaries,
                onAppClick = onAppClick,
                showHeader = false
            )
        }

        item(key = "live_stream_header") {
            LiveStreamHeader()
        }

        item(key = "live_stream_table_header") {
            LiveStreamTableHeader()
        }

        if (uiState.recentEvents.isEmpty()) {
            item(key = "empty_stream") {
                Text(
                    "STREAMING INACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            items(
                items = uiState.recentEvents.take(15),
                key = { it.connectionId }
            ) { event ->
                NetworkEventRow(
                    event = event,
                    onClick = { onAppClick(event.packageName) }
                )
            }
        }
    }
}

@Composable
fun TopApplicationSignatures(
    summaries: List<AppSummary>,
    onAppClick: (String) -> Unit,
    showHeader: Boolean = true
) {
    val sortedApps = remember(summaries) { 
        summaries.sortedByDescending { it.liveConnectionCount }.take(10)
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showHeader) {
            Text(
                text = "TOP APPLICATION SIGNATURES",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        if (sortedApps.isEmpty()) {
            Text(
                "NO ACTIVITY DETECTED",
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(sortedApps, key = { it.packageName }) { app ->
                    AppSignatureTile(
                        app = app,
                        onClick = { onAppClick(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun AppSignatureTile(
    app: AppSummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(100.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Color.DarkGray),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
fun LiveStreamHeader() {
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
}

@Composable
fun LiveStreamTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("TARGET DOMAIN / IP", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray, modifier = Modifier.weight(1f))
        Text("LOC", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray, modifier = Modifier.width(40.dp))
        Text("STATUS", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray, modifier = Modifier.width(50.dp))
    }
}

@Composable
fun NetworkEventRow(
    event: ConnectionEventEntity,
    onClick: () -> Unit
) {
    val isWarning = remember(event.remoteCountry) { 
        event.remoteCountry == "RU" || event.remoteCountry == "KP"
    }
    val accent = if (isWarning) AmberWarning else NeonGreen

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onClick() },
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
    showDenial: Boolean,
    onToggle: (Boolean) -> Unit,
    onRetry: () -> Unit
) {
    val statusText = if (isCapturing) "ONLINE" else "OFFLINE"
    val statusColor = if (isCapturing) NeonGreen else Color.Gray

    Column {
        if (showDenial) {
            Surface(
                color = AmberWarning.copy(alpha = 0.1f),
                contentColor = AmberWarning,
                border = BorderStroke(1.dp, AmberWarning),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VPN PERMISSION DENIED. TAP TO RETRY.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { onRetry() })
                }
            }
        }

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
        Row {
            Text(
                text = "SYS_STATUS: $statusText // UPTIME: ",
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
            UptimeText(startTime, statusColor)
        }
    }
}

@Composable
fun UptimeText(startTime: Long?, color: Color) {
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
    
    Text(
        text = formatUptimeValue(uptimeMillis),
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
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
                        .fillMaxWidth(0.6f)
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
fun IntelScreen(viewModel: IntelViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allApps by viewModel.allAppSummaries.collectAsStateWithLifecycle()
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopApplicationSignatures(
            summaries = allApps,
            onAppClick = { packageName -> viewModel.selectApp(packageName) }
        )
        
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.selectedAppPackage == null) {
                Text(
                    "SELECT AN APP ABOVE TO SEE DETAILS.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelLarge
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        IntelHeader(
                            appName = uiState.appName ?: uiState.selectedAppPackage!!,
                            packageName = uiState.selectedAppPackage!!,
                            pid = uiState.processId ?: 0,
                            version = uiState.version ?: ""
                        )
                    }
                    
                    item {
                        IntelTrafficCards(
                            sentBytes = uiState.dataSentBytes,
                            receivedBytes = uiState.dataReceivedBytes
                        )
                    }

                    item {
                        NetworkTrafficChartPanel(uiState.dataSentBytes, uiState.dataReceivedBytes)
                    }

                    item {
                        DestinationList(uiState.connections)
                    }

                    item {
                        BehavioralAnalysisCard(uiState.baseline, uiState.recentAlerts)
                    }
                }
            }
        }
    }
}

@Composable
fun IntelHeader(
    appName: String,
    packageName: String,
    pid: Int,
    version: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.DarkGray, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Text(appName.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(appName.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                text = "PROCESS ID: $pid • $version",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun IntelTrafficCards(sentBytes: Long, receivedBytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TrafficDataCard(
            label = "DATA SENT (TX)",
            value = formatBytesToGB(sentBytes),
            accentColor = NeonGreen,
            modifier = Modifier.weight(1f)
        )
        TrafficDataCard(
            label = "DATA RECV (RX)",
            value = formatBytesToGB(receivedBytes),
            accentColor = Color.White,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TrafficDataCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accentColor)
        }
    }
}

@Composable
fun NetworkTrafficChartPanel(sentBytes: Long, receivedBytes: Long) {
    var selectedRange by remember { mutableStateOf("1H") }
    
    val seed = remember(sentBytes, receivedBytes, selectedRange) { 
        (sentBytes xor receivedBytes).toInt() 
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NETWORK TRAFFIC", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                
                Row(modifier = Modifier.height(24.dp)) {
                    listOf("1H", "24H", "7D").forEach { range ->
                        val isSelected = selectedRange == range
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .fillMaxHeight()
                                .border(
                                    1.dp,
                                    if (isSelected) NeonGreen else Color.DarkGray,
                                    MaterialTheme.shapes.extraSmall
                                )
                                .clickable { selectedRange = range },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = range,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) NeonGreen else Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            NetworkTrafficCanvas(seed)
        }
    }
}

@Composable
fun NetworkTrafficCanvas(seed: Int) {
    val livePath = remember { Path() }
    val ghostPath = remember { Path() }
    val random = remember(seed) { java.util.Random(seed.toLong()) }
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val width = size.width
        val height = size.height
        
        val gridColor = Color.DarkGray.copy(alpha = 0.3f)
        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, height * 0.25f), end = androidx.compose.ui.geometry.Offset(width, height * 0.25f), strokeWidth = 1f)
        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, height * 0.5f), end = androidx.compose.ui.geometry.Offset(width, height * 0.5f), strokeWidth = 1f)
        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, height * 0.75f), end = androidx.compose.ui.geometry.Offset(width, height * 0.75f), strokeWidth = 1f)

        ghostPath.reset()
        ghostPath.moveTo(0f, height * (0.5f + (random.nextFloat() - 0.5f) * 0.4f))
        for (i in 1..4) {
            ghostPath.quadraticBezierTo(
                width * (i - 0.5f) / 4f, height * random.nextFloat(),
                width * i / 4f, height * (0.5f + (random.nextFloat() - 0.5f) * 0.4f)
            )
        }
        
        drawPath(
            path = ghostPath,
            color = Color.Gray.copy(alpha = 0.3f),
            style = Stroke(width = 2.dp.toPx())
        )

        livePath.reset()
        livePath.moveTo(0f, height * (0.7f + (random.nextFloat() - 0.5f) * 0.4f))
        for (i in 1..4) {
            livePath.quadraticBezierTo(
                width * (i - 0.5f) / 4f, height * random.nextFloat(),
                width * i / 4f, height * (0.7f + (random.nextFloat() - 0.5f) * 0.4f)
            )
        }
        
        drawPath(
            path = livePath,
            color = NeonGreen,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun DestinationList(connections: List<ConnectionEventEntity>) {
    val destinations = remember(connections) {
        connections.distinctBy { it.remoteAddress }.take(10)
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("TOP DESTINATIONS", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        
        destinations.forEach { conn ->
            key(conn.remoteAddress) {
                DestinationRow(conn)
            }
        }
    }
}

@Composable
fun DestinationRow(conn: ConnectionEventEntity) {
    val isTracker = remember(conn.resolvedDomain) {
        conn.resolvedDomain?.contains("google-analytics") == true || 
        conn.resolvedDomain?.contains("doubleclick") == true ||
        conn.resolvedDomain?.contains("facebook") == true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conn.remoteCountry ?: "??",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conn.resolvedDomain ?: conn.remoteAddress,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = conn.remoteAsnOrg ?: "UNKNOWN PROVIDER",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            if (isTracker) {
                Surface(
                    color = AmberWarning.copy(alpha = 0.2f),
                    contentColor = AmberWarning,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, AmberWarning)
                ) {
                    Text(
                        text = "TRACKER",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun BehavioralAnalysisCard(baseline: BaselineSummary?, alerts: List<AlertEvent>) {
    val isAnomalous = remember(alerts) {
        alerts.any { it.severity == Severity.HIGH || it.severity == Severity.CRITICAL }
    }
    val accentColor = if (isAnomalous) AmberWarning else NeonGreen
    val statusText = if (isAnomalous) "ANOMALOUS BEHAVIOR" else "NORMAL BEHAVIOR"
    val icon = if (isAnomalous) Icons.Default.Warning else Icons.Default.CheckCircle

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, accentColor)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(statusText, style = MaterialTheme.typography.labelLarge, color = accentColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = baseline?.summaryText ?: "Analyzing behavioral patterns...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
}

private fun formatUptimeValue(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatBytesToGB(bytes: Long): String {
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    return "%.3f GB".format(gb)
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
