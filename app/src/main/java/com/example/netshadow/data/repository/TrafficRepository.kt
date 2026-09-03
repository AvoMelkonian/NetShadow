package com.example.netshadow.data.repository

import com.example.netshadow.capture.model.ConnectionEvent
import com.example.netshadow.capture.model.NetworkProtocol
import com.example.netshadow.capture.model.TrafficDirection
import com.example.netshadow.data.dao.ConnectionEventDao
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.Direction
import com.example.netshadow.data.model.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrafficRepository(private val connectionEventDao: ConnectionEventDao) {

    suspend fun logConnection(event: ConnectionEvent) = withContext(Dispatchers.IO) {
        val existing = connectionEventDao.getEventById(event.connectionId)
        val entity = event.toEntity(existing?.id ?: 0)
        connectionEventDao.insert(entity)
    }

    private fun ConnectionEvent.toEntity(id: Long): ConnectionEventEntity {
        return ConnectionEventEntity(
            id = id,
            connectionId = this.connectionId,
            protocol = when (this.protocol) {
                NetworkProtocol.TCP -> Protocol.TCP
                NetworkProtocol.UDP -> Protocol.UDP
                NetworkProtocol.OTHER -> Protocol.Unknown(0) // Should ideally capture original protocol number
            },
            direction = when (this.direction) {
                TrafficDirection.INBOUND -> Direction.Inbound
                TrafficDirection.OUTBOUND -> Direction.Outbound
            },
            localAddress = "10.0.0.2", // VPN local address
            localPort = this.srcPort,
            remoteAddress = this.dstIp,
            remotePort = this.dstPort,
            packageName = this.packageName,
            uid = this.uid,
            timestamp = this.timestamp,
            bytesSent = this.bytesSent,
            bytesReceived = this.bytesReceived
        )
    }
}
