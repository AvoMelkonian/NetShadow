package com.example.netshadow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.netshadow.data.converter.DataConverters
import com.example.netshadow.data.dao.AnomalyAlertDao
import com.example.netshadow.data.dao.AppBaselineDao
import com.example.netshadow.data.dao.ConnectionEventDao
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
    abstract fun connectionEventDao(): ConnectionEventDao
    abstract fun appBaselineDao(): AppBaselineDao
    abstract fun anomalyAlertDao(): AnomalyAlertDao

    companion object {
        @Volatile
        private var INSTANCE: NetShadowDatabase? = null

        fun getDatabase(context: Context): NetShadowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetShadowDatabase::class.java,
                    "netshadow_database"
                )
                .fallbackToDestructiveMigration() // For development, consider proper migrations later
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
