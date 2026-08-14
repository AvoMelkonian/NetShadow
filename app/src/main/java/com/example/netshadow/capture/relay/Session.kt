package com.example.netshadow.capture.relay

import com.example.netshadow.capture.model.ConnectionKey
import java.net.InetAddress

/**
 * Represents a live network session tracked by the VPN.
 */
data class Session(
    val key: ConnectionKey,
    val uid: Int,
    val packageName: String,
    val domainName: String? = null,
    var lastActive: Long = System.currentTimeMillis(),
    var isTcpFinished: Boolean = false,
    var packetsSent: Long = 0,
    var bytesSent: Long = 0
) {
    fun updateActivity(bytes: Int) {
        lastActive = System.currentTimeMillis()
        packetsSent++
        bytesSent += bytes
    }
}
