package com.example.netshadow.capture.dns

import android.util.Log
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Basic representation of a DNS question section.
 */
data class DnsQuestion(
    val name: String,
    val type: Int,
    val qclass: Int
)

/**
 * Basic representation of a DNS resource record (Answer/Authority/Additional).
 */
data class DnsResourceRecord(
    val name: String,
    val type: Int,
    val qclass: Int,
    val ttl: Long,
    val data: ByteArray,
    val address: InetAddress? = null
)

/**
 * Basic representation of a DNS message (RFC 1035).
 */
data class DnsMessage(
    val transactionId: Int,
    val isResponse: Boolean,
    val questions: List<DnsQuestion>,
    val answers: List<DnsResourceRecord> = emptyList()
)

/**
 * Parser for DNS wire-format packets (RFC 1035).
 */
class DnsParser {
    companion object {
        private const val TAG = "DnsParser"
        
        const val TYPE_A = 1
        const val TYPE_AAAA = 28

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
            val anCount = ((data[offset + 6].toInt() and 0xFF) shl 8) or (data[offset + 7].toInt() and 0xFF)

            val questions = mutableListOf<DnsQuestion>()
            val answers = mutableListOf<DnsResourceRecord>()
            var currentPos = offset + 12

            try {
                // Parse Question Section
                repeat(qdCount) {
                    val (qname, nextPos) = parseQName(data, currentPos, length)
                    if (nextPos + 4 > length) throw Exception("Truncated question section")
                    
                    val qtype = ((data[nextPos].toInt() and 0xFF) shl 8) or (data[nextPos + 1].toInt() and 0xFF)
                    val qclass = ((data[nextPos + 2].toInt() and 0xFF) shl 8) or (data[nextPos + 3].toInt() and 0xFF)
                    
                    questions.add(DnsQuestion(qname, qtype, qclass))
                    currentPos = nextPos + 4
                }

                // Parse Answer Section (if response)
                if (isResponse) {
                    repeat(anCount) {
                        val (name, nextPos) = parseQName(data, currentPos, length)
                        if (nextPos + 10 > length) throw Exception("Truncated answer header")

                        val type = ((data[nextPos].toInt() and 0xFF) shl 8) or (data[nextPos + 1].toInt() and 0xFF)
                        val qclass = ((data[nextPos + 2].toInt() and 0xFF) shl 8) or (data[nextPos + 3].toInt() and 0xFF)
                        val ttl = ((data[nextPos + 4].toLong() and 0xFF) shl 24) or 
                                  ((data[nextPos + 5].toLong() and 0xFF) shl 16) or 
                                  ((data[nextPos + 6].toLong() and 0xFF) shl 8) or 
                                  (data[nextPos + 7].toLong() and 0xFF)
                        val rdLength = ((data[nextPos + 8].toInt() and 0xFF) shl 8) or (data[nextPos + 9].toInt() and 0xFF)
                        
                        currentPos = nextPos + 10
                        if (currentPos + rdLength > length) throw Exception("Truncated RDATA")

                        val rdata = data.copyOfRange(currentPos, currentPos + rdLength)
                        var address: InetAddress? = null
                        
                        if (type == TYPE_A && rdLength == 4) {
                            address = InetAddress.getByAddress(rdata)
                        } else if (type == TYPE_AAAA && rdLength == 16) {
                            address = InetAddress.getByAddress(rdata)
                        }

                        answers.add(DnsResourceRecord(name, type, qclass, ttl, rdata, address))
                        currentPos += rdLength
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS Parse Error: ${e.message}")
                return null
            }

            return DnsMessage(id, isResponse, questions, answers)
        }

        private fun parseQName(data: ByteArray, startOffset: Int, totalLength: Int): Pair<String, Int> {
            val sb = StringBuilder()
            var pos = startOffset
            var jumped = false
            var resultPos = -1

            while (pos < totalLength) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) {
                    pos++
                    break
                }

                if ((len and 0xC0) == 0xC0) {
                    // Compression pointer
                    if (pos + 1 >= totalLength) throw Exception("Truncated pointer")
                    if (!jumped) resultPos = pos + 2
                    val pointer = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    
                    // Recursive call to follow the pointer (limited depth for safety)
                    val (suffix, _) = parseQName(data, offset = pointer, totalLength = totalLength, depth = 1)
                    if (sb.isNotEmpty()) sb.append(".")
                    sb.append(suffix)
                    
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
        
        // Helper for decompression with depth limit
        private fun parseQName(data: ByteArray, offset: Int, totalLength: Int, depth: Int): Pair<String, Int> {
            if (depth > 5) return "[too_deep]" to offset // Avoid infinite loops
            val sb = StringBuilder()
            var pos = offset
            
            while (pos < totalLength) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) {
                    pos++
                    break
                }
                if ((len and 0xC0) == 0xC0) {
                    val pointer = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    val (suffix, _) = parseQName(data, pointer, totalLength, depth + 1)
                    if (sb.isNotEmpty()) sb.append(".")
                    sb.append(suffix)
                    pos += 2
                    break
                } else {
                    if (sb.isNotEmpty()) sb.append(".")
                    sb.append(String(data, pos + 1, len, Charsets.US_ASCII))
                    pos += 1 + len
                }
            }
            return sb.toString() to pos
        }
    }
}
