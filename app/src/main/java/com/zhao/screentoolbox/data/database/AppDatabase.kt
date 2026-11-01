package com.zhao.screentoolbox.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        OverlayConfigEntity::class,
        OverlayRegionEntity::class,
        BurnMaskStyleEntity::class,
        TouchBlockStyleEntity::class,
        WatermarkStyleEntity::class,
        AppRuleEntity::class,
        AppRuleConfigCrossRef::class,
        OperationLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun overlayDao(): OverlayDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun operationLogDao(): OperationLogDao

    companion object {
        /** 保留旧配置视觉，同时为防误触样式增加独立填充透明度。 */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE touch_block_configs ADD COLUMN alpha REAL NOT NULL DEFAULT 0",
                )
                db.execSQL("UPDATE burn_mask_configs SET alpha = 1.0")
            }
        }
    }
}
