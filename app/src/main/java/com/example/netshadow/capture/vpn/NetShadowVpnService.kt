package com.example.netshadow.capture.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.netshadow.MainActivity
import com.example.netshadow.capture.attribution.TrafficAttributor
import com.example.netshadow.capture.dns.DnsCache
import com.example.netshadow.capture.dns.DnsParser
import com.example.netshadow.capture.dns.DnsRelay
import com.example.netshadow.capture.model.AttributionStatus
import com.example.netshadow.capture.model.ConnectionEvent
import com.example.netshadow.capture.model.ConnectionKey
import com.example.netshadow.capture.parser.IpHeader
import com.example.netshadow.capture.parser.TcpHeader
import com.example.netshadow.capture.parser.UdpHeader
import com.example.netshadow.capture.reader.PacketReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class NetShadowVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var packetReader: PacketReader? = null
    private lateinit var trafficAttributor: TrafficAttributor
    private val dnsCache = DnsCache()
    private lateinit var dnsRelay: DnsRelay

    // Connection cache to avoid redundant UID/Package lookups
    private val connectionCache = ConcurrentHashMap<ConnectionKey, ConnectionEvent>()

    // Exposed flow for UI or other observers
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>()
    val connectionEvents = _connectionEvents.asSharedFlow()

    override fun onCreate() {
        super.onCreate()
        trafficAttributor = TrafficAttributor(this)
        dnsRelay = DnsRelay(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        promoteToForeground()
        setupVpnInterface()
        
        return START_STICKY
    }

    private fun setupVpnInterface() {
        val builder = Builder()

        try {
            vpnInterface = builder
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .setBlocking(false)
                .setSession("NetShadowVPN")
                .establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
            } else {
                Log.i(TAG, "VPN interface established")
                startPacketReader(vpnInterface!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error establishing VPN interface", e)
            stopSelf()
        }
    }

    private fun startPacketReader(pfd: ParcelFileDescriptor) {
        val reader = PacketReader(pfd, serviceScope)
        packetReader = reader
        reader.start()

        // Downstream consumer coroutine
        serviceScope.launch(Dispatchers.Default) {
            for (packet in reader.packets) {
                try {
                    // Parse IPv4 Header with defensive checks
                    val ipHeader = IpHeader.parse(packet.data, packet.length)
                    if (ipHeader != null) {
                        when (ipHeader.protocol) {
                            6 -> { // TCP
                                val tcpHeader = TcpHeader.parse(packet.data, ipHeader.payloadOffset, packet.length)
                                if (tcpHeader != null) {
                                    processTcpPacket(ipHeader, tcpHeader)
                                }
                            }
                            17 -> { // UDP
                                val udpHeader = UdpHeader.parse(packet.data, ipHeader.payloadOffset, packet.length)
                                if (udpHeader != null) {
                                    processUdpPacket(ipHeader, udpHeader, packet.data, packet.length)
                                }
                            }
                            else -> {
                                // Skip ICMP, IGMP, etc. for now
                                Log.v(TAG, "Skipping non-TCP/UDP protocol: ${ipHeader.protocol}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Critical parsing error - dropping packet", e)
                } finally {
                    // Release buffer back to pool
                    reader.releaseBuffer(packet.data)
                }
            }
        }
    }

    private fun processTcpPacket(ipHeader: IpHeader, tcpHeader: TcpHeader) {
        val key = ConnectionKey(
            6,
            ipHeader.sourceAddress,
            tcpHeader.sourcePort,
            ipHeader.destinationAddress,
            tcpHeader.destinationPort
        )
        
        val event = connectionCache.getOrPut(key) {
            val local = InetSocketAddress(ipHeader.sourceAddress, tcpHeader.sourcePort)
            val remote = InetSocketAddress(ipHeader.destinationAddress, tcpHeader.destinationPort)
            val uid = trafficAttributor.getUid(6, local, remote)
            val packageName = trafficAttributor.getPackageName(uid)
            val domainName = dnsCache.get(ipHeader.destinationAddress)
            val status = when {
                uid == android.os.Process.INVALID_UID -> AttributionStatus.UNATTRIBUTED
                uid < 10000 -> AttributionStatus.SYSTEM
                else -> AttributionStatus.RESOLVED
            }
            
            ConnectionEvent(
                6,
                ipHeader.sourceAddress,
                tcpHeader.sourcePort,
                ipHeader.destinationAddress,
                tcpHeader.destinationPort,
                uid,
                packageName,
                domainName,
                status
            ).also {
                serviceScope.launch { _connectionEvents.emit(it) }
            }
        }
        
        Log.v(TAG, "TCP Packet: ${event.packageName} (${event.domainName ?: event.destinationAddress}) (UID=${event.uid})")
    }

    private fun processUdpPacket(ipHeader: IpHeader, udpHeader: UdpHeader, packetData: ByteArray, packetLength: Int) {
        val key = ConnectionKey(
            17,
            ipHeader.sourceAddress,
            udpHeader.sourcePort,
            ipHeader.destinationAddress,
            udpHeader.destinationPort
        )

        val event = connectionCache.getOrPut(key) {
            val local = InetSocketAddress(ipHeader.sourceAddress, udpHeader.sourcePort)
            val remote = InetSocketAddress(ipHeader.destinationAddress, udpHeader.destinationPort)
            val uid = trafficAttributor.getUid(17, local, remote)
            val packageName = trafficAttributor.getPackageName(uid)
            val domainName = dnsCache.get(ipHeader.destinationAddress)
            val status = when {
                uid == android.os.Process.INVALID_UID -> AttributionStatus.UNATTRIBUTED
                uid < 10000 -> AttributionStatus.SYSTEM
                else -> AttributionStatus.RESOLVED
            }

            ConnectionEvent(
                17,
                ipHeader.sourceAddress,
                udpHeader.sourcePort,
                ipHeader.destinationAddress,
                udpHeader.destinationPort,
                uid,
                packageName,
                domainName,
                status
            ).also {
                serviceScope.launch { _connectionEvents.emit(it) }
            }
        }

        // DNS Detection & Filtering
        if (DnsParser.isDnsPacket(udpHeader.destinationPort) || DnsParser.isDnsPacket(udpHeader.sourcePort)) {
            val dnsMessage = DnsParser.parse(packetData, ipHeader.payloadOffset + 8, packetLength)
            if (dnsMessage != null) {
                if (dnsMessage.isResponse) {
                    // Populate DNS Cache from answers
                    dnsMessage.answers.forEach { record ->
                        record.address?.let { addr ->
                            dnsCache.put(addr, record.name)
                        }
                    }
                } else {
                    // Relay DNS Query and Inject Response
                    val queryCopy = packetData.copyOfRange(0, packetLength)
                    serviceScope.launch {
                        val responsePacket = dnsRelay.relay(queryCopy, ipHeader, udpHeader)
                        if (responsePacket != null) {
                            // Parse the response to populate cache before injecting
                            // Offset is 28 (20 IP + 8 UDP)
                            val responseMessage = DnsParser.parse(responsePacket, 28, responsePacket.size)
                            responseMessage?.answers?.forEach { record ->
                                record.address?.let { addr ->
                                    dnsCache.put(addr, record.name)
                                }
                            }
                            packetReader?.writePacket(responsePacket)
                        }
                    }
                }
                
                val type = if (dnsMessage.isResponse) "Response" else "Query"
                val qName = dnsMessage.questions.firstOrNull()?.name ?: "unknown"
                Log.i(TAG, "DNS $type [ID=${dnsMessage.transactionId}]: ${event.packageName} -> $qName")
            } else {
                Log.v(TAG, "UDP Packet: ${event.packageName} (UID=${event.uid}) for ${ipHeader.destinationAddress}:${udpHeader.destinationPort}")
            }
        } else {
            Log.v(TAG, "UDP Packet: ${event.packageName} (${event.domainName ?: event.destinationAddress}) (UID=${event.uid})")
        }
    }

    private fun promoteToForeground() {
        createNotificationChannel()

        val stopIntent = Intent(this, NetShadowVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetShadow is active")
            .setContentText("Monitoring network traffic...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }

    private fun stopVpn() {
        packetReader?.stop()
        packetReader = null
        connectionCache.clear() // Cache invalidation on service stop
        
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        } finally {
            vpnInterface = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.i(TAG, "Service being destroyed")
        serviceScope.cancel()
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NetShadow Traffic Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows that NetShadow is capturing network traffic"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "NetShadowVpnService"
        private const val CHANNEL_ID = "traffic_monitor_channel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.example.netshadow.STOP_VPN"
    }
}
