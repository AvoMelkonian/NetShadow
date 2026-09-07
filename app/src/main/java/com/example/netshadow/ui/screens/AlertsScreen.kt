package com.example.netshadow.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netshadow.data.model.AlertEvent
import com.example.netshadow.data.model.Severity
import com.example.netshadow.ui.AlertsViewModel
import com.example.netshadow.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AlertsScreen(
    viewModel: AlertsViewModel,
    isWideScreen: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (isWideScreen) {
        AlertsTwoPaneLayout(uiState, viewModel)
    } else {
        if (uiState.selectedAlertId != null) {
            AlertDetailView(
                alert = uiState.selectedAlert,
                onBack = { viewModel.selectAlert(null) },
                onMarkExpected = { viewModel.markAsExpected(it) },
                onRemediate = { viewModel.remediateAndClose(it) }
            )
        } else {
            AlertsListView(uiState, viewModel)
        }
    }
}

@Composable
fun AlertsListView(
    uiState: com.example.netshadow.ui.AlertsUiState,
    viewModel: AlertsViewModel
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("EVENT STREAM", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Summary Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryTag("${uiState.criticalCount} CRITICAL", ErrorLight)
            SummaryTag("${uiState.warningCount} WARNING", AmberWarning)
            Text(
                "SYNC: LIVE // 0ms",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("CHRONOLOGICAL DESC ↓", style = MaterialTheme.typography.labelSmall, color = NeonGreen)
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.alerts, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    onClick = { viewModel.selectAlert(alert.id) },
                    onMarkExpected = { viewModel.markAsExpected(alert) }
                )
            }
        }
    }
}

@Composable
fun AlertCard(
    alert: AlertEvent,
    onClick: () -> Unit,
    onMarkExpected: () -> Unit
) {
    val accent = when (alert.severity) {
        Severity.CRITICAL -> ErrorLight
        Severity.HIGH, Severity.MEDIUM -> AmberWarning
        Severity.LOW -> Color.Gray
    }

    val containerColor = if (alert.severity == Severity.LOW) SurfaceDim else SurfaceCard
    val alpha = if (alert.isRead) 0.6f else 1.0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(if (alert.severity == Severity.CRITICAL) Modifier.glowBorder(accent.copy(alpha = 0.5f)) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = alpha)),
        border = BorderStroke(1.dp, if (alert.severity == Severity.CRITICAL) accent else BorderColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Box {
            if (alert.severity == Severity.CRITICAL) {
                Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent).align(Alignment.CenterStart))
            }
            
            Column(modifier = Modifier.padding(16.dp).padding(start = if (alert.severity == Severity.CRITICAL) 8.dp else 0.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (alert.severity == Severity.CRITICAL) Icons.Default.GppBad else Icons.Default.Warning,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = alert.type.name.replace("_", " "),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent
                        )
                    }
                    Text(
                        text = formatTimeValue(alert.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (alert.severity == Severity.LOW) Color.Gray else Color.White
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SignatureChip(alert.type.name)
                    
                    TextButton(
                        onClick = { onMarkExpected() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "MARK AS EXPECTED",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGreen,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlertDetailView(
    alert: AlertEvent?,
    onBack: () -> Unit,
    onMarkExpected: (AlertEvent) -> Unit,
    onRemediate: (Long) -> Unit
) {
    if (alert == null) return

    Column(modifier = Modifier.fillMaxSize().background(Black).padding(16.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonGreen)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(alert.packageName.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(alert.type.name.replace("_", " "), style = MaterialTheme.typography.labelLarge, color = NeonGreen)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Metadata Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetadataField("SOURCE IP", "10.0.0.2", "INTERNAL", modifier = Modifier.weight(1f))
                    MetadataField("TARGET ID", alert.affectedTarget ?: "UNKNOWN", "EXTERNAL", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetadataField("PORT SCAN", "443, 80, 8080", null, modifier = Modifier.weight(1f))
                    MetadataField("DURATION", "142ms", null, modifier = Modifier.weight(1f))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("RAW LOG SNIPPET", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Terminal Block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .border(1.dp, BorderColor)
                .padding(12.dp)
        ) {
            Text(
                text = """
                    [2024-05-20 14:22:01.442] CONNECT OUTBOUND 10.0.0.2:54212 -> ${alert.affectedTarget}:443
                    [2024-05-20 14:22:01.445] TLS_HANDSHAKE_START (SNI: ${alert.affectedTarget})
                    [2024-05-20 14:22:01.502] TLS_HANDSHAKE_COMPLETE (Cipher: AES_256_GCM)
                    [2024-05-20 14:22:01.510] BYTES_SENT: 1.2KB | BYTES_RECV: 4.8KB
                    [2024-05-20 14:22:01.600] SESSION_CLOSE (Reason: NORMAL)
                """.trimIndent(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                color = Color.Gray
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { /* Isolate host stub */ },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.Gray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("ISOLATE HOST", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = { onRemediate(alert.id) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Black)
            ) {
                Text("REMEDIATE & CLOSE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AlertsTwoPaneLayout(uiState: com.example.netshadow.ui.AlertsUiState, viewModel: AlertsViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AlertsListView(uiState, viewModel)
        }
        VerticalDivider(color = BorderColor, thickness = 1.dp)
        Box(modifier = Modifier.weight(1.2f)) {
            if (uiState.selectedAlert != null) {
                AlertDetailView(
                    alert = uiState.selectedAlert,
                    onBack = { viewModel.selectAlert(null) },
                    onMarkExpected = { viewModel.markAsExpected(it) },
                    onRemediate = { viewModel.remediateAndClose(it) }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("SELECT AN EVENT TO VIEW DETAILS", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SummaryTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        border = BorderStroke(1.dp, color),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SignatureChip(text: String) {
    Surface(
        color = Color.DarkGray,
        contentColor = Color.LightGray,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = "SIG_$text",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
        )
    }
}

@Composable
fun MetadataField(label: String, value: String, badge: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
            if (badge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color.DarkGray,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                    )
                }
            }
        }
    }
}

private fun formatTimeValue(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
    return sdf.format(Date(timestamp))
}
