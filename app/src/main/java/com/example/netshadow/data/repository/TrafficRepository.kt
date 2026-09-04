package com.example.netshadow.data.repository

import com.example.netshadow.capture.model.ConnectionEvent
import com.example.netshadow.capture.model.NetworkProtocol
import com.example.netshadow.capture.model.TrafficDirection
import com.example.netshadow.data.dao.AppBaselineDao
import com.example.netshadow.data.dao.ConnectionEventDao
import com.example.netshadow.data.dao.HourlyTraffic
import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.Direction
import com.example.netshadow.data.model.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.sqrt

class TrafficRepository(
    private val connectionEventDao: ConnectionEventDao,
    private val appBaselineDao: AppBaselineDao
) {

    suspend fun logConnection(event: ConnectionEvent) = withContext(Dispatchers.IO) {
        connectionEventDao.upsert(event.toEntity())
    }

    suspend fun logConnections(events: List<ConnectionEvent>) = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext
        connectionEventDao.upsertAll(events.map { it.toEntity() })
    }

    suspend fun computeBaseline(packageName: String) = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - 7 * 24 * 3600000L // 7 days window
        
        val hourlyTraffic = connectionEventDao.getHourlyTrafficStats(packageName, since).first()
        val destinations = connectionEventDao.getKnownDestinations(packageName, since).first()
        
        if (hourlyTraffic.isEmpty()) return@withContext

        // Compute 24-hour histogram
        val histogram = IntArray(24) { 0 }
        val calendar = Calendar.getInstance()
        
        hourlyTraffic.forEach { 
            calendar.timeInMillis = it.hourTimestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            // Increment if total bytes in that hour were non-zero
            if (it.totalBytesSent > 0) {
                histogram[hour]++
            }
        }

        val totalBytesSent = hourlyTraffic.sumOf { it.totalBytesSent }
        // Simple daily average over the window (approximate)
        val typicalDailySent = totalBytesSent / 7

        val baseline = AppBaselineEntity(
            packageName = packageName,
            allowedDomains = emptyList(), // Needs DNS resolution integration later
            allowedIps = destinations,
            typicalDailyBytesSent = typicalDailySent,
            typicalDailyBytesReceived = 0, // Need to fetch received bytes too
            typicalActiveHours = histogram.toList(),
            lastUpdated = System.currentTimeMillis()
        )
        
        appBaselineDao.insertOrUpdate(baseline)
    }

    suspend fun recomputeAllBaselines() = withContext(Dispatchers.IO) {
        val apps = connectionEventDao.getAllPackageNames()
        apps.forEach { computeBaseline(it) }
    }

    fun getKnownDestinations(packageName: String, since: Long): Flow<List<String>> {
        return connectionEventDao.getKnownDestinations(packageName, since)
    }

    fun getHourlyStats(packageName: String, since: Long): Flow<TrafficStats> {
        return connectionEventDao.getHourlyTrafficStats(packageName, since).map { hourlyList ->
            if (hourlyList.isEmpty()) return@map TrafficStats(0.0, 0.0)

            val values = hourlyList.map { it.totalBytesSent.toDouble() }
            val mean = values.average()
            val variance = values.map { (it - mean) * (it - mean) }.average()
            TrafficStats(mean, sqrt(variance))
        }
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
            resolvedDomain = this.resolvedDomain,
            bytesSent = this.bytesSent,
            bytesReceived = this.bytesReceived
        )
    }
}

data class TrafficStats(
    val mean: Double,
    val stdDev: Double
)
