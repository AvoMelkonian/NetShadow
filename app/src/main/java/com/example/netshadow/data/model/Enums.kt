package com.example.netshadow.data.model

sealed class Protocol {
    object TCP : Protocol()
    object UDP : Protocol()
    data class Unknown(val protocolNumber: Int) : Protocol()
}

sealed class Direction {
    object Inbound : Direction()
    object Outbound : Direction()
}

enum class AlertType {
    UNUSUAL_PORT,
    UNAUTHORIZED_DOMAIN,
    HIGH_TRAFFIC_VOLUME,
    MALICIOUS_IP,
    GEOGRAPHIC_ANOMALY
}

enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
