package com.example.netshadow.data.converter

import androidx.room.TypeConverter
import com.example.netshadow.data.model.*

class DataConverters {
    @TypeConverter
    fun fromProtocol(protocol: Protocol): Int {
        return when (protocol) {
            is Protocol.TCP -> 6
            is Protocol.UDP -> 17
            is Protocol.Unknown -> protocol.protocolNumber
        }
    }

    @TypeConverter
    fun toProtocol(protocolNumber: Int): Protocol {
        return when (protocolNumber) {
            6 -> Protocol.TCP
            17 -> Protocol.UDP
            else -> Protocol.Unknown(protocolNumber)
        }
    }

    @TypeConverter
    fun fromDirection(direction: Direction): String {
        return when (direction) {
            Direction.Inbound -> "INBOUND"
            Direction.Outbound -> "OUTBOUND"
        }
    }

    @TypeConverter
    fun toDirection(direction: String): Direction {
        return when (direction) {
            "INBOUND" -> Direction.Inbound
            "OUTBOUND" -> Direction.Outbound
            else -> Direction.Outbound
        }
    }

    @TypeConverter
    fun fromAlertType(type: AlertType): String = type.name

    @TypeConverter
    fun toAlertType(value: String): AlertType = AlertType.valueOf(value)

    @TypeConverter
    fun fromSeverity(severity: Severity): String = severity.name

    @TypeConverter
    fun toSeverity(value: String): Severity = Severity.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(",")

    @TypeConverter
    fun fromIntList(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun toIntList(value: String): List<Int> = if (value.isEmpty()) emptyList() else value.split(",").map { it.toInt() }
}
