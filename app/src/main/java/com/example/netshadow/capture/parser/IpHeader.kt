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
            // Basic bounds check for minimum IPv4 header
            if (length < 20) return null 

            val firstByte = data[0].toInt() and 0xFF
            val version = firstByte shr 4
            if (version != 4) return null // Only IPv4 supported

            val ihl = (firstByte and 0x0F) * 4
            if (length < ihl) return null // Packet shorter than its header

            // Check for fragmentation
            // Byte 6: Flags (3 bits) + Fragment Offset (high 5 bits)
            // Byte 7: Fragment Offset (low 8 bits)
            val flagsAndOffset = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            val isFragment = (flagsAndOffset and 0x3FFF) != 0 // MF flag or non-zero offset
            if (isFragment) {
                // Fragmentation reassembly not implemented yet
                return null
            }

            val totalLength = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            // Truncated packet check
            if (length < totalLength) return null

            val ttl = data[8].toInt() and 0xFF
            val protocol = data[9].toInt() and 0xFF

            // Safe address extraction
            val sourceAddress = try {
                InetAddress.getByAddress(data.sliceArray(12..15))
            } catch (e: Exception) {
                return null
            }
            val destinationAddress = try {
                InetAddress.getByAddress(data.sliceArray(16..19))
            } catch (e: Exception) {
                return null
            }

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
