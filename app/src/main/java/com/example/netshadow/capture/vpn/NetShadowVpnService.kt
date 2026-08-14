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
import com.example.netshadow.capture.relay.TcpRelay
import com.example.netshadow.capture.relay.Tun2SocksEngine
import com.example.netshadow.capture.relay.SessionManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private lateinit var sessionManager: SessionManager
    private var tcpRelay: TcpRelay? = null

    // Connection cache to avoid redundant UID/Package lookups
    private val connectionCache = ConcurrentHashMap<ConnectionKey, ConnectionEvent>()

    // Exposed flow for UI or other observers
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>()
    val connectionEvents = _connectionEvents.asSharedFlow()

    override fun onCreate() {
        super.onCreate()
        trafficAttributor = TrafficAttributor(this)
        dnsRelay = DnsRelay(this)
        sessionManager = SessionManager(trafficAttributor, dnsCache)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        promoteToForeground()
        setupVpnInterface()
        startSessionCleanup()
        
        return START_STICKY
    }

    private fun startSessionCleanup() {
        serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(30_000)
                sessionManager.cleanupIdleSessions()
            }
        }
    }

    private fun setupVpnInterface() {
        val builder = Builder()

        try {
            vpnInterface = builder
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
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
                
                // Start tun2socks on the shim FD provided by PacketReader
                packetReader?.theirFd?.let { fd ->
                    // Convert FileDescriptor to Int using reflection or JNI
                    val fdInt = getFdInt(fd)
                    Tun2SocksEngine.start(fdInt)
                }

                tcpRelay = TcpRelay(this, packetReader!!, sessionManager, serviceScope)
                tcpRelay?.start()
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
                                    // Forward to tun2socks for the actual TCP relaying
                                    reader.forwardToShim(packet.data, packet.length)
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
        val session = sessionManager.getOrCreateSession(
            6, ipHeader, tcpHeader.sourcePort, tcpHeader.destinationPort, ipHeader.totalLength - ipHeader.ihl
        )
        
        sessionManager.updateTcpSession(session.key, tcpHeader)

        val event = connectionCache.getOrPut(session.key) {
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
                protocol = 6,
                sourceAddress = ipHeader.sourceAddress,
                sourcePort = tcpHeader.sourcePort,
                destinationAddress = ipHeader.destinationAddress,
                destinationPort = tcpHeader.destinationPort,
                uid = uid,
                packageName = packageName,
                domainName = domainName,
                status = status
            ).also {
                serviceScope.launch { _connectionEvents.emit(it) }
            }
        }
        
        Log.v(TAG, "TCP Packet: ${event.packageName} (${event.domainName ?: event.destinationAddress}) (UID=${event.uid})")
    }

    private fun processUdpPacket(ipHeader: IpHeader, udpHeader: UdpHeader, packetData: ByteArray, packetLength: Int) {
        val session = sessionManager.getOrCreateSession(
            17, ipHeader, udpHeader.sourcePort, udpHeader.destinationPort, udpHeader.length - 8
        )

        // DNS Detection & Parsing
        var dnsQueryName: String? = null
        val isDns = DnsParser.isDnsPacket(udpHeader.destinationPort) || DnsParser.isDnsPacket(udpHeader.sourcePort)
        val dnsMessage = if (isDns) DnsParser.parse(packetData, ipHeader.payloadOffset + 8, packetLength) else null
        
        if (dnsMessage != null && !dnsMessage.isResponse) {
            dnsQueryName = dnsMessage.questions.firstOrNull()?.name
        }

        val event = connectionCache[session.key] ?: run {
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
                protocol = 17,
                sourceAddress = ipHeader.sourceAddress,
                sourcePort = udpHeader.sourcePort,
                destinationAddress = ipHeader.destinationAddress,
                destinationPort = udpHeader.destinationPort,
                uid = uid,
                packageName = packageName,
                domainName = domainName,
                dnsQuery = dnsQueryName,
                status = status
            ).also {
                connectionCache[session.key] = it
                serviceScope.launch { _connectionEvents.emit(it) }
            }
        }

        // Update dnsQuery if we just discovered it in an existing session
        if (dnsQueryName != null && event.dnsQuery == null) {
            val updatedEvent = event.copy(dnsQuery = dnsQueryName)
            connectionCache[session.key] = updatedEvent
            serviceScope.launch { _connectionEvents.emit(updatedEvent) }
        }

        // DNS Relay Logic
        if (dnsMessage != null) {
            if (dnsMessage.isResponse) {
                // Populate DNS Cache from answers
                dnsMessage.answers.forEach { record ->
                    record.address?.let { addr ->
                        dnsCache.put(addr, record.name)
                    }
                }
                // Forward responses back to the app (if we aren't the ones who generated them)
                // packetReader?.forwardToShim(packetData, packetLength)
            } else {
                // Relay DNS Query and Inject Response
                val queryCopy = packetData.copyOfRange(0, packetLength)
                serviceScope.launch {
                    val responsePacket = dnsRelay.relay(queryCopy, ipHeader, udpHeader)
                    if (responsePacket != null) {
                        // Parse the response to populate cache before injecting
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
            // Forward non-DNS UDP traffic to tun2socks for relaying
            packetReader?.forwardToShim(packetData, packetLength)
            Log.v(TAG, "UDP Packet Forwarded: ${event.packageName} (${event.domainName ?: event.destinationAddress}) (UID=${event.uid})")
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

    private fun getFdInt(fd: java.io.FileDescriptor): Int {
        return try {
            val field = java.io.FileDescriptor::class.java.getDeclaredField("descriptor")
            field.isAccessible = true
            field.get(fd) as Int
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FD int", e)
            -1
        }
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }

    private fun stopVpn() {
        Tun2SocksEngine.stop()
        tcpRelay?.stop()
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
