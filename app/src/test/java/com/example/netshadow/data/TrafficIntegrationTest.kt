package com.example.netshadow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netshadow.capture.model.ConnectionEvent
import com.example.netshadow.capture.model.NetworkProtocol
import com.example.netshadow.capture.model.TrafficDirection
import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.Direction
import com.example.netshadow.data.model.Protocol
import com.example.netshadow.data.repository.TrafficRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrafficIntegrationTest {
    private lateinit var db: NetShadowDatabase
    private lateinit var repository: TrafficRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NetShadowDatabase::class.java).build()
        repository = TrafficRepository(
            db.connectionEventDao(),
            db.appBaselineDao(),
            db.anomalyAlertDao()
        )
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testWriteCoalescing() = runBlocking {
        val flow = MutableSharedFlow<ConnectionEvent>()
        val eventBuffer = ConcurrentHashMap<String, ConnectionEvent>()
        val dbWriteCount = AtomicInteger(0)

        // Mocking the collection and flush logic from VpnService
        val collectionJob = launch {
            flow.collect { event ->
                eventBuffer[event.connectionId] = event
            }
        }

        val flushJob = launch {
            while (isActive) {
                delay(100) // Fast flush for testing
                if (eventBuffer.isNotEmpty()) {
                    val toFlush = eventBuffer.values.toList()
                    eventBuffer.clear()
                    repository.logConnections(toFlush)
                    dbWriteCount.incrementAndGet()
                }
            }
        }

        // Fire 100 updates for the SAME connection rapidly
        repeat(100) { i ->
            flow.emit(
                ConnectionEvent(
                    connectionId = "stress_test_conn",
                    uid = 1000,
                    packageName = "com.test.app",
                    protocol = NetworkProtocol.TCP,
                    srcPort = 1234,
                    dstIp = "8.8.8.8",
                    dstPort = 443,
                    direction = TrafficDirection.OUTBOUND,
                    bytesSent = i.toLong(),
                    bytesReceived = 0
                )
            )
            if (i % 10 == 0) kotlinx.coroutines.yield()
        }

        // Wait until it appears in DB
        val events = db.connectionEventDao().getAllEvents().first { it.isNotEmpty() }
        assertEquals(1, events.size)
        assertTrue("Latest byte count should be high", events[0].bytesSent >= 90L)

        val writes = dbWriteCount.get()
        println("Total DB writes: $writes")
        assertTrue("Coalescing failed, too many DB writes: $writes", writes < 50)

        collectionJob.cancel()
        flushJob.cancel()
    }

    @Test
    fun testRollingWindowAggregation() = runBlocking {
        val packageName = "com.test.app"
        val now = System.currentTimeMillis()
        val hourMs = 3600000L
        
        // Seed hourly data: 100, 200, 300, 400, 500
        val data = listOf(100L, 200L, 300L, 400L, 500L)
        data.forEachIndexed { index, bytes ->
            val timestamp = now - (index * hourMs)
            db.connectionEventDao().upsert(
                ConnectionEventEntity(
                    connectionId = "conn_$index",
                    protocol = Protocol.TCP,
                    direction = Direction.Outbound,
                    localAddress = "10.0.0.2",
                    localPort = 1000 + index,
                    remoteAddress = "8.8.8.$index",
                    remotePort = 443,
                    packageName = packageName,
                    uid = 1000,
                    timestamp = timestamp,
                    bytesSent = bytes,
                    bytesReceived = 0
                )
            )
        }

        val stats = repository.getHourlyStats(packageName, now - 7 * 24 * hourMs).first()
        
        // Expected mean: (100+200+300+400+500)/5 = 300
        // Expected variance: ((100-300)^2 + (200-300)^2 + (300-300)^2 + (400-300)^2 + (500-300)^2) / 5
        // = (40000 + 10000 + 0 + 10000 + 40000) / 5 = 100000 / 5 = 20000
        // Expected stdDev: sqrt(20000) ≈ 141.42
        
        assertEquals(300.0, stats.mean, 0.001)
        assertEquals(sqrt(20000.0), stats.stdDev, 0.001)

        val destinations = repository.getKnownDestinations(packageName, now - 7 * 24 * hourMs).first()
        assertEquals(5, destinations.size)
        assertTrue(destinations.contains("8.8.8.0"))
        assertTrue(destinations.contains("8.8.8.4"))
    }

    @Test
    fun testComputeBaseline() = runBlocking {
        val packageName = "com.test.baseline"
        val now = System.currentTimeMillis()
        val hourMs = 3600000L

        // Generate traffic for two different hours
        val timestamps = listOf(
            now - 24 * hourMs, // 1 day ago, current hour
            now - 25 * hourMs  // 1 day ago, 1 hour before
        )

        timestamps.forEachIndexed { index, ts ->
            db.connectionEventDao().upsert(
                ConnectionEventEntity(
                    connectionId = "conn_b_$index",
                    protocol = Protocol.TCP,
                    direction = Direction.Outbound,
                    localAddress = "10.0.0.2",
                    localPort = 2000 + index,
                    remoteAddress = "1.2.3.$index",
                    remotePort = 80,
                    packageName = packageName,
                    uid = 2000,
                    timestamp = ts,
                    bytesSent = 1000,
                    bytesReceived = 0
                )
            )
        }

        repository.computeBaseline(packageName)

        val baseline = db.appBaselineDao().getBaselineForApp(packageName)
        assertEquals(packageName, baseline?.packageName)
        assertEquals(2, baseline?.allowedIps?.size)
        // typicalActiveHours should have two slots filled with 1
        assertEquals(2, baseline?.typicalActiveHours?.count { it > 0 })
    }

    @Test
    fun testAlertDeduplication() = runBlocking {
        val packageName = "com.test.dedup"
        val now = System.currentTimeMillis()
        
        // Baseline with NO allowed IPs
        val baseline = AppBaselineEntity(
            packageName = packageName,
            allowedDomains = emptyList(),
            allowedIps = emptyList(),
            typicalDailyBytesSent = 1000,
            typicalDailyBytesReceived = 1000,
            typicalActiveHours = List(24) { 1 },
            lastUpdated = now
        )
        db.appBaselineDao().insertOrUpdate(baseline)

        val event = ConnectionEvent(
            connectionId = "conn_alert_1",
            uid = 1000,
            packageName = packageName,
            protocol = NetworkProtocol.TCP,
            srcPort = 1234,
            dstIp = "9.9.9.9",
            dstPort = 443,
            direction = TrafficDirection.OUTBOUND,
            bytesSent = 100,
            bytesReceived = 0
        )

        // Log the same connection twice
        repository.logConnection(event)
        repository.logConnection(event.copy(connectionId = "conn_alert_2")) // Same IP, different conn ID

        val alerts = db.anomalyAlertDao().getAllAlerts().first()
        
        // Should only have 1 alert for the new IP "9.9.9.9"
        assertEquals(1, alerts.size)
        assertEquals("9.9.9.9", alerts[0].target)
        
        // Log a DIFFERENT connection to a DIFFERENT IP
        repository.logConnection(event.copy(connectionId = "conn_alert_3", dstIp = "1.1.1.1"))
        
        val alertsAfter = db.anomalyAlertDao().getAllAlerts().first()
        assertEquals(2, alertsAfter.size)
    }
}
