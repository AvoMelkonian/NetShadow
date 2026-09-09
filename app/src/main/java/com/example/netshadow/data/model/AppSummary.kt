package com.example.netshadow.data.model

data class AppSummary(
    val packageName: String,
    val appName: String?,
    val liveConnectionCount: Int,
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val alertCount: Int
)
