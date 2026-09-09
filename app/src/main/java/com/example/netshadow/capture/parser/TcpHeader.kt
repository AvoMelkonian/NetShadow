package com.example.netshadow.capture.parser

data class TcpHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val dataOffset: Int,
    val isSyn: Boolean,
    val isAck: Boolean,
    val isFin: Boolean,
    val isRst: Boolean,
    val isPsh: Boolean
) {
    companion object {
        fun parse(data: ByteArray, offset: Int, length: Int): TcpHeader? {
            // Minimum TCP header length is 20 bytes
            if (length - offset < 20) return null

            val sourcePort = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            val destinationPort = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)

            // Data offset is the top nibble of byte 12 (relative to TCP header start)
            val dataOffset = ((data[offset + 12].toInt() and 0xF0) shr 4) * 4

            val flags = data[offset + 13].toInt() and 0xFF
            val isFin = (flags and 0x01) != 0
            val isSyn = (flags and 0x02) != 0
            val isRst = (flags and 0x04) != 0
            val isPsh = (flags and 0x08) != 0
            val isAck = (flags and 0x10) != 0

            return TcpHeader(
                sourcePort = sourcePort,
                destinationPort = destinationPort,
                dataOffset = dataOffset,
                isSyn = isSyn,
                isAck = isAck,
                isFin = isFin,
                isRst = isRst,
                isPsh = isPsh
            )
        }
    }

    override fun toString(): String {
        val flags = mutableListOf<String>()
        if (isSyn) flags.add("SYN")
        if (isAck) flags.add("ACK")
        if (isFin) flags.add("FIN")
        if (isRst) flags.add("RST")
        if (isPsh) flags.add("PSH")
        
        return "TCP $sourcePort -> $destinationPort [${flags.joinToString("|")}], offset=$dataOffset"
    }
}
