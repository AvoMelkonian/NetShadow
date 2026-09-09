package com.example.netshadow.intelligence.geoip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.netshadow.NetShadowApp

class GeoIpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.netshadow.LOOKUP_IP") {
            val ip = intent.getStringExtra("ip") ?: return
            val app = context.applicationContext as NetShadowApp
            val country = app.geoIpService.getCountryCode(ip)
            if (country == null) {
                Log.w("GeoIpReceiver", "IP: $ip -> Lookup failed (Database might be missing in assets)")
            } else {
                Log.i("GeoIpReceiver", "IP: $ip -> Country: $country")
            }
        }
    }
}
