package com.zhao.screentoolbox.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.core.model.WatermarkConfig
import com.zhao.screentoolbox.core.model.normalizedRectFromPixels
import com.zhao.screentoolbox.core.model.withRect
import com.zhao.screentoolbox.overlay.burn.BurnMaskView
import com.zhao.screentoolbox.overlay.common.RegionEditView
import com.zhao.screentoolbox.overlay.editor.OverlayEditorPanelView
import com.zhao.screentoolbox.overlay.editor.OverlayPanelGeometry
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
    private val applicationHost = OverlayWindowHost(
        context = context,
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    )
    private var accessibilityHost: OverlayWindowHost? = null
    private val entries = linkedMapOf<String, OverlayEntry>()
    private val knownConfigs = linkedMapOf<String, OverlayConfig>()
    private var editingId: String? = null
    private var watermarkAlphaLimit = MAX_PASS_THROUGH_ALPHA
    private var editorPanelView: OverlayEditorPanelView? = null
    private var editorPanelParams: WindowManager.LayoutParams? = null
    private var editorPanelHost: OverlayWindowHost? = null
    private var editorPanelConfig: OverlayConfig? = null
    private var editorConfigChanged: ((OverlayConfig) -> Unit)? = null
    private var editorPanelMinimized = false
    private var editorPanelSnapAnimator: ValueAnimator? = null
    private var regionGestureSnapshot: RegionGestureSnapshot? = null
    private var cachedScreenSize: ScreenSize? = null
    private val density = context.resources.displayMetrics.density
    private val minRegionSizePx = (MIN_REGION_SIZE_DP * density).toInt().coerceAtLeast(1)

    /** 绑定无障碍服务提供的窗口宿主，防烧屏遮罩随后可在服务重算时重建。 */
    fun attachAccessibilityHost(accessibilityContext: Context) {
        if (accessibilityHost?.context === accessibilityContext) return
        detachAccessibilityHost()
        accessibilityHost = OverlayWindowHost(
            context = accessibilityContext,
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        )
        Log.d(TAG, "无障碍窗口宿主已绑定")
    }

    /** 无障碍服务断开时移除其承载的窗口，普通悬浮窗不受影响。 */
    fun detachAccessibilityHost() {
        val host = accessibilityHost ?: return
        if (editorPanelHost === host) {
            removeEditorPanel()
        }
        entries.values
            .filter { it.host === host }
            .map { it.config.id }
            .forEach(::remove)
        accessibilityHost = null
        Log.d(TAG, "无障碍窗口宿主已解绑")
    }

    /** 将当前期望配置与窗口集合对齐，缺失的创建、变化的更新、多余的删除。 */
    fun reconcile(
        configs: List<OverlayConfig>,
        editingConfigId: String? = null,
    ) {
        val screen = screenSize(forceRefresh = true)
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
        editorPanelView?.setScreenBounds(
            screenWidth = screen.width,
            screenHeight = screen.height,
            minWidthPx = minRegionSizePx,
            minHeightPx = minRegionSizePx,
        )
        clampEditorPanel()
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
        if (regionGestureSnapshot?.configId == id) {
            regionGestureSnapshot = null
        }
        val entry = entries.remove(id) ?: return
        try {
            entry.host.windowManager.removeViewImmediate(entry.view)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "移除悬浮窗失败: $id", exception)
        }
    }

    override fun removeAll() {
        entries.keys.toList().forEach(::remove)
        removeEditorPanel()
        editingId = null
    }

    /** 显示可拖动、可最小化的参数面板，生命周期和所有系统窗口保持一致。 */
    fun showEditorPanel(
        config: OverlayConfig,
        onConfigChanged: (OverlayConfig) -> Unit,
        onSave: () -> Unit,
        onCancel: () -> Unit,
    ) {
        require(config is BurnMaskConfig || config is TouchBlockConfig) {
            "悬浮编辑面板只支持防烧屏和防误触"
        }
        val host = windowHostFor(config)
        if (host == null) {
            runtimeStore.update {
                it.copy(lastError = "请先启用无障碍服务后再编辑防烧屏遮罩")
            }
            return
        }
        removeEditorPanel()
        editorPanelConfig = config
        editorConfigChanged = onConfigChanged
        editorPanelMinimized = false
        val screen = screenSize()
        val panelSize = expandedPanelSize(screen, config)
        val panel = OverlayEditorPanelView(host.context).apply {
            bind(
                config = config,
                screenWidth = screen.width,
                screenHeight = screen.height,
                minWidthPx = minRegionSizePx,
                minHeightPx = minRegionSizePx,
                onConfigChanged = { updated ->
                    editorPanelConfig = updated
                    editorConfigChanged?.invoke(updated)
                },
                onSave = onSave,
                onCancel = onCancel,
            )
            onDrag = { dx, dy -> moveEditorPanel(dx, dy) }
            onDragStart = ::cancelEditorPanelAnimation
            onDragEnd = {
                if (editorPanelMinimized) {
                    animateEditorPanelToEdge()
                }
            }
            onMinimize = ::minimizeEditorPanel
            onRestore = ::expandEditorPanel
        }
        val params = WindowManager.LayoutParams(
            panelSize.width,
            panelSize.height,
            host.type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = max(0, (screen.width - panelSize.width) / 2)
            y = max(dp(PANEL_MARGIN_DP), screen.height - panelSize.height - dp(PANEL_MARGIN_DP))
            title = "ScreenToolbox:EDITOR_PANEL"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }
        try {
            host.windowManager.addView(panel, params)
            editorPanelView = panel
            editorPanelParams = params
            editorPanelHost = host
            panel.post { clampEditorPanel() }
            Log.d(TAG, "参数面板已创建: ${config.id}")
        } catch (exception: RuntimeException) {
            runtimeStore.update {
                it.copy(lastError = "无法创建参数面板: ${exception.javaClass.simpleName}")
            }
            Log.e(TAG, "创建参数面板失败", exception)
            removeEditorPanel()
        }
    }

    fun removeEditorPanel() {
        if (editorPanelView != null) {
            Log.d(TAG, "参数面板已移除: ${editorPanelConfig?.id}")
        }
        cancelEditorPanelAnimation()
        editorPanelView?.let { view ->
            try {
                editorPanelHost?.windowManager?.removeViewImmediate(view)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "移除参数面板失败", exception)
            }
        }
        editorPanelView = null
        editorPanelParams = null
        editorPanelHost = null
        editorPanelConfig = null
        editorConfigChanged = null
        editorPanelMinimized = false
    }

    fun isEditorPanelVisible(): Boolean = editorPanelView != null

    private fun add(config: OverlayConfig) {
        val host = windowHostFor(config)
        if (host == null) {
            runtimeStore.update {
                it.copy(lastError = "请启用无障碍服务后显示防烧屏遮罩")
            }
            return
        }
        val screen = screenSize()
        val rect = config.rect
            .sanitized()
            .clamped(screen.width, screen.height, minRegionSizePx, minRegionSizePx)
        val params = createLayoutParams(config, rect, screen.width, screen.height, host.type)
        val view = createView(config, host.context)
        val entry = OverlayEntry(view, params, config, host)
        bindView(entry, config)
        try {
            host.windowManager.addView(view, params)
            entries[config.id] = entry
            runtimeStore.clearError()
        } catch (exception: RuntimeException) {
            runtimeStore.update {
                it.copy(lastError = createWindowErrorMessage(config, exception))
            }
            Log.e(TAG, "创建悬浮窗失败: ${config.id}", exception)
        }
    }

    private fun update(config: OverlayConfig) {
        val entry = entries[config.id] ?: return
        val host = windowHostFor(config)
        if (host == null) {
            remove(config.id)
            runtimeStore.update {
                it.copy(lastError = "请启用无障碍服务后显示防烧屏遮罩")
            }
            return
        }
        if (entry.host !== host) {
            remove(config.id)
            add(config)
            return
        }
        val screen = screenSize()
        val rect = config.rect
            .sanitized()
            .clamped(screen.width, screen.height, minRegionSizePx, minRegionSizePx)
        val params = createLayoutParams(config, rect, screen.width, screen.height, host.type)
        entry.params.copyFrom(params)
        entry.config = config
        bindView(entry, config)
        try {
            host.windowManager.updateViewLayout(entry.view, entry.params)
        } catch (exception: RuntimeException) {
            runtimeStore.update {
                it.copy(lastError = "更新悬浮窗失败，已移除异常窗口")
            }
            Log.e(TAG, "更新悬浮窗失败: ${config.id}", exception)
            remove(config.id)
        }
    }

    private fun createView(config: OverlayConfig, viewContext: Context): View = when (config.type) {
        com.zhao.screentoolbox.core.model.OverlayType.BURN_MASK -> BurnMaskView(viewContext)
        com.zhao.screentoolbox.core.model.OverlayType.TOUCH_BLOCK -> TouchBlockView(viewContext)
        com.zhao.screentoolbox.core.model.OverlayType.WATERMARK -> WatermarkView(viewContext)
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
            regionView.onGestureStart = { beginGeometryGesture(entry) }
            regionView.onCommit = { commitGeometry(entry) }
            regionView.onCancel = { cancelGeometryGesture(entry) }
        }
    }

    private fun createLayoutParams(
        config: OverlayConfig,
        rect: NormalizedRect,
        screenWidth: Int,
        screenHeight: Int,
        windowType: Int,
    ): WindowManager.LayoutParams {
        val editable = config.id == editingId
        val flags = OverlayWindowPolicy.flags(config, editable)
        val params = WindowManager.LayoutParams(
            max(1, (rect.width * screenWidth).toInt()),
            max(1, (rect.height * screenHeight).toInt()),
            windowType,
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

    private fun commitGeometry(entry: OverlayEntry) {
        regionGestureSnapshot = null
        val screen = screenSize()
        val rect = normalizedRectFromPixels(
            x = entry.params.x,
            y = entry.params.y,
            width = entry.params.width,
            height = entry.params.height,
            screenWidth = screen.width,
            screenHeight = screen.height,
            minWidthPx = minRegionSizePx,
            minHeightPx = minRegionSizePx,
        )
        val updated = entry.config.withRect(rect)
        entry.config = updated
        if (editorPanelConfig?.id == updated.id) {
            editorPanelConfig = updated
            editorPanelView?.updateGeometry(rect)
            editorConfigChanged?.invoke(updated)
        }
    }

    private fun beginGeometryGesture(entry: OverlayEntry) {
        if (entry.config.id != editingId) return
        regionGestureSnapshot = RegionGestureSnapshot(
            configId = entry.config.id,
            x = entry.params.x,
            y = entry.params.y,
            width = entry.params.width,
            height = entry.params.height,
        )
    }

    private fun cancelGeometryGesture(entry: OverlayEntry) {
        val snapshot = regionGestureSnapshot
        regionGestureSnapshot = null
        if (snapshot?.configId != entry.config.id) return
        entry.params.x = snapshot.x
        entry.params.y = snapshot.y
        entry.params.width = snapshot.width
        entry.params.height = snapshot.height
        safeUpdate(entry)
    }

    private fun moveEditorPanel(dx: Int, dy: Int) {
        val view = editorPanelView ?: return
        val params = editorPanelParams ?: return
        val screen = screenSize()
        params.x = OverlayPanelGeometry.clampedOffset(
            value = params.x + dx,
            size = params.width,
            screenSize = screen.width,
        )
        params.y = OverlayPanelGeometry.clampedOffset(
            value = params.y + dy,
            size = params.height,
            screenSize = screen.height,
        )
        safeUpdateEditorPanel(view, "拖动参数面板失败")
    }

    private fun minimizeEditorPanel() {
        val view = editorPanelView ?: return
        val params = editorPanelParams ?: return
        cancelEditorPanelAnimation()
        val screen = screenSize()
        val iconSize = dp(MINIMIZED_PANEL_DP)
        val centerX = params.x + params.width / 2
        val centerY = params.y + params.height / 2
        editorPanelMinimized = true
        view.showMinimized()
        params.width = iconSize
        params.height = iconSize
        params.x = OverlayPanelGeometry.clampedOffset(
            value = centerX - iconSize / 2,
            size = iconSize,
            screenSize = screen.width,
        )
        params.y = OverlayPanelGeometry.clampedOffset(
            value = centerY - iconSize / 2,
            size = iconSize,
            screenSize = screen.height,
        )
        safeUpdateEditorPanel(view, "缩小参数面板失败")
        animateEditorPanelToEdge()
    }

    private fun expandEditorPanel() {
        if (!editorPanelMinimized) return
        val view = editorPanelView ?: return
        val params = editorPanelParams ?: return
        cancelEditorPanelAnimation()
        val screen = screenSize()
        val panelSize = expandedPanelSize(screen, editorPanelConfig)
        val side = OverlayPanelGeometry.nearestSide(
            x = params.x,
            width = params.width,
            screenWidth = screen.width,
        )
        val iconCenterY = params.y + params.height / 2
        editorPanelMinimized = false
        view.showExpanded()
        params.width = panelSize.width
        params.height = panelSize.height
        params.x = OverlayPanelGeometry.expandedX(
            side = side,
            panelWidth = panelSize.width,
            screenWidth = screen.width,
            marginPx = dp(PANEL_MARGIN_DP),
        )
        params.y = OverlayPanelGeometry.expandedY(
            iconCenterY = iconCenterY,
            panelHeight = panelSize.height,
            screenHeight = screen.height,
            marginPx = dp(PANEL_MARGIN_DP),
        )
        safeUpdateEditorPanel(view, "展开参数面板失败")
    }

    private fun animateEditorPanelToEdge() {
        val view = editorPanelView ?: return
        val params = editorPanelParams ?: return
        if (!editorPanelMinimized) return
        cancelEditorPanelAnimation()
        val screen = screenSize()
        val side = OverlayPanelGeometry.nearestSide(
            x = params.x,
            width = params.width,
            screenWidth = screen.width,
        )
        val targetX = OverlayPanelGeometry.snappedX(
            side = side,
            width = params.width,
            screenWidth = screen.width,
        )
        if (params.x == targetX) return
        val animation = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = PANEL_SNAP_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                safeUpdateEditorPanel(view, "吸附参数面板失败")
            }
            addListener(
                object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (editorPanelSnapAnimator === this@apply) {
                            editorPanelSnapAnimator = null
                        }
                    }
                },
            )
        }
        editorPanelSnapAnimator = animation
        animation.start()
    }

    private fun cancelEditorPanelAnimation() {
        editorPanelSnapAnimator?.cancel()
        editorPanelSnapAnimator = null
    }

    private fun clampEditorPanel() {
        val view = editorPanelView ?: return
        val params = editorPanelParams ?: return
        val screen = screenSize()
        if (editorPanelMinimized) {
            params.width = dp(MINIMIZED_PANEL_DP)
            params.height = dp(MINIMIZED_PANEL_DP)
            params.y = OverlayPanelGeometry.clampedOffset(
                value = params.y,
                size = params.height,
                screenSize = screen.height,
            )
            if (editorPanelSnapAnimator?.isRunning != true) {
                val side = OverlayPanelGeometry.nearestSide(
                    x = params.x,
                    width = params.width,
                    screenWidth = screen.width,
                )
                params.x = OverlayPanelGeometry.snappedX(
                    side = side,
                    width = params.width,
                    screenWidth = screen.width,
                )
            }
        } else {
            val panelSize = expandedPanelSize(screen, editorPanelConfig)
            params.width = panelSize.width
            params.height = panelSize.height
            params.x = OverlayPanelGeometry.clampedOffset(
                value = params.x,
                size = params.width,
                screenSize = screen.width,
            ).coerceAtLeast(dp(PANEL_MARGIN_DP)).coerceAtMost(
                max(
                    dp(PANEL_MARGIN_DP),
                    screen.width - params.width - dp(PANEL_MARGIN_DP),
                ),
            )
            params.y = OverlayPanelGeometry.expandedY(
                iconCenterY = params.y + params.height / 2,
                panelHeight = params.height,
                screenHeight = screen.height,
                marginPx = dp(PANEL_MARGIN_DP),
            )
        }
        try {
            editorPanelHost?.windowManager?.updateViewLayout(view, params)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "校正参数面板位置失败", exception)
        }
    }

    private fun safeUpdateEditorPanel(view: View, errorMessage: String) {
        val params = editorPanelParams ?: return
        try {
            editorPanelHost?.windowManager?.updateViewLayout(view, params)
        } catch (exception: RuntimeException) {
            runtimeStore.update { it.copy(lastError = errorMessage) }
            Log.w(TAG, errorMessage, exception)
        }
    }

    private fun expandedPanelSize(screen: ScreenSize, config: OverlayConfig?): PanelSize {
        val width = min(max(1, screen.width - dp(24)), dp(360)).coerceAtLeast(1)
        val preferredHeightDp = when (config) {
            is BurnMaskConfig -> 430
            is TouchBlockConfig -> 260
            else -> 300
        }
        val height = min(max(1, screen.height - dp(24)), dp(preferredHeightDp)).coerceAtLeast(1)
        return PanelSize(width, height)
    }

    private fun safeUpdate(entry: OverlayEntry) {
        try {
            entry.host.windowManager.updateViewLayout(entry.view, entry.params)
        } catch (exception: RuntimeException) {
            runtimeStore.update { it.copy(lastError = "调整悬浮窗位置失败") }
            Log.e(TAG, "调整悬浮窗失败: ${entry.config.id}", exception)
        }
    }

    private data class ScreenSize(val width: Int, val height: Int)
    private data class PanelSize(val width: Int, val height: Int)
    private data class OverlayEntry(
        val view: View,
        val params: WindowManager.LayoutParams,
        var config: OverlayConfig,
        val host: OverlayWindowHost,
    )
    private data class RegionGestureSnapshot(
        val configId: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    @Suppress("DEPRECATION")
    private fun screenSize(forceRefresh: Boolean = false): ScreenSize {
        cachedScreenSize?.takeUnless { forceRefresh }?.let { return it }
        val windowManager = applicationHost.windowManager
        val refreshed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 悬浮窗跨越其他应用，使用最大窗口范围确保可覆盖状态栏等系统栏区域。
            val bounds = windowManager.maximumWindowMetrics.bounds
            ScreenSize(bounds.width(), bounds.height())
        } else {
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            ScreenSize(metrics.widthPixels, metrics.heightPixels)
        }
        cachedScreenSize = refreshed
        return refreshed
    }

    private fun windowHostFor(config: OverlayConfig): OverlayWindowHost? =
        if (config is BurnMaskConfig) accessibilityHost else applicationHost

    private fun createWindowErrorMessage(
        config: OverlayConfig,
        exception: RuntimeException,
    ): String = if (config is BurnMaskConfig) {
        "无法创建防烧屏遮罩，请检查无障碍服务: ${exception.javaClass.simpleName}"
    } else {
        "无法创建悬浮窗，请检查悬浮窗权限: ${exception.javaClass.simpleName}"
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private class OverlayWindowHost(
        val context: Context,
        val type: Int,
    ) {
        val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    }

    private companion object {
        const val TAG = "OverlayManager"
        const val MIN_REGION_SIZE_DP = 4f
        const val MINIMIZED_PANEL_DP = 56
        const val PANEL_MARGIN_DP = 12
        const val PANEL_SNAP_DURATION_MS = 180L
        const val MAX_PASS_THROUGH_ALPHA = 0.8f
    }
}
