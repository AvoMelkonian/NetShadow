package com.example.netshadow.data.dao

import androidx.room.*
import com.example.netshadow.data.entity.ConnectionEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionEventDao {
    @Upsert
    suspend fun upsert(event: ConnectionEventEntity)

    @Upsert
    suspend fun upsertAll(events: List<ConnectionEventEntity>)

    @Query("SELECT * FROM connection_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<ConnectionEventEntity>>

    @Query("SELECT * FROM connection_events WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getEventsByApp(packageName: String): Flow<List<ConnectionEventEntity>>

    @Query("SELECT * FROM connection_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getEventsInTimeRange(startTime: Long, endTime: Long): Flow<List<ConnectionEventEntity>>

    @Query("SELECT DISTINCT packageName FROM connection_events")
    suspend fun getAllPackageNames(): List<String>

    @Query("SELECT packageName, SUM(bytesSent) as totalSent, SUM(bytesReceived) as totalReceived FROM connection_events GROUP BY packageName")
    fun getAppTrafficAggregation(): Flow<List<AppTrafficStats>>

    @Query("SELECT DISTINCT remoteAddress FROM connection_events WHERE packageName = :packageName AND timestamp >= :since")
    fun getKnownDestinations(packageName: String, since: Long): Flow<List<String>>

    @Query("SELECT (timestamp / 3600000) * 3600000 as hourTimestamp, SUM(bytesSent) as totalBytesSent FROM connection_events WHERE packageName = :packageName AND timestamp >= :since GROUP BY hourTimestamp")
    fun getHourlyTrafficStats(packageName: String, since: Long): Flow<List<HourlyTraffic>>

    @Query("SELECT * FROM connection_events ORDER BY timestamp DESC")
    suspend fun getAllEventsList(): List<ConnectionEventEntity>

    @Query("DELETE FROM connection_events WHERE timestamp < :threshold")
    suspend fun deleteOldEvents(threshold: Long)

    @Query("""
        SELECT 
            ce.packageName,
            ce.packageName as appName,
            COUNT(DISTINCT ce.connectionId) as liveConnectionCount,
            SUM(ce.bytesSent) as totalBytesSent,
            SUM(ce.bytesReceived) as totalBytesReceived,
            (SELECT COUNT(*) FROM anomaly_alerts aa WHERE aa.packageName = ce.packageName) as alertCount
        FROM connection_events ce
        GROUP BY ce.packageName
    """)
    fun getAppSummaries(): Flow<List<com.example.netshadow.data.model.AppSummary>>
}

data class AppTrafficStats(
    val packageName: String,
    val totalSent: Long,
    val totalReceived: Long
)

data class HourlyTraffic(
    val hourTimestamp: Long,
    val totalBytesSent: Long
)
