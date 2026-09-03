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
        connectionEventDao.upsert(event.toEntity())
    }

    suspend fun logConnections(events: List<ConnectionEvent>) = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext
        connectionEventDao.upsertAll(events.map { it.toEntity() })
    }

    private fun ConnectionEvent.toEntity(): ConnectionEventEntity {
        return ConnectionEventEntity(
            connectionId = this.connectionId,
            protocol = when (this.protocol) {
                NetworkProtocol.TCP -> Protocol.TCP
                NetworkProtocol.UDP -> Protocol.UDP
                NetworkProtocol.OTHER -> Protocol.Unknown(0)
            },
            direction = when (this.direction) {
                TrafficDirection.INBOUND -> Direction.Inbound
                TrafficDirection.OUTBOUND -> Direction.Outbound
            },
            localAddress = "10.0.0.2",
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
