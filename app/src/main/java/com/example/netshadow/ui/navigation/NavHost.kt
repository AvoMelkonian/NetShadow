package com.example.netshadow.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
    
    val intelViewModel: IntelViewModel = viewModel(
        factory = IntelViewModel.Factory(repository)
    )
    val alertsViewModel: AlertsViewModel = viewModel(
        factory = AlertsViewModel.Factory(repository)
    )
    val ctrlViewModel: CtrlViewModel = viewModel(
        factory = CtrlViewModel.Factory(repository)
    )

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            NetShadowTopAppBar(
                leadingIcon = null,
                trailingIcon = null
            )
        },
        bottomBar = {
            NetShadowBottomNavigation(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    // Deep navigation fix: 
                    // 1. If we are navigating to the current top-level item, do nothing or scroll to top
                    // 2. Otherwise, clear everything and go to the new screen
                    if (currentRoute?.startsWith(screen.route) != true) {
                        navController.navigate(screen.route) {
                            // Pop up to the absolute root of the graph
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
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
                    },
                    onAppClick = { packageName ->
                        navController.navigate("${Screen.Intel.route}/$packageName")
                    }
                )
            }
            composable(
                route = Screen.Intel.routeWithArgs,
                arguments = listOf(navArgument(Screen.Intel.argPackageName) {
                    type = NavType.StringType
                    nullable = true
                })
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString(Screen.Intel.argPackageName)
                LaunchedEffect(packageName) {
                    if (packageName != null) {
                        intelViewModel.selectApp(packageName)
                    }
                }
                IntelScreen(intelViewModel)
            }
            composable(Screen.Intel.route) { IntelScreen(intelViewModel) }
            composable(Screen.Alerts.route) {
                AlertsScreen(
                    viewModel = alertsViewModel,
                    isWideScreen = false // Could use WindowSizeClass here later
                )
            }
            composable(Screen.Ctrl.route) { 
                CtrlScreen(
                    viewModel = ctrlViewModel,
                    onToggleVpn = { active ->
                        onToggleCapture(active)
                        if (active) {
                            statsViewModel.startMockFeed()
                        } else {
                            statsViewModel.stopMockFeed()
                        }
                    }
                ) 
            }
        }
    }
}
