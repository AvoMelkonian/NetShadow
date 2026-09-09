package com.example.netshadow.intelligence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.Direction
import com.example.netshadow.data.model.Protocol
import com.example.netshadow.data.repository.TrafficStats
import com.example.netshadow.intelligence.geoip.GeoIpService
import com.example.netshadow.intelligence.trackers.TrackerMatcher
import com.example.netshadow.intelligence.rules.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuleTest {

    private fun createBaseEvent(
        timestamp: Long = System.currentTimeMillis(),
        bytesSent: Long = 100,
        remoteAddress: String = "8.8.8.8",
        resolvedDomain: String? = null
    ) = ConnectionEventEntity(
        connectionId = "test",
        protocol = Protocol.TCP,
        direction = Direction.Outbound,
        localAddress = "10.0.0.2",
        localPort = 1234,
        remoteAddress = remoteAddress,
        remotePort = 443,
        packageName = "com.test",
        uid = 1000,
        timestamp = timestamp,
        resolvedDomain = resolvedDomain,
        bytesSent = bytesSent,
        bytesReceived = 0
    )

    private fun createBaseBaseline(
        allowedIps: List<String> = listOf("8.8.8.8"),
        allowedDomains: List<String> = listOf("google.com"),
        allowedCountries: List<String> = listOf("US"),
        typicalActiveHours: List<Int> = List(24) { 1 }
    ) = AppBaselineEntity(
        packageName = "com.test",
        allowedDomains = allowedDomains,
        allowedIps = allowedIps,
        allowedCountries = allowedCountries,
        typicalDailyBytesSent = 1000,
        typicalDailyBytesReceived = 1000,
        typicalActiveHours = typicalActiveHours,
        lastUpdated = System.currentTimeMillis()
    )

    @Test
    fun testByteSpikeRule() {
        val rule = ByteSpikeRule()
        val stats = TrafficStats(mean = 100.0, stdDev = 10.0)
        
        // Threshold = 100 + 3*10 = 130
        
        // Under threshold
        assertNull(rule.evaluate(createBaseEvent(bytesSent = 120), null, stats))
        
        // Exact threshold (should be false if using >)
        assertNull(rule.evaluate(createBaseEvent(bytesSent = 130), null, stats))
        
        // Over threshold
        val result = rule.evaluate(createBaseEvent(bytesSent = 131), null, stats)
        assertNotNull(result)
        assertTrue(result!!.isAnomaly)
    }

    @Test
    fun testUnusualHourRule() {
        val rule = UnusualHourRule()
        
        // Baseline with only hour 12 active
        val activeHours = List(24) { if (it == 12) 1 else 0 }
        val baseline = createBaseBaseline(typicalActiveHours = activeHours)
        
        val calendar = Calendar.getInstance()
        
        // Activity at 12:00
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        assertNull(rule.evaluate(createBaseEvent(timestamp = calendar.timeInMillis), baseline, null))
        
        // Activity at 13:00
        calendar.set(Calendar.HOUR_OF_DAY, 13)
        val result = rule.evaluate(createBaseEvent(timestamp = calendar.timeInMillis), baseline, null)
        assertNotNull(result)
        assertTrue(result!!.isAnomaly)
    }

    @Test
    fun testNewDomainRule() {
        val rule = NewDomainRule()
        val baseline = createBaseBaseline(allowedDomains = listOf("google.com"))
        
        // Known domain
        assertNull(rule.evaluate(createBaseEvent(resolvedDomain = "google.com"), baseline, null))
        
        // New domain
        val result = rule.evaluate(createBaseEvent(resolvedDomain = "malicious.com"), baseline, null)
        assertNotNull(result)
        assertTrue(result!!.isAnomaly)
    }

    @Test
    fun testNewIpRule() {
        val rule = NewIpRule()
        val baseline = createBaseBaseline(allowedIps = listOf("8.8.8.8"))
        
        // Known IP
        assertNull(rule.evaluate(createBaseEvent(remoteAddress = "8.8.8.8"), baseline, null))
        
        // New IP
        val result = rule.evaluate(createBaseEvent(remoteAddress = "1.1.1.1"), baseline, null)
        assertNotNull(result)
        assertTrue(result!!.isAnomaly)
    }

    @Test
    fun testNewCountryRule() {
        val mockGeoIpService = object : GeoIpService(ApplicationProvider.getApplicationContext()) {
            override fun getCountryCode(ipAddress: String): String? {
                return when (ipAddress) {
                    "8.8.8.8" -> "US"
                    "1.1.1.1" -> "AU"
                    else -> null
                }
            }
        }
        
        val rule = NewCountryRule(mockGeoIpService)
        val baseline = createBaseBaseline(allowedCountries = listOf("US"))
        
        // Known Country
        assertNull(rule.evaluate(createBaseEvent(remoteAddress = "8.8.8.8"), baseline, null))
        
        // New Country
        val result = rule.evaluate(createBaseEvent(remoteAddress = "1.1.1.1"), baseline, null)
        assertNotNull(result)
        assertTrue(result!!.isAnomaly)
        assertEquals("AU", result.target)
    }

    @Test
    fun testTrackerRule() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matcher = TrackerMatcher(context)
        val rule = TrackerRule(matcher)
        
        // "doubleclick.net" is in our trackers.json
        val result = rule.evaluate(createBaseEvent(resolvedDomain = "ads.doubleclick.net"), null, null)
        assertNotNull(result)
        assertTrue(result!!.isAnomaly)
        
        // Test subdomain matching specifically
        assertTrue(matcher.isTracker("sub.ads.doubleclick.net"))
        assertTrue(matcher.isTracker("doubleclick.net"))
        assertFalse(matcher.isTracker("not-doubleclick.net"))
        
        // Non-tracker
        assertNull(rule.evaluate(createBaseEvent(resolvedDomain = "example.com"), null, null))
    }
}
