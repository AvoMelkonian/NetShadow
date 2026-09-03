package com.example.netshadow.capture.model

import java.net.InetAddress

data class ConnectionKey(
    val protocol: Int,
    val sourceAddress: InetAddress,
    val sourcePort: Int,
    val destinationAddress: InetAddress,
    val destinationPort: Int
) {
    override fun toString(): String {
        return "$protocol:${sourceAddress.hostAddress}:$sourcePort:${destinationAddress.hostAddress}:$destinationPort"
    }
}
