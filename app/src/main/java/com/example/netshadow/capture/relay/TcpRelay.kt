package com.example.netshadow.capture.relay

import android.net.VpnService
import android.util.Log
import com.example.netshadow.capture.parser.IpHeader
import com.example.netshadow.capture.parser.TcpHeader
import com.example.netshadow.capture.reader.PacketReader
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * A lightweight TCP Relay that handles TCP connections intercepted by the VPN.
 * 
 * NOTE: For a full production implementation, a user-space TCP stack (like lwIP) 
 * is required to handle the TCP state machine (SYN/ACK, windows, etc.).
 * This implementation demonstrates the integration seam for attribution.
 */
class TcpRelay(
    private val vpnService: VpnService,
    private val packetReader: PacketReader,
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope
) {
    private val selector = Selector.open()
    private val activeConnections = ConcurrentHashMap<SocketChannel, RelaySession>()
    private var relayJob: Job? = null

    data class RelaySession(
        val session: Session,
        val remoteChannel: SocketChannel
    )

    fun start() {
        relayJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    if (selector.select(1000) == 0) continue
                    
                    val keys = selector.selectedKeys().iterator()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        keys.remove()

                        if (!key.isValid) continue

                        if (key.isConnectable) {
                            handleConnect(key)
                        } else if (key.isReadable) {
                            handleRead(key)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Relay selector error", e)
            }
        }
    }

    /**
     * Intercepts a TCP packet. If it's a SYN, it initiates a connection.
     * In a real implementation, this would involve spoofing a SYN-ACK back to the TUN.
     */
    fun handleTcpPacket(ipHeader: IpHeader, tcpHeader: TcpHeader, data: ByteArray, length: Int) {
        val session = sessionManager.getOrCreateSession(
            6, ipHeader, tcpHeader.sourcePort, tcpHeader.destinationPort, length - ipHeader.ihl - tcpHeader.dataOffset
        )

        if (tcpHeader.isSyn && !tcpHeader.isAck) {
            initiateConnection(session)
        } else if (tcpHeader.isFin || tcpHeader.isRst) {
            closeConnection(session)
        } else {
            // Forward data to the established SocketChannel
            // This requires mapping the ConnectionKey to the SocketChannel
        }
    }

    private fun initiateConnection(session: Session) {
        scope.launch(Dispatchers.IO) {
            try {
                val channel = SocketChannel.open()
                channel.configureBlocking(false)
                
                // CRITICAL: Protect the socket to avoid routing loops
                vpnService.protect(channel.socket())

                val remoteAddr = InetSocketAddress(session.key.destinationAddress, session.key.destinationPort)
                channel.connect(remoteAddr)
                
                channel.register(selector, SelectionKey.OP_CONNECT, session)
                Log.d(TAG, "Initiated relay for ${session.packageName} to $remoteAddr")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to initiate relay for ${session.packageName}", e)
            }
        }
    }

    private fun handleConnect(key: SelectionKey) {
        val channel = key.channel() as SocketChannel
        val session = key.attachment() as Session
        try {
            if (channel.finishConnect()) {
                key.interestOps(SelectionKey.OP_READ)
                activeConnections[channel] = RelaySession(session, channel)
                Log.i(TAG, "Connected to ${session.key.destinationAddress}:${session.key.destinationPort} for ${session.packageName}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Connect failed", e)
            channel.close()
        }
    }

    private fun handleRead(key: SelectionKey) {
        val channel = key.channel() as SocketChannel
        val relaySession = activeConnections[channel] ?: return
        val buffer = ByteBuffer.allocate(16384)
        
        try {
            val bytesRead = channel.read(buffer)
            if (bytesRead == -1) {
                closeChannel(channel)
                return
            }
            
            if (bytesRead > 0) {
                buffer.flip()
                val data = ByteArray(bytesRead)
                buffer.get(data)
                
                // In a real implementation, we would wrap this data in TCP/IP headers 
                // and write it back to the TUN interface.
                // packetReader.writePacket(wrapInIpTcp(relaySession, data))
                
                relaySession.session.updateReceived(bytesRead)
                Log.v(TAG, "Relayed $bytesRead bytes from ${relaySession.session.domainName ?: relaySession.session.key.destinationAddress} to ${relaySession.session.packageName}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Read failed", e)
            closeChannel(channel)
        }
    }

    private fun closeConnection(session: Session) {
        // Find and close the SocketChannel associated with this session
    }

    private fun closeChannel(channel: SocketChannel) {
        activeConnections.remove(channel)
        try {
            channel.close()
        } catch (e: IOException) {
            // Ignore
        }
    }

    fun stop() {
        relayJob?.cancel()
        selector.close()
        activeConnections.values.forEach { it.remoteChannel.close() }
        activeConnections.clear()
    }

    companion object {
        private const val TAG = "TcpRelay"
    }
}
