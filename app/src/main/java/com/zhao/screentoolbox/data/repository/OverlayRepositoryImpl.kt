package com.zhao.screentoolbox.data.repository

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.room.withTransaction
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.data.database.AppDatabase
import com.zhao.screentoolbox.domain.repository.OverlayRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class OverlayRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) : OverlayRepository {
    private val dao = database.overlayDao()

    override val overlays: Flow<List<OverlayConfig>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** 在一个事务内同步主配置和按类型拆分的明细，避免半写入配置被服务加载。 */
    override suspend fun upsert(config: OverlayConfig) {
        val display = displayInfo()
        database.withTransaction {
            val existing = dao.getById(config.id)
            val parts = config.toEntityParts(
                sortOrder = existing?.config?.sortOrder ?: (System.currentTimeMillis() / 1000L).toInt(),
                existingCreatedAt = existing?.config?.createdAt,
                referenceWidthPx = existing?.region?.referenceWidthPx ?: display.widthPx,
                referenceHeightPx = existing?.region?.referenceHeightPx ?: display.heightPx,
                referenceOrientation = existing?.region?.referenceOrientation ?: display.orientation,
            )
            dao.upsertConfig(parts.config)
            dao.upsertRegion(parts.region)
            if (parts.burnStyle != null) dao.upsertBurnStyle(parts.burnStyle)
            if (parts.touchStyle != null) dao.upsertTouchStyle(parts.touchStyle)
            if (parts.watermarkStyle != null) dao.upsertWatermarkStyle(parts.watermarkStyle)
        }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.updateEnabled(id, enabled, System.currentTimeMillis())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    private data class DisplayInfo(
        val widthPx: Int,
        val heightPx: Int,
        val orientation: Int,
    )

    @Suppress("DEPRECATION")
    private fun displayInfo(): DisplayInfo {
        val windowManager = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            DisplayInfo(
                widthPx = bounds.width(),
                heightPx = bounds.height(),
                orientation = if (bounds.width() > bounds.height()) {
                    Configuration.ORIENTATION_LANDSCAPE
                } else {
                    Configuration.ORIENTATION_PORTRAIT
                },
            )
        } else {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            DisplayInfo(
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                orientation = if (metrics.widthPixels > metrics.heightPixels) {
                    Configuration.ORIENTATION_LANDSCAPE
                } else {
                    Configuration.ORIENTATION_PORTRAIT
                },
            )
        }
    }
}
