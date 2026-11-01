package com.zhao.screentoolbox.overlay.common

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import com.zhao.screentoolbox.ui.theme.AppPalette

/** 当前系统是否处于深色模式。 */
internal fun Context.isNightMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

/** 系统悬浮窗编辑提示使用的主强调色。 */
@ColorInt
internal fun Context.overlayPrimaryColor(): Int =
    (if (isNightMode()) AppPalette.Dark.primary else AppPalette.Light.primary).toArgb()
