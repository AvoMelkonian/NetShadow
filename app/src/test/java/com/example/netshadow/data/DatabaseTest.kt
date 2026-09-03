package com.example.netshadow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netshadow.data.dao.AnomalyAlertDao
import com.example.netshadow.data.dao.AppBaselineDao
import com.example.netshadow.data.dao.ConnectionEventDao
import com.example.netshadow.data.entity.AnomalyAlertEntity
import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.model.AlertType
import com.example.netshadow.data.model.Direction
import com.example.netshadow.data.model.Protocol
import com.example.netshadow.data.model.Severity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: NetShadowDatabase
    private lateinit var eventDao: ConnectionEventDao
    private lateinit var baselineDao: AppBaselineDao
    private lateinit var alertDao: AnomalyAlertDao

    @Test
    fun testDatabaseVersion() {
        // Simple stub to verify the database is at version 1
        assertEquals(1, db.openHelper.readableDatabase.version)
    }

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NetShadowDatabase::class.java).build()
        eventDao = db.connectionEventDao()
        baselineDao = db.appBaselineDao()
        alertDao = db.anomalyAlertDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadConnectionEvent() = runBlocking {
        val event = ConnectionEventEntity(
            connectionId = "conn_1",
            protocol = Protocol.TCP,
            direction = Direction.Outbound,
            localAddress = "192.168.1.10",
            localPort = 12345,
            remoteAddress = "8.8.8.8",
            remotePort = 443,
            packageName = "com.test.app",
            uid = 10001,
            timestamp = System.currentTimeMillis(),
            bytesSent = 100,
            bytesReceived = 200
        )
        eventDao.insert(event)
        val events = eventDao.getAllEvents().first()
        assertEquals(events[0].connectionId, "conn_1")
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadAppBaseline() = runBlocking {
        val baseline = AppBaselineEntity(
            packageName = "com.test.app",
            allowedDomains = listOf("google.com", "github.com"),
            allowedIps = listOf("8.8.8.8"),
            typicalDailyBytesSent = 1000,
            typicalDailyBytesReceived = 2000,
            lastUpdated = System.currentTimeMillis()
        )
        baselineDao.insertOrUpdate(baseline)
        val result = baselineDao.getBaselineForApp("com.test.app")
        assertEquals(result?.packageName, "com.test.app")
        assertEquals(result?.allowedDomains?.size, 2)
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadAnomalyAlert() = runBlocking {
        val alert = AnomalyAlertEntity(
            timestamp = System.currentTimeMillis(),
            type = AlertType.MALICIOUS_IP,
            severity = Severity.HIGH,
            message = "Connection to malicious IP detected",
            packageName = "com.test.app",
            connectionId = "conn_1"
        )
        alertDao.insert(alert)
        val alerts = alertDao.getAllAlerts().first()
        assertEquals(alerts[0].type, AlertType.MALICIOUS_IP)
    }
}
