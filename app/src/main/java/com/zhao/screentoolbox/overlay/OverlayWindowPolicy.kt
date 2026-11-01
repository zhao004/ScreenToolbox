package com.zhao.screentoolbox.overlay

import android.view.WindowManager
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.core.model.WatermarkConfig

/** 统一计算不同类型悬浮窗的触摸和系统栏布局标志。 */
internal object OverlayWindowPolicy {
    fun flags(config: OverlayConfig, editable: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (config is BurnMaskConfig) {
            flags = flags or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        }
        val touchable = editable || config is TouchBlockConfig
        if (!touchable || config is WatermarkConfig) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }
}
