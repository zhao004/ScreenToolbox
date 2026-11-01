package com.zhao.screentoolbox.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.core.model.WatermarkConfig
import com.zhao.screentoolbox.core.model.normalizedRectFromPixels
import com.zhao.screentoolbox.overlay.burn.BurnMaskView
import com.zhao.screentoolbox.overlay.common.RegionEditView
import com.zhao.screentoolbox.overlay.common.ResizeCorner
import com.zhao.screentoolbox.overlay.touch.TouchBlockView
import com.zhao.screentoolbox.overlay.watermark.WatermarkView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * 管理本应用创建的所有系统悬浮窗。
 *
 * 所有公开方法必须在主线程调用；服务是唯一所有者，UI 不得持有该类实例。
 */
@Singleton
class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeStore: OverlayRuntimeStore,
) : OverlayController {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val entries = linkedMapOf<String, OverlayEntry>()
    private val knownConfigs = linkedMapOf<String, OverlayConfig>()
    private var editingId: String? = null
    private var watermarkAlphaLimit = MAX_PASS_THROUGH_ALPHA
    private val density = context.resources.displayMetrics.density
    private val minWindowSizePx = (MIN_WINDOW_DP * density).toInt()

    /** 将当前期望配置与窗口集合对齐，缺失的创建、变化的更新、多余的删除。 */
    fun reconcile(
        configs: List<OverlayConfig>,
        editingConfigId: String? = null,
    ) {
        editingId = editingConfigId
        val watermarkCount = configs.count { it is WatermarkConfig }
        watermarkAlphaLimit = if (watermarkCount == 0) {
            MAX_PASS_THROUGH_ALPHA
        } else {
            MAX_PASS_THROUGH_ALPHA / watermarkCount
        }
        val desiredIds = configs.mapTo(linkedSetOf()) { it.id }
        entries.keys.toList().filterNot(desiredIds::contains).forEach(::remove)
        configs.forEach { config ->
            knownConfigs[config.id] = config
            if (entries.containsKey(config.id)) {
                update(config)
            } else {
                add(config)
            }
        }
    }

    override fun show(id: String) {
        val config = knownConfigs[id] ?: return
        if (!entries.containsKey(id)) add(config)
    }

    override fun hide(id: String) {
        remove(id)
    }

    override fun update(id: String) {
        knownConfigs[id]?.let(::update)
    }

    override fun remove(id: String) {
        val entry = entries.remove(id) ?: return
        try {
            windowManager.removeViewImmediate(entry.view)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "移除悬浮窗失败: $id", exception)
        }
    }

    override fun removeAll() {
        entries.keys.toList().forEach(::remove)
        editingId = null
    }

    private fun add(config: OverlayConfig) {
        val screen = screenSize()
        val rect = config.rect
            .sanitized()
            .clamped(screen.width, screen.height, minWindowSizePx, minWindowSizePx)
        val params = createLayoutParams(config, rect, screen.width, screen.height)
        val view = createView(config)
        val entry = OverlayEntry(view, params, config)
        bindView(entry, config)
        try {
            windowManager.addView(view, params)
            entries[config.id] = entry
            runtimeStore.clearError()
        } catch (exception: RuntimeException) {
            runtimeStore.update {
                it.copy(lastError = "无法创建悬浮窗，请检查悬浮窗权限: ${exception.javaClass.simpleName}")
            }
            Log.e(TAG, "创建悬浮窗失败: ${config.id}", exception)
        }
    }

    private fun update(config: OverlayConfig) {
        val entry = entries[config.id] ?: return
        val screen = screenSize()
        val rect = config.rect
            .sanitized()
            .clamped(screen.width, screen.height, minWindowSizePx, minWindowSizePx)
        val params = createLayoutParams(config, rect, screen.width, screen.height)
        entry.params.copyFrom(params)
        entry.config = config
        bindView(entry, config)
        try {
            windowManager.updateViewLayout(entry.view, entry.params)
        } catch (exception: RuntimeException) {
            runtimeStore.update {
                it.copy(lastError = "更新悬浮窗失败，已移除异常窗口")
            }
            Log.e(TAG, "更新悬浮窗失败: ${config.id}", exception)
            remove(config.id)
        }
    }

    private fun createView(config: OverlayConfig): View = when (config.type) {
        com.zhao.screentoolbox.core.model.OverlayType.BURN_MASK -> BurnMaskView(context)
        com.zhao.screentoolbox.core.model.OverlayType.TOUCH_BLOCK -> TouchBlockView(context)
        com.zhao.screentoolbox.core.model.OverlayType.WATERMARK -> WatermarkView(context)
    }

    private fun bindView(entry: OverlayEntry, config: OverlayConfig) {
        when (config) {
            is BurnMaskConfig -> (entry.view as? BurnMaskView)?.bind(config, config.id == editingId)
            is TouchBlockConfig -> (entry.view as? TouchBlockView)?.bind(config, config.id == editingId)
            is WatermarkConfig -> (entry.view as? WatermarkView)?.bind(config, config.id == editingId)
        }
        if (entry.view is RegionEditView) {
            val regionView = entry.view
            regionView.onMove = { dx, dy -> moveWindow(entry, dx, dy) }
            regionView.onResize = { corner, dx, dy -> resizeWindow(entry, corner, dx, dy) }
            regionView.onCommit = { commitGeometry(entry) }
        }
    }

    private fun createLayoutParams(
        config: OverlayConfig,
        rect: NormalizedRect,
        screenWidth: Int,
        screenHeight: Int,
    ): WindowManager.LayoutParams {
        val editable = config.id == editingId
        val touchable = editable || config.type == com.zhao.screentoolbox.core.model.OverlayType.TOUCH_BLOCK
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable || config is WatermarkConfig) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        val params = WindowManager.LayoutParams(
            max(1, (rect.width * screenWidth).toInt()),
            max(1, (rect.height * screenHeight).toInt()),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (rect.x * screenWidth).toInt()
            y = (rect.y * screenHeight).toInt()
            title = "ScreenToolbox:${config.type.name}:${config.id}"
            alpha = when (config) {
                is WatermarkConfig -> config.alpha.coerceIn(0f, watermarkAlphaLimit)
                else -> 1f
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }
        return params
    }

    private fun moveWindow(entry: OverlayEntry, dx: Int, dy: Int) {
        val screen = screenSize()
        entry.params.x = (entry.params.x + dx).coerceIn(0, max(0, screen.width - entry.params.width))
        entry.params.y = (entry.params.y + dy).coerceIn(0, max(0, screen.height - entry.params.height))
        safeUpdate(entry)
    }

    private fun resizeWindow(entry: OverlayEntry, corner: ResizeCorner, dx: Int, dy: Int) {
        val screen = screenSize()
        var left = entry.params.x
        var top = entry.params.y
        var right = left + entry.params.width
        var bottom = top + entry.params.height
        when (corner) {
            ResizeCorner.TOP_LEFT -> {
                left += dx
                top += dy
            }

            ResizeCorner.TOP_RIGHT -> {
                right += dx
                top += dy
            }

            ResizeCorner.BOTTOM_LEFT -> {
                left += dx
                bottom += dy
            }

            ResizeCorner.BOTTOM_RIGHT -> {
                right += dx
                bottom += dy
            }
        }
        left = left.coerceIn(0, max(0, screen.width - minWindowSizePx))
        top = top.coerceIn(0, max(0, screen.height - minWindowSizePx))
        right = right.coerceIn(left + minWindowSizePx, screen.width)
        bottom = bottom.coerceIn(top + minWindowSizePx, screen.height)
        entry.params.x = left
        entry.params.y = top
        entry.params.width = right - left
        entry.params.height = bottom - top
        safeUpdate(entry)
    }

    private fun commitGeometry(entry: OverlayEntry) {
        val screen = screenSize()
        val rect = normalizedRectFromPixels(
            x = entry.params.x,
            y = entry.params.y,
            width = entry.params.width,
            height = entry.params.height,
            screenWidth = screen.width,
            screenHeight = screen.height,
            minWidthPx = minWindowSizePx,
            minHeightPx = minWindowSizePx,
        )
        runtimeStore.reportGeometry(entry.config.id, rect)
    }

    private fun safeUpdate(entry: OverlayEntry) {
        try {
            windowManager.updateViewLayout(entry.view, entry.params)
        } catch (exception: RuntimeException) {
            runtimeStore.update { it.copy(lastError = "调整悬浮窗位置失败") }
            Log.e(TAG, "调整悬浮窗失败: ${entry.config.id}", exception)
        }
    }

    private data class ScreenSize(val width: Int, val height: Int)

    @Suppress("DEPRECATION")
    private fun screenSize(): ScreenSize {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            ScreenSize(bounds.width(), bounds.height())
        } else {
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            ScreenSize(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private companion object {
        const val TAG = "OverlayManager"
        const val MIN_WINDOW_DP = 48f
        const val MAX_PASS_THROUGH_ALPHA = 0.8f
    }
}
