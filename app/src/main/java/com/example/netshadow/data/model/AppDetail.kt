package com.example.netshadow.data.model

import com.example.netshadow.data.entity.AnomalyAlertEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.entity.AppBaselineEntity

data class AppDetail(
    val packageName: String,
    val baseline: AppBaselineEntity?,
    val recentEvents: List<ConnectionEventEntity>,
    val alerts: List<AnomalyAlertEntity>,
    val summaryString: String
)
