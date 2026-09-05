package com.example.netshadow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.netshadow.capture.vpn.NetShadowVpnService
import com.example.netshadow.data.model.AppSummary
import com.example.netshadow.ui.DashboardViewModel
import com.example.netshadow.ui.theme.NetShadowTheme

import com.example.netshadow.ui.navigation.MainNavigationContainer

class MainActivity : ComponentActivity() {

    private var showDenialUI = mutableStateOf(false)

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkBatteryOptimizationAndStart()
        } else {
            showDenialUI.value = true
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVpnService()
        } else {
            // Even if denied, we can try to start the service, 
            // but the notification won't show on API 33+
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val repository = (application as NetShadowApp).trafficRepository
        val viewModelFactory = DashboardViewModel.Factory(repository)

        setContent {
            NetShadowTheme {
                val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory)

                MainNavigationContainer(
                    viewModel = viewModel,
                    showDenial = showDenialUI.value,
                    onStartCapture = { prepareVpn() },
                    onRetry = {
                        showDenialUI.value = false
                        prepareVpn()
                    },
                    onExport = {
                        getExternalFilesDir(null)?.let { viewModel.exportLog(it) }
                    }
                )
            }
        }
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPrepareLauncher.launch(intent)
        } else {
            checkBatteryOptimizationAndStart()
        }
    }

    private fun checkBatteryOptimizationAndStart() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            // Show explanation then request
            Toast.makeText(this, "Please disable battery optimization for NetShadow to ensure stable monitoring.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            // We proceed anyway, but user will see the system dialog
            checkNotificationPermissionAndStart()
        } else {
            checkNotificationPermissionAndStart()
        }
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startVpnService()
            }
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, NetShadowVpnService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "VPN Service Started", Toast.LENGTH_SHORT).show()
    }
}
