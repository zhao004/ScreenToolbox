package com.zhao.screentoolbox.overlay.burn

import android.content.Context
import android.graphics.Color
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.overlay.common.RegionEditView

/** 防烧屏遮罩使用实色绘制，编辑态额外显示蓝色边框和缩放手柄。 */
class BurnMaskView(context: Context) : RegionEditView(context) {
    fun bind(config: BurnMaskConfig, editable: Boolean) {
        this.editable = editable
        val color = config.colorArgb.toInt()
        configurePaint(
            fillColor = Color.argb((config.alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color)),
            borderColor = Color.rgb(37, 99, 235),
            cornerRadiusDp = config.cornerRadiusDp,
        )
    }
}
