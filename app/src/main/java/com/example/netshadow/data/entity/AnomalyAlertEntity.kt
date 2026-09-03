package com.example.netshadow.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.netshadow.data.model.AlertType
import com.example.netshadow.data.model.Severity

@Entity(tableName = "anomaly_alerts")
data class AnomalyAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: AlertType,
    val severity: Severity,
    val message: String,
    val packageName: String,
    val connectionId: String?,
    val isRead: Boolean = false
)
