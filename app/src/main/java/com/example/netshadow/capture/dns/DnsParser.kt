package com.example.netshadow.capture.dns

import android.util.Log

/**
 * Basic representation of a DNS question section.
 */
data class DnsQuestion(
    val name: String,
    val type: Int,
    val qclass: Int
)

/**
 * Basic representation of a DNS message (RFC 1035).
 */
data class DnsMessage(
    val transactionId: Int,
    val isResponse: Boolean,
    val questions: List<DnsQuestion>
)

/**
 * Parser for DNS wire-format packets (RFC 1035).
 */
class DnsParser {
    companion object {
        private const val TAG = "DnsParser"

        fun isDnsPacket(port: Int): Boolean {
            return port == 53
        }

        /**
         * Parses a raw DNS message from a UDP payload.
         * 
         * @param data The raw packet data (IP + UDP + DNS).
         * @param offset The offset where the DNS header starts.
         * @param length The total length of the packet data.
         * @return A parsed DnsMessage or null if parsing fails.
         */
        fun parse(data: ByteArray, offset: Int, length: Int): DnsMessage? {
            val dnsLength = length - offset
            if (dnsLength < 12) return null // Minimum DNS header length

            // Header Parsing
            val id = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            val flags = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
            val isResponse = (flags and 0x8000) != 0
            val qdCount = ((data[offset + 4].toInt() and 0xFF) shl 8) or (data[offset + 5].toInt() and 0xFF)

            // Only parse the question section for now
            val questions = mutableListOf<DnsQuestion>()
            var currentPos = offset + 12

            try {
                repeat(qdCount) {
                    val (qname, nextPos) = parseQName(data, currentPos, length)
                    if (nextPos + 4 > length) throw Exception("Truncated question section")
                    
                    val qtype = ((data[nextPos].toInt() and 0xFF) shl 8) or (data[nextPos + 1].toInt() and 0xFF)
                    val qclass = ((data[nextPos + 2].toInt() and 0xFF) shl 8) or (data[nextPos + 3].toInt() and 0xFF)
                    
                    questions.add(DnsQuestion(qname, qtype, qclass))
                    currentPos = nextPos + 4
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS Parse Error: ${e.message}")
                return null
            }

            return DnsMessage(id, isResponse, questions)
        }

        private fun parseQName(data: ByteArray, startOffset: Int, totalLength: Int): Pair<String, Int> {
            val sb = StringBuilder()
            var pos = startOffset
            var jumped = false
            var resultPos = -1

            // Simple label parser (handles basic names, pointers skipped for simple query logging)
            while (pos < totalLength) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) {
                    pos++
                    break
                }

                if ((len and 0xC0) == 0xC0) {
                    // Compression pointer - usually found in responses
                    // For queries we usually don't see this, but handle it defensively
                    if (!jumped) resultPos = pos + 2
                    val pointer = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    // Note: We'd normally jump to pointer here, but for simple logging
                    // we'll just stop to avoid recursion loops in a basic implementation.
                    sb.append("[compressed]")
                    jumped = true
                    break
                } else {
                    if (sb.isNotEmpty()) sb.append(".")
                    if (pos + 1 + len > totalLength) throw Exception("QNAME out of bounds")
                    sb.append(String(data, pos + 1, len, Charsets.US_ASCII))
                    pos += 1 + len
                }
            }
            
            val finalPos = if (jumped) resultPos else pos
            return sb.toString() to finalPos
        }
    }
}
