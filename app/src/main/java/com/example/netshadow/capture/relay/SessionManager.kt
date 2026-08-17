package com.example.netshadow.capture.relay

import android.util.Log
import com.example.netshadow.capture.attribution.TrafficAttributor
import com.example.netshadow.capture.dns.DnsCache
import com.example.netshadow.capture.model.ConnectionKey
import com.example.netshadow.capture.parser.IpHeader
import com.example.netshadow.capture.parser.TcpHeader
import com.example.netshadow.capture.parser.UdpHeader
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class SessionManager(
    private val trafficAttributor: TrafficAttributor,
    private val dnsCache: DnsCache
) {
    private val sessions = ConcurrentHashMap<ConnectionKey, Session>()

    /**
     * Retrieves an existing session or creates a new one with attribution.
     */
    fun getOrCreateSession(
        protocol: Int,
        ipHeader: IpHeader,
        srcPort: Int,
        dstPort: Int,
        payloadLength: Int
    ): Session {
        val key = ConnectionKey(
            protocol,
            ipHeader.sourceAddress,
            srcPort,
            ipHeader.destinationAddress,
            dstPort
        )

        return sessions.getOrPut(key) {
            val local = InetSocketAddress(ipHeader.sourceAddress, srcPort)
            val remote = InetSocketAddress(ipHeader.destinationAddress, dstPort)
            val uid = trafficAttributor.getUid(protocol, local, remote)
            val packageName = trafficAttributor.getPackageName(uid)
            val domainName = dnsCache.get(ipHeader.destinationAddress)

            Log.i(TAG, "New Session: $packageName ($domainName) [${key.toShortString()}]")
            
            Session(key, uid, packageName, domainName)
        }.apply {
            updateSent(payloadLength)
        }
    }

    /**
     * Handles TCP specific lifecycle updates (FIN/RST).
     */
    fun updateTcpSession(key: ConnectionKey, tcpHeader: TcpHeader) {
        if (tcpHeader.isFin || tcpHeader.isRst) {
            val session = sessions[key]
            if (session != null) {
                Log.d(TAG, "TCP Session closing: ${session.packageName} [${key.toShortString()}]")
                sessions.remove(key)
            }
        }
    }

    /**
     * Periodic cleanup of idle sessions (especially UDP).
     */
    fun cleanupIdleSessions(udpTimeoutMs: Long = 60_000, tcpTimeoutMs: Long = 300_000) {
        val now = System.currentTimeMillis()
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val session = entry.value
            val timeout = if (session.key.protocol == 17) udpTimeoutMs else tcpTimeoutMs
            
            if (now - session.lastActive > timeout) {
                Log.d(TAG, "Evicting idle session: ${session.packageName} [${session.key.toShortString()}]")
                iterator.remove()
            }
        }
    }

    private fun ConnectionKey.toShortString(): String {
        val proto = if (protocol == 6) "TCP" else "UDP"
        return "$proto $sourceAddress:$sourcePort -> $destinationAddress:$destinationPort"
    }

    companion object {
        private const val TAG = "SessionManager"
    }
}
