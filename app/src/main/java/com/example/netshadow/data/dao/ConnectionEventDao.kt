package com.example.netshadow.data.dao

import androidx.room.*
import com.example.netshadow.data.entity.ConnectionEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ConnectionEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<ConnectionEventEntity>)

    @Query("SELECT * FROM connection_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<ConnectionEventEntity>>

    @Query("SELECT * FROM connection_events WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getEventsByApp(packageName: String): Flow<List<ConnectionEventEntity>>

    @Query("SELECT * FROM connection_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getEventsInTimeRange(startTime: Long, endTime: Long): Flow<List<ConnectionEventEntity>>

    @Query("SELECT packageName, SUM(bytesSent) as totalSent, SUM(bytesReceived) as totalReceived FROM connection_events GROUP BY packageName")
    fun getAppTrafficAggregation(): Flow<List<AppTrafficStats>>

    @Query("DELETE FROM connection_events WHERE timestamp < :threshold")
    suspend fun deleteOldEvents(threshold: Long)
}

data class AppTrafficStats(
    val packageName: String,
    val totalSent: Long,
    val totalReceived: Long
)
