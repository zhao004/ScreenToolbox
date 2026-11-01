package com.zhao.screentoolbox.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.core.model.WatermarkMode

@Entity(tableName = "overlay_configs")
data class OverlayConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: OverlayType,
    val enabled: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "overlay_regions",
    foreignKeys = [
        ForeignKey(
            entity = OverlayConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class OverlayRegionEntity(
    @PrimaryKey val configId: String,
    val xRatio: Float,
    val yRatio: Float,
    val widthRatio: Float,
    val heightRatio: Float,
    val referenceWidthPx: Int,
    val referenceHeightPx: Int,
    val referenceOrientation: Int,
)

@Entity(
    tableName = "burn_mask_configs",
    foreignKeys = [
        ForeignKey(
            entity = OverlayConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
/**
 * 防烧屏样式。
 *
 * `alpha` 是 v1 数据库的兼容字段：当前遮罩固定完全不透明，读写时统一归一化为 1f。
 */
data class BurnMaskStyleEntity(
    @PrimaryKey val configId: String,
    val colorArgb: Long,
    val alpha: Float,
    val cornerRadiusDp: Float,
)

@Entity(
    tableName = "touch_block_configs",
    foreignKeys = [
        ForeignKey(
            entity = OverlayConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TouchBlockStyleEntity(
    @PrimaryKey val configId: String,
    val boundaryVisible: Boolean,
)

@Entity(
    tableName = "watermark_configs",
    foreignKeys = [
        ForeignKey(
            entity = OverlayConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WatermarkStyleEntity(
    @PrimaryKey val configId: String,
    val text: String,
    val textSizeSp: Float,
    val fontWeight: Int,
    val colorArgb: Long,
    val alpha: Float,
    val rotationDegrees: Float,
    val mode: WatermarkMode,
    val tileSpacingDp: Float,
    val positionXRatio: Float,
    val positionYRatio: Float,
)

@Entity(
    tableName = "app_rules",
    indices = [Index(value = ["packageName"], unique = true)],
)
data class AppRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val displayName: String,
    val enabled: Boolean,
    val burnMaskEnabled: Boolean,
    val touchBlockEnabled: Boolean,
    val watermarkEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "app_rule_configs",
    primaryKeys = ["ruleId", "configId"],
    foreignKeys = [
        ForeignKey(
            entity = AppRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OverlayConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("configId")],
)
data class AppRuleConfigCrossRef(
    val ruleId: Long,
    val configId: String,
)

@Entity(tableName = "operation_logs")
data class OperationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val detail: String?,
    val timestamp: Long,
)

data class OverlayConfigWithDetails(
    @Embedded val config: OverlayConfigEntity,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val region: OverlayRegionEntity?,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val burnStyle: BurnMaskStyleEntity?,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val touchStyle: TouchBlockStyleEntity?,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val watermarkStyle: WatermarkStyleEntity?,
)

data class AppRuleWithConfigIds(
    @Embedded val rule: AppRuleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = AppRuleConfigCrossRef::class,
            parentColumn = "ruleId",
            entityColumn = "configId",
        ),
    )
    val configs: List<OverlayConfigEntity>,
)
