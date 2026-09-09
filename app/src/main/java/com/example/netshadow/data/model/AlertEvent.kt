package com.example.netshadow.data.model

data class AlertEvent(
    val id: Long,
    val timestamp: Long,
    val type: AlertType,
    val severity: Severity,
    val message: String,
    val packageName: String,
    val isRead: Boolean,
    val affectedTarget: String? = null
)
