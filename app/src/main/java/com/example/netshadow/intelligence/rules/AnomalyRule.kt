package com.example.netshadow.intelligence.rules

import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.AlertType
import com.example.netshadow.data.model.Severity
import com.example.netshadow.data.repository.TrafficStats
import com.example.netshadow.intelligence.geoip.GeoIpService
import com.example.netshadow.intelligence.trackers.TrackerMatcher
import java.util.Calendar

data class RuleResult(
    val isAnomaly: Boolean,
    val type: AlertType,
    val severity: Severity,
    val message: String,
    val target: String? = null
)

interface AnomalyRule {
    fun evaluate(
        event: ConnectionEventEntity,
        baseline: AppBaselineEntity?,
        stats: TrafficStats?
    ): RuleResult?
}

class ByteSpikeRule : AnomalyRule {
    override fun evaluate(
        event: ConnectionEventEntity,
        baseline: AppBaselineEntity?,
        stats: TrafficStats?
    ): RuleResult? {
        if (stats == null || stats.mean == 0.0) return null
        
        val threshold = stats.mean + 3 * stats.stdDev
        val currentBytes = event.bytesSent.toDouble()
        
        return if (currentBytes > threshold) {
            RuleResult(
                isAnomaly = true,
                type = AlertType.HIGH_TRAFFIC_VOLUME,
                severity = Severity.HIGH,
                message = "Traffic spike detected: ${event.bytesSent} bytes (Threshold: ${threshold.toLong()})",
                target = "spike"
            )
        } else null
    }
}

class UnusualHourRule : AnomalyRule {
    override fun evaluate(
        event: ConnectionEventEntity,
        baseline: AppBaselineEntity?,
        stats: TrafficStats?
    ): RuleResult? {
        if (baseline == null) return null
        
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = event.timestamp
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return if (baseline.typicalActiveHours.getOrElse(hour) { 0 } == 0) {
            RuleResult(
                isAnomaly = true,
                type = AlertType.UNUSUAL_PORT,
                severity = Severity.MEDIUM,
                message = "Activity at unusual hour: $hour:00",
                target = hour.toString()
            )
        } else null
    }
}

class NewDomainRule : AnomalyRule {
    override fun evaluate(
        event: ConnectionEventEntity,
        baseline: AppBaselineEntity?,
        stats: TrafficStats?
    ): RuleResult? {
        if (baseline == null || event.resolvedDomain == null) return null
        
        return if (!baseline.allowedDomains.contains(event.resolvedDomain)) {
            RuleResult(
                isAnomaly = true,
                type = AlertType.UNAUTHORIZED_DOMAIN,
                severity = Severity.MEDIUM,
                message = "Connection to new domain: ${event.resolvedDomain}",
                target = event.resolvedDomain
            )
        } else null
    }
}

class NewIpRule : AnomalyRule {
    override fun evaluate(
        event: ConnectionEventEntity,
        baseline: AppBaselineEntity?,
        stats: TrafficStats?
    ): RuleResult? {
        if (baseline == null) return null
        
        return if (!baseline.allowedIps.contains(event.remoteAddress)) {
            RuleResult(
                isAnomaly = true,
                type = AlertType.UNAUTHORIZED_DOMAIN,
                severity = Severity.LOW,
                message = "Connection to new IP address: ${event.remoteAddress}",
                target = event.remoteAddress
            )
        } else null
    }
}

class NewCountryRule(private val geoIpService: GeoIpService?) : AnomalyRule {
    override fun evaluate(
        event: ConnectionEventEntity,
        baseline: AppBaselineEntity?,
        stats: TrafficStats?
    ): RuleResult? {
        if (baseline == null || geoIpService == null) return null
        
        val countryCode = geoIpService.getCountryCode(event.remoteAddress) ?: return null
        
        return if (!baseline.allowedCountries.contains(countryCode)) {
            RuleResult(
                isAnomaly = true,
                type = AlertType.UNAUTHORIZED_DOMAIN,
                severity = Severity.HIGH,
                message = "Connection to new country: $countryCode",
                target = countryCode
            )
        } else null
    }
}

class TrackerRule(private val trackerMatcher: TrackerMatcher?) : AnomalyRule {
    override fun evaluate(
        event: ConnectionEventEntity,
        baseline: AppBaselineEntity?,
        stats: TrafficStats?
    ): RuleResult? {
        if (trackerMatcher == null || event.resolvedDomain == null) return null
        
        return if (trackerMatcher.isTracker(event.resolvedDomain)) {
            RuleResult(
                isAnomaly = true,
                type = AlertType.UNAUTHORIZED_DOMAIN, // Could add a specific TRACKER type
                severity = Severity.MEDIUM,
                message = "Connection to known tracker: ${event.resolvedDomain}",
                target = event.resolvedDomain
            )
        } else null
    }
}
