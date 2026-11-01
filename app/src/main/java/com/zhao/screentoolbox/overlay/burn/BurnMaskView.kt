package com.zhao.screentoolbox.overlay.burn

import android.content.Context
import android.content.res.Configuration
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.overlay.common.RegionEditView
import com.zhao.screentoolbox.overlay.common.overlayPrimaryColor

private const val RGB_MASK = 0x00FFFFFF
private const val OPAQUE_ALPHA_MASK = 0xFF000000.toInt()

/** 将配置颜色转换为固定完全不透明的 ARGB，颜色自身的透明度不参与遮罩渲染。 */
internal fun opaqueBurnMaskColor(colorArgb: Long): Int =
    (colorArgb.toInt() and RGB_MASK) or OPAQUE_ALPHA_MASK

/** 防烧屏遮罩使用实色绘制，编辑态额外显示蓝色边框。 */
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
            fillColor = opaqueBurnMaskColor(config.colorArgb),
            borderColor = context.overlayPrimaryColor(),
            cornerRadiusDp = config.cornerRadiusDp,
        )
    }
}
