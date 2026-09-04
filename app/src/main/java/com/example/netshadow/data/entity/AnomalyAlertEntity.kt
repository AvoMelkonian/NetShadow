package com.example.netshadow.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.netshadow.data.model.AlertType
import com.example.netshadow.data.model.Severity

@Entity(
    tableName = "anomaly_alerts",
    indices = [
        androidx.room.Index(
            value = ["type", "packageName", "target"],
            unique = true
        )
    ]
)
data class AnomalyAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: AlertType,
    val severity: Severity,
    val message: String,
    val packageName: String,
    val connectionId: String?,
    val target: String? = null, // e.g., IP address or domain
    val isRead: Boolean = false
)
