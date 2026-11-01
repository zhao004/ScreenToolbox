package com.zhao.screentoolbox.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OverlayDao {
    @Transaction
    @Query("SELECT * FROM overlay_configs ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeAll(): Flow<List<OverlayConfigWithDetails>>

    @Transaction
    @Query("SELECT * FROM overlay_configs WHERE id = :id")
    suspend fun getById(id: String): OverlayConfigWithDetails?

    @Upsert
    suspend fun upsertConfig(config: OverlayConfigEntity)

    @Upsert
    suspend fun upsertRegion(region: OverlayRegionEntity)

    @Upsert
    suspend fun upsertBurnStyle(style: BurnMaskStyleEntity)

    @Upsert
    suspend fun upsertTouchStyle(style: TouchBlockStyleEntity)

    @Upsert
    suspend fun upsertWatermarkStyle(style: WatermarkStyleEntity)

    @Query("DELETE FROM overlay_configs WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AppRuleDao {
    @Transaction
    @Query("SELECT * FROM app_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AppRuleWithConfigIds>>

    @Transaction
    @Query("SELECT * FROM app_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): AppRuleWithConfigIds?

    @Upsert
    suspend fun upsertRule(rule: AppRuleEntity): Long

    @Query("DELETE FROM app_rule_configs WHERE ruleId = :ruleId")
    suspend fun deleteCrossRefs(ruleId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<AppRuleConfigCrossRef>)

    @Query("DELETE FROM app_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface OperationLogDao {
    @Insert
    suspend fun insert(log: OperationLogEntity)

    @Query("DELETE FROM operation_logs WHERE id NOT IN (SELECT id FROM operation_logs ORDER BY timestamp DESC LIMIT 500)")
    suspend fun trim()
}
