package com.example.netshadow.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_baselines")
data class AppBaselineEntity(
    @PrimaryKey val packageName: String,
    val allowedDomains: List<String>,
    val allowedIps: List<String>,
    val allowedCountries: List<String>,
    val typicalDailyBytesSent: Long,
    val typicalDailyBytesReceived: Long,
    val typicalActiveHours: List<Int>, // 24-hour histogram
    val lastUpdated: Long
)
