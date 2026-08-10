package com.example.netshadow.capture.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.netshadow.MainActivity

class NetShadowVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        promoteToForeground()
        setupVpnInterface()
        
        return START_STICKY
    }

    private fun setupVpnInterface() {
        val builder = Builder()

        try {
            vpnInterface = builder
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .setBlocking(false)
                .setSession("NetShadowVPN")
                .establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
            } else {
                Log.i(TAG, "VPN interface established")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error establishing VPN interface", e)
            stopSelf()
        }
    }

    private fun promoteToForeground() {
        createNotificationChannel()

        val stopIntent = Intent(this, NetShadowVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetShadow is active")
            .setContentText("Monitoring network traffic...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NetShadow Traffic Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows that NetShadow is capturing network traffic"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "NetShadowVpnService"
        private const val CHANNEL_ID = "traffic_monitor_channel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.example.netshadow.STOP_VPN"
    }
}
