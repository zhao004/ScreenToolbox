package com.zhao.screentoolbox.overlay.burn

import android.content.Context
import android.content.res.Configuration
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.overlay.common.RegionEditView
import com.zhao.screentoolbox.overlay.common.overlayPrimaryColor
import kotlin.math.roundToInt

private const val RGB_MASK = 0x00FFFFFF
private const val MAX_ALPHA = 255

/** 将配置颜色和独立透明度组合为最终填充色，忽略颜色值自身的 Alpha 通道。 */
internal fun burnMaskFillColor(colorArgb: Long, alpha: Float): Int {
    val alphaChannel = (alpha.coerceIn(0f, 1f) * MAX_ALPHA).roundToInt()
    return (colorArgb.toInt() and RGB_MASK) or (alphaChannel shl 24)
}

/** 防烧屏遮罩按配置透明度绘制填充，编辑态额外显示蓝色边框。 */
class BurnMaskView(context: Context) : RegionEditView(context) {
    private var currentConfig: BurnMaskConfig? = null
    private var currentEditable = false

    fun bind(config: BurnMaskConfig, editable: Boolean) {
        currentConfig = config
        currentEditable = editable
        applyTheme()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme()
    }

    private fun applyTheme() {
        val config = currentConfig ?: return
        editable = currentEditable
        configurePaint(
            fillColor = burnMaskFillColor(config.colorArgb, config.alpha),
            borderColor = context.overlayPrimaryColor(),
            cornerRadiusDp = config.cornerRadiusDp,
        )
    }
}
