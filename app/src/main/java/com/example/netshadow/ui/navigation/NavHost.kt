package com.example.netshadow.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.netshadow.ui.components.NetShadowBottomNavigation
import com.example.netshadow.ui.components.NetShadowTopAppBar
import com.example.netshadow.ui.screens.AlertsScreen
import com.example.netshadow.ui.screens.CtrlScreen
import com.example.netshadow.ui.screens.IntelScreen
import com.example.netshadow.ui.screens.StatsScreen
import com.example.netshadow.ui.theme.Black
import com.example.netshadow.ui.theme.NeonGreen

import androidx.compose.runtime.collectAsState
import com.example.netshadow.ui.DashboardViewModel

@Composable
fun MainNavigationContainer(
    viewModel: DashboardViewModel,
    showDenial: Boolean,
    onStartCapture: () -> Unit,
    onRetry: () -> Unit,
    onExport: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val summaries by viewModel.appSummaries.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()

    Scaffold(
        topBar = {
            NetShadowTopAppBar(
                leadingIcon = {
                    when (currentRoute) {
                        Screen.Stats.route -> Icon(Icons.Default.Person, contentDescription = "Profile", tint = NeonGreen)
                        Screen.Ctrl.route -> Icon(Icons.Default.Menu, contentDescription = "Menu", tint = NeonGreen)
                        else -> null
                    }
                },
                trailingIcon = {
                    when (currentRoute) {
                        Screen.Ctrl.route -> Icon(Icons.Default.Shield, contentDescription = "Shield", tint = NeonGreen)
                        else -> null
                    }
                }
            )
        },
        bottomBar = {
            NetShadowBottomNavigation(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        },
        containerColor = Black
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Stats.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Stats.route) {
                StatsScreen(
                    showDenial = showDenial,
                    summaries = summaries,
                    exportStatus = exportStatus,
                    onStartCapture = onStartCapture,
                    onRetry = onRetry,
                    onExport = onExport
                )
            }
            composable(Screen.Intel.route) { IntelScreen() }
            composable(Screen.Alerts.route) { AlertsScreen() }
            composable(Screen.Ctrl.route) { CtrlScreen() }
        }
    }
}
