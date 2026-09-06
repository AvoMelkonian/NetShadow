package com.example.netshadow.intelligence.enrichment

import android.util.Log
import com.example.netshadow.data.dao.AnomalyAlertDao
import com.example.netshadow.data.dao.AppBaselineDao
import com.example.netshadow.data.dao.ConnectionEventDao
import com.example.netshadow.data.entity.AnomalyAlertEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.repository.TrafficStats
import com.example.netshadow.intelligence.RuleEvaluator
import com.example.netshadow.intelligence.dns.DnsResolver
import com.example.netshadow.intelligence.geoip.GeoIpService
import com.example.netshadow.intelligence.trackers.TrackerMatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.sqrt

class EnrichmentManager(
    private val connectionEventDao: ConnectionEventDao,
    private val appBaselineDao: AppBaselineDao,
    private val anomalyAlertDao: AnomalyAlertDao,
    private val geoIpService: GeoIpService?,
    private val trackerMatcher: TrackerMatcher?,
    private val dnsResolver: DnsResolver,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val ruleEvaluator = RuleEvaluator(geoIpService, trackerMatcher)

    fun enrichAsync(event: ConnectionEventEntity) {
        scope.launch {
            try {
                var enriched = event
                
                // 1. DNS Enrichment
                if (enriched.resolvedDomain == null) {
                    val hostname = dnsResolver.reverseLookup(enriched.remoteAddress)
                    if (hostname != null) {
                        enriched = enriched.copy(resolvedDomain = hostname)
                    }
                }
                
                // 2. GeoIP & ASN Enrichment
                geoIpService?.let { service ->
                    val country = service.getCountryCode(enriched.remoteAddress)
                    val asnInfo = service.getAsnInfo(enriched.remoteAddress)
                    
                    enriched = enriched.copy(
                        remoteCountry = country,
                        remoteAsn = asnInfo?.asn,
                        remoteAsnOrg = asnInfo?.organization
                    )
                }
                
                // 3. Persist enriched entity
                if (enriched != event) {
                    connectionEventDao.upsert(enriched)
                }

                // 4. Rule Evaluation (now with enriched data)
                evaluateRules(enriched)
                
            } catch (e: Exception) {
                Log.e("EnrichmentManager", "Error enriching event ${event.connectionId}: ${e.message}")
            }
        }
    }

    private suspend fun evaluateRules(event: ConnectionEventEntity) {
        val baseline = appBaselineDao.getBaselineForApp(event.packageName)
        val since = System.currentTimeMillis() - 7 * 24 * 3600000L
        
        // Use a helper to get stats without circular dependency if possible, 
        // but here we can just query the DAO directly or similar logic as in TrafficRepository
        val stats = getHourlyStats(event.packageName, since)
        
        val results = ruleEvaluator.evaluateAll(event, baseline, stats)
        
        results.forEach { result ->
            val alert = AnomalyAlertEntity(
                timestamp = System.currentTimeMillis(),
                type = result.type,
                severity = result.severity,
                message = result.message,
                packageName = event.packageName,
                connectionId = event.connectionId,
                target = result.target
            )
            anomalyAlertDao.insertIgnore(alert)
        }
    }

    private suspend fun getHourlyStats(packageName: String, since: Long): TrafficStats? {
        val hourlyList = connectionEventDao.getHourlyTrafficStats(packageName, since).first()
        if (hourlyList.isEmpty()) return null

        val values = hourlyList.map { it.totalBytesSent.toDouble() }
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return TrafficStats(mean, sqrt(variance))
    }
}
