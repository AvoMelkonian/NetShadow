package com.example.netshadow.data.dao

import androidx.room.*
import com.example.netshadow.data.entity.AppBaselineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppBaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(baseline: AppBaselineEntity)

    @Query("SELECT * FROM app_baselines WHERE packageName = :packageName")
    suspend fun getBaselineForApp(packageName: String): AppBaselineEntity?

    @Query("SELECT * FROM app_baselines")
    fun getAllBaselines(): Flow<List<AppBaselineEntity>>

    @Delete
    suspend fun deleteBaseline(baseline: AppBaselineEntity)
}
