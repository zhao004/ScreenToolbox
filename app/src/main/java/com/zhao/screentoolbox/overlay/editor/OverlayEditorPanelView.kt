package com.zhao.screentoolbox.overlay.editor

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.zhao.screentoolbox.R
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.overlay.common.ScreenDrag
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 防烧屏和防误触共用的系统悬浮编辑面板。
 *
 * 面板只负责参数输入和命令回调，不直接持久化配置或操作目标区域窗口。
 */
class OverlayEditorPanelView(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var currentConfig: OverlayConfig? = null
    private var onConfigChanged: ((OverlayConfig) -> Unit)? = null
    private var onSave: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var screenWidthPx = 1
    private var screenHeightPx = 1
    private var minWidthPx = 1
    private var minHeightPx = 1
    private var minimized = false

    private var widthSlider: SeekBar? = null
    private var widthValueLabel: TextView? = null
    private var heightSlider: SeekBar? = null
    private var heightValueLabel: TextView? = null
    private var colorPaletteScroll: HorizontalScrollView? = null
    private var colorValueLabel: TextView? = null
    private val colorSwatches = linkedMapOf<Long, View>()

    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var dragging = false

    var onDrag: ((dx: Int, dy: Int) -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    var onMinimize: (() -> Unit)? = null
    var onRestore: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        elevation = dp(12).toFloat()
        clipToOutline = true
    }

    /** 首次显示面板时绑定配置、屏幕边界和操作回调。 */
    fun bind(
        config: OverlayConfig,
        screenWidth: Int,
        screenHeight: Int,
        minWidthPx: Int,
        minHeightPx: Int,
        onConfigChanged: (OverlayConfig) -> Unit,
        onSave: () -> Unit,
        onCancel: () -> Unit,
    ) {
        require(config is BurnMaskConfig || config is TouchBlockConfig) {
            "悬浮编辑面板只支持防烧屏和防误触配置"
        }
        currentConfig = config
        this.onConfigChanged = onConfigChanged
        this.onSave = onSave
        this.onCancel = onCancel
        setScreenBounds(screenWidth, screenHeight, minWidthPx, minHeightPx)
        showExpanded()
    }

    /** 屏幕尺寸或最小窗口约束变化时同步进度条范围。 */
    fun setScreenBounds(
        screenWidth: Int,
        screenHeight: Int,
        minWidthPx: Int,
        minHeightPx: Int,
    ) {
        screenWidthPx = screenWidth.coerceAtLeast(1)
        screenHeightPx = screenHeight.coerceAtLeast(1)
        this.minWidthPx = minWidthPx.coerceAtLeast(1)
        this.minHeightPx = minHeightPx.coerceAtLeast(1)
        updateSizeControls()
    }

    /** 区域移动完成后同步几何参数与进度条显示。 */
    fun updateGeometry(rect: NormalizedRect) {
        currentConfig = when (val config = currentConfig) {
            is BurnMaskConfig -> config.copy(rect = rect)
            is TouchBlockConfig -> config.copy(rect = rect)
            else -> config
        }
        updateSizeControls()
    }

    /** 切换为仅显示圆图标的最小化形态。 */
    fun showMinimized() {
        minimized = true
        removeAllViews()
        setPadding(0, 0, 0, 0)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(37, 99, 235))
            setStroke(dp(2), Color.rgb(147, 197, 253))
        }
        isClickable = true
        contentDescription = "展开编辑面板"
        addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_notification)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                isClickable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LayoutParams(dp(28), dp(28)).apply {
                gravity = Gravity.CENTER
            },
        )
        setOnTouchListener { _, event -> handleMinimizedTouch(event) }
    }

    /** 恢复完整参数编辑形态。 */
    fun showExpanded() {
        minimized = false
        setOnTouchListener(null)
        isClickable = false
        contentDescription = null
        gravity = Gravity.TOP
        rebuildExpandedContent(currentConfig ?: return)
    }

    fun isMinimized(): Boolean = minimized

    private fun rebuildExpandedContent(config: OverlayConfig) {
        removeAllViews()
        widthSlider = null
        widthValueLabel = null
        heightSlider = null
        heightValueLabel = null
        colorPaletteScroll = null
        colorValueLabel = null
        colorSwatches.clear()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.argb(246, 15, 23, 42))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(71, 85, 105))
        }
        addView(createHeader(if (config is BurnMaskConfig) "防烧屏编辑" else "防误触编辑"))
        addView(createDivider())

        val controls = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        controls.addView(createSizeField("宽度", config.rect.width, isWidth = true))
        controls.addView(createSizeField("高度", config.rect.height, isWidth = false))
        when (config) {
            is BurnMaskConfig -> addBurnControls(controls, config)
            is TouchBlockConfig -> addTouchControls(controls, config)
            else -> error("不支持的悬浮编辑类型")
        }
        addView(
            ScrollView(context).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
                addView(
                    controls,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
        )
        addView(createActionRow())
        updateSizeControls()
    }

    private fun createHeader(title: String): View {
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = HORIZONTAL
            addView(
                textView(title, 17f, Color.WHITE).apply {
                    setTypeface(typeface, Typeface.BOLD)
                },
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(createDragImage())
            addView(
                imageView(R.drawable.ic_panel_minimize, "缩小编辑面板").apply {
                    setOnClickListener { onMinimize?.invoke() }
                },
                LayoutParams(dp(40), dp(40)).apply {
                    marginStart = dp(4)
                },
            )
        }
    }

    private fun createDragImage(): View {
        return imageView(R.drawable.ic_drag_handle, "拖动编辑面板").apply {
            isClickable = true
            setOnTouchListener { _, event -> handleDragTouch(event) }
        }.also { dragImage ->
            dragImage.layoutParams = LayoutParams(dp(40), dp(40))
        }
    }

    private fun createSizeField(label: String, ratio: Float, isWidth: Boolean): View {
        val valueText = textView("", 13f, Color.rgb(203, 213, 225))
        val seekBar = createStyledSeekBar().apply {
            progressTintList = ColorStateList.valueOf(Color.rgb(37, 99, 235))
            thumbTintList = ColorStateList.valueOf(Color.rgb(147, 197, 253))
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        updateSizeFromSliders()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                },
            )
        }
        if (isWidth) {
            widthSlider = seekBar
            widthValueLabel = valueText
        } else {
            heightSlider = seekBar
            heightValueLabel = valueText
        }
        setSliderValue(seekBar, ratio, isWidth)
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(2), 0, 0)
            addView(
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        textView(label, 14f, Color.WHITE),
                        LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(valueText)
                },
            )
            addView(seekBar)
        }
    }

    private fun addBurnControls(parent: LinearLayout, config: BurnMaskConfig) {
        parent.addView(
            createSliderField(
                label = "透明度",
                initial = config.alpha.times(100).roundToInt(),
                min = 0,
                max = 100,
                unit = "%",
            ) { value ->
                updateBurnConfig { burn ->
                    burn.copy(alpha = value.coerceIn(0, 100) / 100f)
                }
            },
        )
        parent.addView(
            createSliderField(
                label = "圆角",
                initial = config.cornerRadiusDp.roundToInt(),
                min = 0,
                max = 64,
                unit = "dp",
            ) { value ->
                updateBurnConfig { burn ->
                    burn.copy(cornerRadiusDp = value.toFloat())
                }
            },
        )
        parent.addView(createColorField(config.colorArgb))
    }

    private fun addTouchControls(parent: LinearLayout, config: TouchBlockConfig) {
        parent.addView(
            CheckBox(context).apply {
                text = "显示区域边界"
                setTextColor(Color.WHITE)
                textSize = 14f
                buttonTintList = ColorStateList.valueOf(Color.rgb(96, 165, 250))
                isChecked = config.boundaryVisible
                setOnCheckedChangeListener { _, checked ->
                    updateTouchConfig { touch -> touch.copy(boundaryVisible = checked) }
                }
            },
        )
    }

    private fun updateSizeFromSliders() {
        val config = currentConfig ?: return
        val widthPercent = minWidthPercent() + (widthSlider?.progress ?: 0)
        val heightPercent = minHeightPercent() + (heightSlider?.progress ?: 0)
        val updatedRect = OverlayPanelGeometry.resizeRect(
            rect = config.rect,
            widthPercent = widthPercent,
            heightPercent = heightPercent,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            minWidthPx = minWidthPx,
            minHeightPx = minHeightPx,
        )
        widthValueLabel?.text = "${(updatedRect.width * 100).roundToInt()}%"
        heightValueLabel?.text = "${(updatedRect.height * 100).roundToInt()}%"
        updateConfigRect(updatedRect)
    }

    private fun updateSizeControls() {
        val rect = currentConfig?.rect ?: return
        widthSlider?.let {
            setSliderValue(it, rect.width, isWidth = true)
        }
        heightSlider?.let {
            setSliderValue(it, rect.height, isWidth = false)
        }
    }

    private fun setSliderValue(seekBar: SeekBar, ratio: Float, isWidth: Boolean) {
        val minPercent = if (isWidth) minWidthPercent() else minHeightPercent()
        val percent = (ratio * 100).roundToInt().coerceIn(minPercent, 100)
        seekBar.max = 100 - minPercent
        seekBar.progress = percent - minPercent
        if (isWidth) {
            widthValueLabel?.text = "$percent%"
        } else {
            heightValueLabel?.text = "$percent%"
        }
    }

    private fun minWidthPercent(): Int =
        ceil(minWidthPx * 100f / screenWidthPx).toInt().coerceIn(MIN_SIZE_PERCENT, 100)

    private fun minHeightPercent(): Int =
        ceil(minHeightPx * 100f / screenHeightPx).toInt().coerceIn(MIN_SIZE_PERCENT, 100)

    private fun createSliderField(
        label: String,
        initial: Int,
        min: Int,
        max: Int,
        unit: String,
        onValue: (Int) -> Unit,
    ): View {
        val valueText = textView("$initial$unit", 13f, Color.rgb(203, 213, 225))
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(2), 0, 0)
            addView(
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        textView(label, 14f, Color.WHITE),
                        LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(valueText)
                },
            )
            addView(
                createStyledSeekBar().apply {
                    this.max = max - min
                    progress = initial - min
                    progressTintList = ColorStateList.valueOf(Color.rgb(37, 99, 235))
                    thumbTintList = ColorStateList.valueOf(Color.rgb(147, 197, 253))
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                                if (!fromUser) return
                                val value = progress + min
                                valueText.text = "$value$unit"
                                onValue(value)
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                        },
                    )
                },
            )
        }
    }

    private fun createStyledSeekBar(): SeekBar {
        return SeekBar(context).apply {
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(34))
        }
    }

    private fun createColorField(initialColor: Long): View {
        colorSwatches.clear()
        val selectedValue = textView("", 12f, Color.rgb(203, 213, 225)).apply {
            typeface = Typeface.MONOSPACE
        }
        colorValueLabel = selectedValue
        val palette = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        COLOR_PRESETS.forEachIndexed { index, color ->
            val swatch = View(context).apply {
                tag = colorSwatchTag(color)
                isFocusable = true
                setOnClickListener { selectBurnColor(color) }
            }
            colorSwatches[color] = swatch
            palette.addView(
                swatch,
                LayoutParams(dp(36), dp(32)).apply {
                    if (index > 0) marginStart = dp(6)
                },
            )
        }
        val paletteScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_ALWAYS
            clipToPadding = false
            addView(
                palette,
                LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)),
            )
        }
        colorPaletteScroll = paletteScroll
        updateColorSelection(initialColor)
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(6), 0, dp(2))
            addView(
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        textView("颜色", 14f, Color.WHITE),
                        LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(selectedValue)
                },
            )
            addView(paletteScroll)
        }
    }

    private fun selectBurnColor(color: Long) {
        updateBurnConfig { burn -> burn.copy(colorArgb = color) }
        updateColorSelection(color)
    }

    private fun updateColorSelection(selectedColor: Long) {
        colorSwatches.forEach { (color, swatch) ->
            val selected = color == selectedColor
            swatch.isSelected = selected
            swatch.contentDescription = buildString {
                append("颜色 #")
                append(colorHex(color))
                if (selected) append("，已选择")
            }
            swatch.background = GradientDrawable().apply {
                setColor(color.toInt())
                cornerRadius = dp(7).toFloat()
                setStroke(
                    dp(if (selected) 3 else 1),
                    if (selected) Color.rgb(191, 219, 254) else Color.rgb(100, 116, 139),
                )
            }
        }
        colorValueLabel?.text = "#${colorHex(selectedColor)}"
        val selectedSwatch = colorSwatches[selectedColor] ?: return
        val scroll = colorPaletteScroll ?: return
        scroll.post {
            val target = selectedSwatch.left -
                (scroll.width - selectedSwatch.width) / 2
            scroll.smoothScrollTo(target.coerceAtLeast(0), 0)
        }
    }

    private fun createActionRow(): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            addView(
                actionButton("取消", Color.rgb(71, 85, 105)) { onCancel?.invoke() },
                LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) },
            )
            addView(
                actionButton("保存", Color.rgb(37, 99, 235)) { onSave?.invoke() },
                LayoutParams(0, dp(42), 1f).apply { marginStart = dp(8) },
            )
        }
    }

    private fun actionButton(text: String, color: Int, action: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(color)
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { action() }
        }
    }

    private fun createDivider(): View {
        return View(context).apply {
            setBackgroundColor(Color.rgb(51, 65, 85))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(2)
            }
        }
    }

    private fun colorHex(color: Long): String =
        color.toString(16).uppercase().padStart(8, '0')

    private fun colorSwatchTag(color: Long): String = "color-swatch:${colorHex(color)}"

    private fun updateBurnConfig(transform: (BurnMaskConfig) -> BurnMaskConfig) {
        val updated = (currentConfig as? BurnMaskConfig)?.let(transform) ?: return
        currentConfig = updated
        onConfigChanged?.invoke(updated)
    }

    private fun updateTouchConfig(transform: (TouchBlockConfig) -> TouchBlockConfig) {
        val updated = (currentConfig as? TouchBlockConfig)?.let(transform) ?: return
        currentConfig = updated
        onConfigChanged?.invoke(updated)
    }

    private fun updateConfigRect(rect: NormalizedRect) {
        val updated = when (val config = currentConfig) {
            is BurnMaskConfig -> config.copy(rect = rect)
            is TouchBlockConfig -> config.copy(rect = rect)
            else -> return
        }
        currentConfig = updated
        onConfigChanged?.invoke(updated)
    }

    private fun handleDragTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartRawX = event.rawX
                touchStartRawY = event.rawY
                lastTouchRawX = event.rawX
                lastTouchRawY = event.rawY
                dragging = true
                onDragStart?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val delta = ScreenDrag.between(
                    previousRawX = lastTouchRawX,
                    previousRawY = lastTouchRawY,
                    rawX = event.rawX,
                    rawY = event.rawY,
                )
                lastTouchRawX = event.rawX
                lastTouchRawY = event.rawY
                if (delta.first != 0 || delta.second != 0) {
                    onDrag?.invoke(delta.first, delta.second)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                dragging = false
                onDragEnd?.invoke()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                onDragEnd?.invoke()
                return true
            }
        }
        return false
    }

    private fun handleMinimizedTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartRawX = event.rawX
                touchStartRawY = event.rawY
                lastTouchRawX = event.rawX
                lastTouchRawY = event.rawY
                dragging = false
                onDragStart?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val totalX = event.rawX - touchStartRawX
                val totalY = event.rawY - touchStartRawY
                if (!dragging &&
                    totalX * totalX + totalY * totalY > touchSlop * touchSlop
                ) {
                    dragging = true
                }
                if (dragging) {
                    val delta = ScreenDrag.between(
                        previousRawX = lastTouchRawX,
                        previousRawY = lastTouchRawY,
                        rawX = event.rawX,
                        rawY = event.rawY,
                    )
                    lastTouchRawX = event.rawX
                    lastTouchRawY = event.rawY
                    if (delta.first != 0 || delta.second != 0) {
                        onDrag?.invoke(delta.first, delta.second)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    onDragEnd?.invoke()
                } else {
                    performClick()
                    onRestore?.invoke()
                }
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                onDragEnd?.invoke()
                dragging = false
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun imageView(resourceId: Int, description: String): ImageView {
        return ImageView(context).apply {
            setImageResource(resourceId)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(90, 37, 99, 235))
                cornerRadius = dp(10).toFloat()
            }
        }
    }

    private fun textView(text: String, sizeSp: Float, color: Int): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        const val MIN_SIZE_PERCENT = 1
        val COLOR_PRESETS = listOf(
            0xFF000000,
            0xFF1F2937,
            0xFF475569,
            0xFF1D4ED8,
            0xFF2563EB,
            0xFF0284C7,
            0xFF0D9488,
            0xFF16A34A,
            0xFFCA8A04,
            0xFFF97316,
            0xFFEF4444,
            0xFFDB2777,
            0xFFFFFFFF,
        )
    }
}
