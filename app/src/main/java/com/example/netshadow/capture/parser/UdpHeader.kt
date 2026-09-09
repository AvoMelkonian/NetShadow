package com.example.netshadow.capture.parser

data class UdpHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val length: Int
) {
    companion object {
        fun parse(data: ByteArray, offset: Int, length: Int): UdpHeader? {
            // UDP header length is fixed at 8 bytes
            if (length - offset < 8) return null

            val sourcePort = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            val destinationPort = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
            val udpLength = ((data[offset + 4].toInt() and 0xFF) shl 8) or (data[offset + 5].toInt() and 0xFF)

            return UdpHeader(
                sourcePort = sourcePort,
                destinationPort = destinationPort,
                length = udpLength
            )
        }
    }

    fun isDns(): Boolean = sourcePort == 53 || destinationPort == 53
    fun isLikelyQuic(): Boolean = sourcePort == 443 || destinationPort == 443

    override fun toString(): String {
        val type = when {
            isDns() -> "DNS"
            isLikelyQuic() -> "QUIC?"
            else -> "UDP"
        }
        return "$type $sourcePort -> $destinationPort, len=$length"
    }
}
