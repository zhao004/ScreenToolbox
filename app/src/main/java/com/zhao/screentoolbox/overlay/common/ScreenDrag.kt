package com.zhao.screentoolbox.overlay.common

import kotlin.math.roundToInt

/** 使用屏幕原始坐标计算拖动增量，避免窗口移动后局部坐标原点变化。 */
internal object ScreenDrag {
    fun between(
        previousRawX: Float,
        previousRawY: Float,
        rawX: Float,
        rawY: Float,
    ): Pair<Int, Int> {
        return (rawX - previousRawX).roundToInt() to
            (rawY - previousRawY).roundToInt()
    }
}
