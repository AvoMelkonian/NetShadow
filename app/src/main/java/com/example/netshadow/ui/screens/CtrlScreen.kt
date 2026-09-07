package com.example.netshadow.ui.screens

import android.content.Intent
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.ui.CtrlViewModel
import com.example.netshadow.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CtrlScreen(
    viewModel: CtrlViewModel,
    onToggleVpn: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            CtrlHeader()
        }

        item {
            CoreMonitoringCard(
                vpnEnabled = uiState.vpnEnabled,
                status = uiState.engineStatus,
                pid = uiState.enginePid,
                onToggle = { enabled ->
                    viewModel.toggleVpn(enabled)
                    onToggleVpn(enabled)
                }
            )
        }

        item {
            ExclusionListHeader(activeCount = uiState.excludedApps.size)
        }

        items(uiState.appSummaries, key = { it.packageName }) { app ->
            ExclusionRow(
                app = app,
                isExcluded = uiState.excludedApps.contains(app.packageName),
                onToggle = { viewModel.toggleExclusion(app.packageName, it) },
                onReset = { viewModel.resetBaseline(app.packageName) }
            )
        }

        item {
            ExportCard(onExport = {
                scope.launch {
                    try {
                        val csvData = viewModel.getTrafficCsv()
                        val exportDir = File(context.cacheDir, "exports")
                        if (!exportDir.exists()) exportDir.mkdirs()
                        val file = File(exportDir, "netshadow_log_${System.currentTimeMillis()}.csv")
                        file.writeText(csvData)
                        
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Network Log"))
                    } catch (e: Exception) {
                        android.util.Log.e("CtrlScreen", "Export failed", e)
                    }
                }
            })
        }
    }
}

@Composable
fun CtrlHeader() {
    Column {
        Text("SYSTEM CONTROLS", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            "Manage deep-packet inspection and behavioral baselines.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun CoreMonitoringCard(
    vpnEnabled: Boolean,
    status: String,
    pid: Int?,
    onToggle: (Boolean) -> Unit
) {
    val accent = if (vpnEnabled) NeonGreen else Color.Gray

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (vpnEnabled) NeonGreen else BorderColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(Color.DarkGray, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = accent)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Core Monitoring Engine", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Intercepting TCP/UDP vectors on all interfaces.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status, pid, vpnEnabled)
                
                BracketedToggle(
                    checked = vpnEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, pid: Int?, active: Boolean) {
    val color = if (active) NeonGreen else Color.Gray
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        border = BorderStroke(1.dp, color),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = "STATUS: $status" + (pid?.let { " // PID: $it" } ?: ""),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BracketedToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(64.dp, 32.dp)
            .then(if (checked) Modifier.glowBorder(NeonGreen, 4.dp) else Modifier)
    ) {
        val bracketColor = if (checked) NeonGreen else Color.DarkGray
        Box(modifier = Modifier.fillMaxSize().border(1.dp, bracketColor.copy(alpha = 0.3f), MaterialTheme.shapes.extraSmall))
        
        ScaledSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.align(Alignment.Center),
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonGreen,
                checkedTrackColor = NeonGreen.copy(alpha = 0.2f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
fun ScaledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    colors: SwitchColors = SwitchDefaults.colors()
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale),
        colors = colors
    )
}

@Composable
fun ExclusionListHeader(activeCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("EXCLUSION LIST", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = Color.DarkGray,
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                "$activeCount ACTIVE",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color.LightGray
            )
        }
    }
}

@Composable
fun ExclusionRow(
    app: AppSummary,
    isExcluded: Boolean,
    onToggle: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(Color.DarkGray, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(app.packageName.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName ?: app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.White)
                Text(
                    "pkg:${app.packageName} proto:tcp/udp",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Baseline", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
            
            ScaledSwitch(
                checked = isExcluded,
                onCheckedChange = onToggle,
                scale = 0.8f,
                colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen)
            )
        }
    }
}

@Composable
fun ExportCard(onExport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onExport() },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = NeonGreen)
            Spacer(modifier = Modifier.width(12.dp))
            Text("EXPORT CONNECTION LOG (CSV)", style = MaterialTheme.typography.labelLarge, color = NeonGreen)
        }
    }
}
