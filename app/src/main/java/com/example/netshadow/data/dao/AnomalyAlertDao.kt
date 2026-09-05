package com.example.netshadow.data.dao

import androidx.room.*
import com.example.netshadow.data.entity.AnomalyAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnomalyAlertDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(alert: AnomalyAlertEntity)

    @Query("SELECT * FROM anomaly_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AnomalyAlertEntity>>

    @Query("SELECT COUNT(*) FROM anomaly_alerts WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE anomaly_alerts SET isRead = 1 WHERE id = :alertId")
    suspend fun markAsRead(alertId: Long)

    @Query("UPDATE anomaly_alerts SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("SELECT * FROM anomaly_alerts WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getAlertsForApp(packageName: String): Flow<List<AnomalyAlertEntity>>

    @Query("DELETE FROM anomaly_alerts WHERE timestamp < :threshold")
    suspend fun deleteOldAlerts(threshold: Long)
}
