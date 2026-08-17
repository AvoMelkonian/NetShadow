package com.example.netshadow.capture.model

/**
 * The direction of the network traffic.
 */
enum class TrafficDirection {
    OUTBOUND,
    INBOUND
}

/**
 * Supported network protocols for traffic attribution.
 */
enum class NetworkProtocol {
    TCP,
    UDP,
    OTHER
}

/**
 * Represents a resolved attribution status for a connection.
 */
enum class AttributionStatus {
    RESOLVED,
    UNATTRIBUTED,
    SYSTEM
}

/**
 * The primary data model for captured network events in NetShadow.
 */
data class ConnectionEvent(
    val connectionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val uid: Int,
    val packageName: String,
    val protocol: NetworkProtocol,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    val resolvedDomain: String? = null,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val direction: TrafficDirection
)
