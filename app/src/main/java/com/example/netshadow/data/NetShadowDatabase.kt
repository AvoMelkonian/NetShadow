package com.example.netshadow.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.netshadow.data.converter.DataConverters
import com.example.netshadow.data.entity.AnomalyAlertEntity
import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity

@Database(
    entities = [
        ConnectionEventEntity::class,
        AppBaselineEntity::class,
        AnomalyAlertEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DataConverters::class)
abstract class NetShadowDatabase : RoomDatabase() {
    // DAOs will be added in the next part
}
