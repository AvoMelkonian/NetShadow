package com.example.netshadow.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.netshadow.data.model.Protocol
import com.example.netshadow.data.model.Direction

@Entity(tableName = "connection_events")
data class ConnectionEventEntity(
    @PrimaryKey val connectionId: String,
    val protocol: Protocol,
    val direction: Direction,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val packageName: String,
    val uid: Int,
    val timestamp: Long,
    val resolvedDomain: String? = null,
    val remoteCountry: String? = null,
    val remoteCity: String? = null,
    val remoteAsn: Int? = null,
    val remoteAsnOrg: String? = null,
    val bytesSent: Long,
    val bytesReceived: Long
)
