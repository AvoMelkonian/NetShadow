package com.example.netshadow.capture.model

import java.net.InetAddress

enum class AttributionStatus {
    RESOLVED,
    UNATTRIBUTED,
    SYSTEM
}

data class ConnectionEvent(
    val protocol: Int,
    val sourceAddress: InetAddress,
    val sourcePort: Int,
    val destinationAddress: InetAddress,
    val destinationPort: Int,
    val uid: Int,
    val packageName: String,
    val domainName: String? = null,
    val dnsQuery: String? = null,
    val status: AttributionStatus,
    val timestamp: Long = System.currentTimeMillis()
)
