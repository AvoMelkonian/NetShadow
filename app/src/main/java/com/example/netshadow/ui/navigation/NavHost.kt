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

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.netshadow.data.repository.TrafficRepository
import com.example.netshadow.ui.AlertsViewModel
import com.example.netshadow.ui.CtrlViewModel
import com.example.netshadow.ui.IntelViewModel
import com.example.netshadow.ui.StatsViewModel

@Composable
fun MainNavigationContainer(
    repository: TrafficRepository,
    showDenial: Boolean,
    onToggleCapture: (Boolean) -> Unit,
    onRetry: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // StatsViewModel needs a custom factory for the repository
    val statsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(repository)
    )
    
    // For now, others can be instantiated directly or with simple factories if they need repository
    // Stubbing them with simple instantiation if they don't have custom factories yet
    // Actually, Intel and Alerts also need repository. I should add factories to them too eventually.
    // For Part 3, I'll just provide them.
    val intelViewModel: IntelViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = IntelViewModel(repository) as T
        }
    )
    val alertsViewModel: AlertsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = AlertsViewModel(repository) as T
        }
    )
    val ctrlViewModel: CtrlViewModel = viewModel()

    val context = androidx.compose.ui.platform.LocalContext.current

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
                    viewModel = statsViewModel,
                    showDenial = showDenial,
                    onToggleCapture = { active ->
                        onToggleCapture(active)
                        if (active) {
                            statsViewModel.startMockFeed()
                        } else {
                            statsViewModel.stopMockFeed()
                        }
                    },
                    onRetry = onRetry,
                    onExport = {
                        context.getExternalFilesDir(null)?.let { statsViewModel.exportLog(it) }
                    }
                )
            }
            composable(Screen.Intel.route) { IntelScreen(intelViewModel) }
            composable(Screen.Alerts.route) { AlertsScreen(alertsViewModel) }
            composable(Screen.Ctrl.route) { CtrlScreen(ctrlViewModel) }
        }
    }
}
