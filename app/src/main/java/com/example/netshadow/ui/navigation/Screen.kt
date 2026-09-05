package com.example.netshadow.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Stats : Screen("stats", "STATS", Icons.Default.BarChart)
    data object Intel : Screen("intel", "INTEL", Icons.AutoMirrored.Filled.List)
    data object Alerts : Screen("alerts", "ALERTS", Icons.Default.Notifications)
    data object Ctrl : Screen("ctrl", "CTRL", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Stats,
    Screen.Intel,
    Screen.Alerts,
    Screen.Ctrl
)
