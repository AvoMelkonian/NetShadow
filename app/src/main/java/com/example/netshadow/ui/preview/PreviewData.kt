package com.example.netshadow.ui.preview

import com.example.netshadow.data.entity.AnomalyAlertEntity
import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.*

object PreviewData {
    val sampleAppSummary = AppSummary(
        packageName = "com.android.chrome",
        appName = "Chrome",
        liveConnectionCount = 5,
        totalBytesSent = 1024 * 1024 * 12,
        totalBytesReceived = 1024 * 1024 * 142,
        alertCount = 2
    )

    val sampleAlert = AnomalyAlertEntity(
        id = 1,
        timestamp = System.currentTimeMillis() - 3600000,
        type = AlertType.GEOGRAPHIC_ANOMALY,
        severity = Severity.HIGH,
        message = "Connection to unusual country: RU",
        packageName = "com.android.chrome",
        connectionId = "conn_1",
        target = "RU"
    )

    val sampleAlertEvent = AlertEvent(
        id = 1,
        timestamp = System.currentTimeMillis() - 3600000,
        type = AlertType.GEOGRAPHIC_ANOMALY,
        severity = Severity.HIGH,
        message = "Connection to unusual country: RU",
        packageName = "com.android.chrome",
        isRead = false,
        affectedTarget = "RU"
    )

    val sampleBaseline = AppBaselineEntity(
        packageName = "com.android.chrome",
        allowedDomains = listOf("google.com", "github.com", "gstatic.com"),
        allowedIps = listOf("8.8.8.8", "1.1.1.1"),
        allowedCountries = listOf("US", "DE", "GB"),
        typicalDailyBytesSent = 50_000_000,
        typicalDailyBytesReceived = 200_000_000,
        typicalActiveHours = List(24) { if (it in 8..22) 1 else 0 },
        lastUpdated = System.currentTimeMillis()
    )

    val sampleBaselineSummary = BaselineSummary(
        packageName = "com.android.chrome",
        allowedCountries = listOf("US", "DE", "GB"),
        allowedDomains = listOf("google.com", "github.com"),
        typicalDailyBytesSent = 50_000_000,
        typicalDailyBytesReceived = 200_000_000,
        activeHours = (8..22).toList(),
        trustScore = 0.85f,
        summaryText = "Standard browsing behavior; primarily US/EU traffic."
    )

    val sampleAppDetail = AppDetail(
        packageName = "com.android.chrome",
        baseline = sampleBaseline,
        recentEvents = PreviewConnectionEventFactory.generateQuietDay(10),
        alerts = listOf(sampleAlert),
        summaryString = "contacts 42 servers in 5 countries"
    )
    
    val chaoticEvents = PreviewConnectionEventFactory.generateChaoticBurst("com.android.chrome", 50)
}
