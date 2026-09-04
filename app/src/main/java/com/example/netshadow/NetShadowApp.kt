package com.example.netshadow

import android.app.Application
import com.example.netshadow.data.NetShadowDatabase
import com.example.netshadow.data.repository.TrafficRepository

class NetShadowApp : Application() {
    val database by lazy { NetShadowDatabase.getDatabase(this) }
    val trafficRepository by lazy { 
        TrafficRepository(
            database.connectionEventDao(),
            database.appBaselineDao(),
            database.anomalyAlertDao()
        ) 
    }
}
