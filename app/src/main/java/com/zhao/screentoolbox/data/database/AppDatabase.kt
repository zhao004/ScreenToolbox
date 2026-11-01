package com.zhao.screentoolbox.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun overlayDao(): OverlayDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun operationLogDao(): OperationLogDao
}
