package com.example.netshadow.data.repository

import com.example.netshadow.capture.model.ConnectionEvent
import com.example.netshadow.capture.model.NetworkProtocol
import com.example.netshadow.capture.model.TrafficDirection
import com.example.netshadow.data.dao.AnomalyAlertDao
import com.example.netshadow.data.dao.AppBaselineDao
import com.example.netshadow.data.dao.ConnectionEventDao
import com.example.netshadow.data.dao.HourlyTraffic
import com.example.netshadow.data.entity.AnomalyAlertEntity
import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.AppDetail
import com.example.netshadow.data.model.Direction
import com.example.netshadow.data.model.Protocol
import com.example.netshadow.intelligence.RuleEvaluator
import com.example.netshadow.intelligence.dns.DefaultDnsResolver
import com.example.netshadow.intelligence.dns.DnsResolver
import com.example.netshadow.intelligence.enrichment.EnrichmentManager
import com.example.netshadow.intelligence.trackers.TrackerMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class TrafficRepository(
    private val connectionEventDao: ConnectionEventDao,
    private val appBaselineDao: AppBaselineDao,
    private val anomalyAlertDao: AnomalyAlertDao,
    private val geoIpService: com.example.netshadow.intelligence.geoip.GeoIpService? = null,
    private val trackerMatcher: TrackerMatcher? = null,
    private val dnsResolver: DnsResolver = DefaultDnsResolver
) {
    private val enrichmentManager = EnrichmentManager(
        connectionEventDao,
        appBaselineDao,
        anomalyAlertDao,
        geoIpService,
        trackerMatcher,
        dnsResolver
    )

    suspend fun logConnection(event: ConnectionEvent) = withContext(Dispatchers.IO) {
        val entity = event.toEntity()
        connectionEventDao.upsert(entity)
        enrichmentManager.enrichAsync(entity)
    }

    suspend fun logConnections(events: List<ConnectionEvent>) = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext

        val entities = events.map { it.toEntity() }
        connectionEventDao.upsertAll(entities)

        entities.forEach { enrichmentManager.enrichAsync(it) }
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

        val allowedCountries = if (geoIpService != null) {
            destinations.mapNotNull { geoIpService.getCountryCode(it) }.distinct()
        } else emptyList()

        val baseline = AppBaselineEntity(
            packageName = packageName,
            allowedDomains = emptyList(), // Needs DNS resolution integration later
            allowedIps = destinations,
            allowedCountries = allowedCountries,
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

    fun getAppSummaries(): Flow<List<com.example.netshadow.data.model.AppSummary>> {
        return connectionEventDao.getAppSummaries()
    }

    fun getAlerts(): Flow<List<AnomalyAlertEntity>> {
        return anomalyAlertDao.getAllAlerts()
    }

    fun getAppDetail(packageName: String): Flow<AppDetail> {
        return combine(
            appBaselineDao.getBaselineFlow(packageName),
            connectionEventDao.getEventsByApp(packageName),
            anomalyAlertDao.getAlertsForApp(packageName)
        ) { baseline, events, alerts ->
            val serverCount = events.map { it.remoteAddress }.distinct().size
            // Use GeoIP to count countries from current events if baseline is thin
            val countries = events.mapNotNull { it.remoteCountry }.distinct()
            val countryCount = countries.size
            
            val summary = "contacts $serverCount servers in $countryCount countries"
            
            AppDetail(
                packageName = packageName,
                baseline = baseline,
                recentEvents = events.take(50),
                alerts = alerts,
                summaryString = summary
            )
        }
    }

    suspend fun markAsExpected(packageName: String, target: String, type: ExpectedType) = withContext(Dispatchers.IO) {
        val existing = appBaselineDao.getBaselineForApp(packageName) ?: return@withContext
        
        val updatedBaseline = when (type) {
            ExpectedType.DOMAIN -> {
                if (existing.allowedDomains.contains(target)) return@withContext
                existing.copy(allowedDomains = existing.allowedDomains + target)
            }
            ExpectedType.IP -> {
                if (existing.allowedIps.contains(target)) return@withContext
                existing.copy(allowedIps = existing.allowedIps + target)
            }
            ExpectedType.COUNTRY -> {
                if (existing.allowedCountries.contains(target)) return@withContext
                existing.copy(allowedCountries = existing.allowedCountries + target)
            }
        }
        
        appBaselineDao.insertOrUpdate(updatedBaseline)
    }

    suspend fun exportTrafficLogAsCsv(): String = withContext(Dispatchers.IO) {
        val events = connectionEventDao.getAllEventsList()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        
        val header = "Timestamp,PackageName,UID,Protocol,Direction,LocalAddress,LocalPort,RemoteAddress,RemotePort,Domain,Country,ASN,ASN_Org,BytesSent,BytesReceived\n"
        
        val body = events.joinToString("\n") { e ->
            val timestamp = dateFormat.format(Date(e.timestamp))
            val protocolStr = when (val p = e.protocol) {
                is Protocol.TCP -> "TCP"
                is Protocol.UDP -> "UDP"
                is Protocol.Unknown -> "Unknown(${p.protocolNumber})"
            }
            val directionStr = when (e.direction) {
                is Direction.Inbound -> "Inbound"
                is Direction.Outbound -> "Outbound"
            }
            
            listOf(
                timestamp,
                e.packageName,
                e.uid.toString(),
                protocolStr,
                directionStr,
                e.localAddress,
                e.localPort.toString(),
                e.remoteAddress,
                e.remotePort.toString(),
                e.resolvedDomain ?: "",
                e.remoteCountry ?: "",
                e.remoteAsn?.toString() ?: "",
                "\"${e.remoteAsnOrg ?: ""}\"", // Quote ASN Org as it may contain commas
                e.bytesSent.toString(),
                e.bytesReceived.toString()
            ).joinToString(",")
        }
        
        header + body
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

enum class ExpectedType {
    DOMAIN, IP, COUNTRY
}
