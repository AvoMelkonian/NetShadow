package com.example.netshadow.capture.dns

import android.net.VpnService
import android.util.Log
import com.example.netshadow.capture.parser.IpHeader
import com.example.netshadow.capture.parser.UdpHeader
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class DnsRelay(private val vpnService: VpnService) {

    private val upstreamDns = InetAddress.getByName("8.8.8.8")
    
    // Create a bounded dispatcher to limit concurrent DNS requests
    // and prevent thread exhaustion under heavy load.
    private val dnsDispatcher = Executors.newFixedThreadPool(16).asCoroutineDispatcher()

    suspend fun relay(
        queryData: ByteArray,
        ipHeader: IpHeader,
        udpHeader: UdpHeader
    ): ByteArray? = withContext(dnsDispatcher) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = 5000

            // Extract DNS payload from original packet
            val dnsPayloadOffset = ipHeader.payloadOffset + 8
            val dnsPayloadLength = udpHeader.length - 8
            val dnsPayload = queryData.copyOfRange(dnsPayloadOffset, dnsPayloadOffset + dnsPayloadLength)

            val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstreamDns, 53)
            socket.send(outPacket)

            val inBuffer = ByteArray(1500)
            val inPacket = DatagramPacket(inBuffer, inBuffer.size)
            socket.receive(inPacket)

            val responseData = inPacket.data.copyOfRange(0, inPacket.length)
            
            // Build the injected packet
            buildInjectedPacket(responseData, ipHeader, udpHeader)
        } catch (e: Exception) {
            Log.e(TAG, "DNS Relay error", e)
            null
        } finally {
            socket?.close()
        }
    }

    private fun buildInjectedPacket(
        dnsResponse: ByteArray,
        origIp: IpHeader,
        origUdp: UdpHeader
    ): ByteArray {
        val totalLength = 20 + 8 + dnsResponse.size
        val packet = ByteArray(totalLength)

        // IP Header (20 bytes)
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[8] = 64.toByte() // TTL
        packet[9] = 17.toByte() // Protocol UDP
        
        // Source and Destination swap
        val srcIp = origIp.destinationAddress.address
        val dstIp = origIp.sourceAddress.address
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        // IP Checksum
        val ipChecksum = computeChecksum(packet, 0, 20)
        packet[10] = (ipChecksum shr 8).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header (8 bytes)
        val srcPort = origUdp.destinationPort
        val dstPort = origUdp.sourcePort
        packet[20] = (srcPort shr 8).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = (dstPort shr 8).toByte()
        packet[23] = (dstPort and 0xFF).toByte()
        val udpLength = 8 + dnsResponse.size
        packet[24] = (udpLength shr 8).toByte()
        packet[25] = (udpLength and 0xFF).toByte()
        
        // DNS Payload
        System.arraycopy(dnsResponse, 0, packet, 28, dnsResponse.size)

        // UDP Checksum (Optional in IPv4, but good practice)
        // For simplicity and to match common lightweight VPN implementations, 
        // we can set it to 0 (ignored) or calculate it.
        // Let's set it to 0 first, if it fails nslookup we'll calculate it.
        packet[26] = 0
        packet[27] = 0

        return packet
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        var len = length
        while (len > 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            len -= 2
        }
        if (len > 0) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF)
    }

    companion object {
        private const val TAG = "DnsRelay"
    }
}
