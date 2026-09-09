package com.example.netshadow.ui.preview

import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.Direction
import com.example.netshadow.data.model.Protocol
import java.util.UUID
import kotlin.random.Random

/**
 * Factory for producing realistic fake ConnectionEvents for UI previews and testing.
 * Fulfills Phase 2, Part A requirements.
 */
object PreviewConnectionEventFactory {

    private val apps = listOf(
        "com.android.chrome" to 1001,
        "com.whatsapp" to 1002,
        "com.google.android.gms" to 1000,
        "com.facebook.orca" to 1003,
        "com.spotify.music" to 1004,
        "com.example.netshadow" to 1005,
        "com.instagram.android" to 1006,
        "com.twitter.android" to 1007
    )

    private val countries = listOf("US", "DE", "GB", "FR", "JP", "CA", "AU", "BR", "IN", "SG")
    private val anomalyCountries = listOf("RU", "CN", "KP", "IR", "SY")

    private val commonDomains = listOf(
        "google.com", "github.com", "android.com", "whatsapp.net", 
        "spotify.com", "apple.com", "amazon.com", "microsoft.com",
        "instagram.com", "twitter.com", "facebook.com", "netflix.com"
    )

    private val trackerDomains = listOf(
        "google-analytics.com", "doubleclick.net", "app-measurement.com", 
        "facebook.net", "scorecardresearch.com", "crashlytics.com",
        "ads.yahoo.com", "adservice.google.com"
    )

    fun createEvent(
        packageName: String? = null,
        uid: Int? = null,
        country: String? = null,
        domain: String? = null,
        isIpv6: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
        protocol: Protocol = if (Random.nextBoolean()) Protocol.TCP else Protocol.UDP,
        bytesSent: Long = Random.nextLong(100, 10000),
        bytesReceived: Long = Random.nextLong(500, 50000),
        direction: Direction = Direction.Outbound
    ): ConnectionEventEntity {
        val app = if (packageName != null && uid != null) packageName to uid else apps.random()
        
        val remoteIp = if (isIpv6) {
            "2001:4860:4860::${Random.nextInt(1000, 9999)}"
        } else {
            "${Random.nextInt(1, 255)}.${Random.nextInt(0, 255)}.${Random.nextInt(0, 255)}.${Random.nextInt(1, 255)}"
        }

        // Simulate tracker flag by domain selection
        val resolvedDomain = domain ?: when {
            Random.nextFloat() < 0.15f -> trackerDomains.random()
            Random.nextFloat() < 0.7f -> commonDomains.random()
            else -> null // Edge case: null resolvedDomain
        }

        return ConnectionEventEntity(
            connectionId = UUID.randomUUID().toString(),
            protocol = protocol,
            direction = direction,
            localAddress = "10.0.0.2",
            localPort = Random.nextInt(32768, 65535),
            remoteAddress = remoteIp,
            remotePort = when (protocol) {
                is Protocol.TCP -> listOf(80, 443, 8080, 8443).random()
                else -> listOf(53, 123, 443, 5060).random()
            },
            packageName = app.first,
            uid = app.second,
            timestamp = timestamp,
            resolvedDomain = resolvedDomain,
            remoteCountry = country ?: countries.random(),
            remoteCity = listOf("New York", "London", "Berlin", "Tokyo", "Paris").random(),
            remoteAsn = Random.nextInt(100, 50000),
            remoteAsnOrg = listOf("Google LLC", "Amazon.com", "Microsoft Corp", "DigitalOcean", "Cloudflare").random(),
            bytesSent = bytesSent,
            bytesReceived = bytesReceived
        )
    }

    /**
     * Generates a "quiet realistic day" batch.
     */
    fun generateQuietDay(count: Int = 30): List<ConnectionEventEntity> {
        val now = System.currentTimeMillis()
        return (1..count).map {
            createEvent(timestamp = now - Random.nextLong(0, 86400000))
        }.sortedByDescending { it.timestamp }
    }

    /**
     * Generates a "chaotic burst" batch for testing dense/anomaly states.
     */
    fun generateChaoticBurst(targetApp: String = "com.suspicious.app", count: Int = 60): List<ConnectionEventEntity> {
        val now = System.currentTimeMillis()
        val burstUid = 9999
        
        val normalEvents = (1..15).map { createEvent(timestamp = now - 3600000) }
        
        // High-frequency burst
        val burstEvents = (1..count).map {
            createEvent(
                packageName = targetApp,
                uid = burstUid,
                timestamp = now - Random.nextLong(0, 30000), // Within last 30 seconds
                isIpv6 = Random.nextFloat() < 0.4f,
                country = if (Random.nextFloat() < 0.3f) anomalyCountries.random() else countries.random(),
                bytesSent = Random.nextLong(500000, 5000000) // High volume
            )
        }

        // Flagged-anomaly event (Specific target and protocol)
        val anomaly = createEvent(
            packageName = targetApp,
            uid = burstUid,
            timestamp = now,
            country = "KP",
            domain = "c2.malware-network.org",
            protocol = Protocol.Unknown(999),
            bytesSent = 2048,
            bytesReceived = 0
        )

        return (normalEvents + burstEvents + anomaly).sortedByDescending { it.timestamp }
    }
}
