package com.example.netshadow.capture.parser

import java.net.InetAddress
import java.nio.ByteBuffer

data class IpHeader(
    val version: Int,
    val ihl: Int, // Header length in bytes
    val protocol: Int,
    val ttl: Int,
    val sourceAddress: InetAddress,
    val destinationAddress: InetAddress,
    val totalLength: Int,
    val payloadOffset: Int
) {
    companion object {
        private const val PROTOCOL_TCP = 6
        private const val PROTOCOL_UDP = 17
        private const val PROTOCOL_ICMP = 1

        fun parse(data: ByteArray, length: Int): IpHeader? {
            if (length < 20) return null // Minimum IPv4 header length

            val firstByte = data[0].toInt() and 0xFF
            val version = firstByte shr 4
            if (version != 4) return null // Only IPv4 supported for now

            val ihl = (firstByte and 0x0F) * 4
            if (length < ihl) return null // Packet shorter than its header

            val totalLength = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val ttl = data[8].toInt() and 0xFF
            val protocol = data[9].toInt() and 0xFF

            val sourceAddress = InetAddress.getByAddress(data.sliceArray(12..15))
            val destinationAddress = InetAddress.getByAddress(data.sliceArray(16..19))

            return IpHeader(
                version = version,
                ihl = ihl,
                protocol = protocol,
                ttl = ttl,
                sourceAddress = sourceAddress,
                destinationAddress = destinationAddress,
                totalLength = totalLength,
                payloadOffset = ihl
            )
        }
    }

    override fun toString(): String {
        val protoStr = when (protocol) {
            6 -> "TCP"
            17 -> "UDP"
            1 -> "ICMP"
            else -> "Proto($protocol)"
        }
        return "IPv4 $sourceAddress -> $destinationAddress [$protoStr], len=$totalLength, ihl=$ihl, ttl=$ttl"
    }
}
