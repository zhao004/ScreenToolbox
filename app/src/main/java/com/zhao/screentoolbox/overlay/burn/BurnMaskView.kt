package com.zhao.screentoolbox.overlay.burn

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.overlay.common.RegionEditView
import com.zhao.screentoolbox.overlay.common.overlayPrimaryColor

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
        val color = config.colorArgb.toInt()
        configurePaint(
            fillColor = Color.argb((config.alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color)),
            borderColor = context.overlayPrimaryColor(),
            cornerRadiusDp = config.cornerRadiusDp,
        )
    }
}
