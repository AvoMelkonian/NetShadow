package com.example.netshadow.ui.preview

import com.example.netshadow.data.entity.AnomalyAlertEntity
import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.*

object PreviewData {
    val sampleAppSummary = AppSummary(
        packageName = "com.example.browser",
        appName = "Browser",
        liveConnectionCount = 3,
        totalBytesSent = 1024 * 1024 * 5,
        totalBytesReceived = 1024 * 1024 * 25,
        alertCount = 1
    )

    val sampleAlert = AnomalyAlertEntity(
        id = 1,
        timestamp = System.currentTimeMillis() - 3600000,
        type = AlertType.GEOGRAPHIC_ANOMALY,
        severity = Severity.HIGH,
        message = "Connection to unusual country: RU",
        packageName = "com.example.browser",
        connectionId = "conn_1",
        target = "RU"
    )

    val sampleBaseline = AppBaselineEntity(
        packageName = "com.example.browser",
        allowedDomains = listOf("google.com", "github.com"),
        allowedIps = listOf("8.8.8.8", "1.1.1.1"),
        allowedCountries = listOf("US", "DE"),
        typicalDailyBytesSent = 1000000,
        typicalDailyBytesReceived = 5000000,
        typicalActiveHours = List(24) { if (it in 9..17) 1 else 0 },
        lastUpdated = System.currentTimeMillis()
    )

    val sampleAppDetail = AppDetail(
        packageName = "com.example.browser",
        baseline = sampleBaseline,
        recentEvents = emptyList(),
        alerts = listOf(sampleAlert),
        summaryString = "contacts 12 servers in 3 countries"
    )
}
