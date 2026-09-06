package com.example.netshadow.data.model

data class BaselineSummary(
    val packageName: String,
    val allowedCountries: List<String>,
    val allowedDomains: List<String>,
    val typicalDailyBytesSent: Long,
    val typicalDailyBytesReceived: Long,
    val activeHours: List<Int>, // 0-23
    val trustScore: Float, // 0.0 to 1.0
    val summaryText: String
)
