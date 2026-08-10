package com.example.netshadow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.netshadow.capture.vpn.NetShadowVpnService
import com.example.netshadow.ui.theme.NetShadowTheme

class MainActivity : ComponentActivity() {

    private var showDenialUI = mutableStateOf(false)

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkNotificationPermissionAndStart()
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
        setContent {
            NetShadowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        showDenial = showDenialUI.value,
                        onStartCapture = { prepareVpn() },
                        onRetry = {
                            showDenialUI.value = false
                            prepareVpn()
                        }
                    )
                }
            }
        }
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPrepareLauncher.launch(intent)
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

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    showDenial: Boolean,
    onStartCapture: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showDenial) {
            Text(
                text = "VPN permission is required to capture network traffic.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        } else {
            Button(onClick = onStartCapture) {
                Text("Start Capture")
            }
        }
    }
}
