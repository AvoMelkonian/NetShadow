package com.example.netshadow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netshadow.capture.model.ConnectionEvent
import com.example.netshadow.capture.model.NetworkProtocol
import com.example.netshadow.capture.model.TrafficDirection
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrafficIntegrationTest {
    private lateinit var db: NetShadowDatabase
    private lateinit var repository: TrafficRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NetShadowDatabase::class.java).build()
        repository = TrafficRepository(db.connectionEventDao())
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
}
