package com.zhao.screentoolbox.overlay.editor

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.zhao.screentoolbox.R
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.overlay.common.ScreenDrag
import com.zhao.screentoolbox.overlay.common.isNightMode
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val MAX_PERCENT = 100
private const val DEFAULT_SEEK_BAR_INSET_DP = 12

/**
 * 防烧屏和防误触共用的系统悬浮编辑面板。
 *
 * 面板只负责参数输入和命令回调，不直接持久化配置或操作目标区域窗口。
 */
class OverlayEditorPanelView(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isDarkMode = context.isNightMode()
    private var palette = resolveOverlayEditorPalette(isDarkMode)
    private var currentConfig: OverlayConfig? = null
    private var onConfigChanged: ((OverlayConfig) -> Unit)? = null
    private var onSave: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var screenWidthPx = 1
    private var screenHeightPx = 1
    private var minWidthPx = 1
    private var minHeightPx = 1
    private var minimized = false
    private var showOpacityControl = false

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
        showOpacityControl: Boolean,
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
        this.showOpacityControl = showOpacityControl
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
        findFocus()?.let(::hideKeyboard)
        clearFocus()
        minimized = true
        removeAllViews()
        setPadding(0, 0, 0, 0)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.primary)
            setStroke(dp(2), palette.outline)
        }
        isClickable = true
        contentDescription = "展开编辑面板"
        addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_notification)
                imageTintList = ColorStateList.valueOf(palette.onPrimary)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nextDarkMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        if (nextDarkMode == isDarkMode) return

        isDarkMode = nextDarkMode
        palette = resolveOverlayEditorPalette(isDarkMode)
        if (minimized) {
            showMinimized()
        } else {
            currentConfig?.let(::rebuildExpandedContent)
        }
    }

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
            setColor(palette.container)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), palette.containerBorder)
        }
        addView(createHeader(if (config is BurnMaskConfig) "防烧屏编辑" else "防误触编辑"))
        addView(createDivider())

        val controls = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        controls.addView(createNameField(config.name))
        controls.addView(createSizeField("宽度", config.rect.width, isWidth = true))
        controls.addView(createSizeField("高度", config.rect.height, isWidth = false))
        if (showOpacityControl) {
            controls.addView(createOpacityField(config))
        }
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
            addView(createDragImage())
            addView(
                textView(title, 17f, palette.title).apply {
                    setTypeface(typeface, Typeface.BOLD)
                },
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                },
            )
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

    private fun createNameField(name: String): View {
        val input = EditText(context).apply {
            setText(name)
            hint = "例如：视频防烧屏"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(MAX_RULE_NAME_LENGTH))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(palette.title)
            setHintTextColor(palette.body)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), 0, dp(12), 0)
            background = GradientDrawable().apply {
                setColor(palette.neutralContainer)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), palette.outline)
            }
            setOnEditorActionListener { view, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard(view)
                    view.clearFocus()
                    true
                } else {
                    false
                }
            }
            doAfterTextChanged { updateConfigName(it?.toString().orEmpty()) }
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(textView("名称", 14f, palette.title))
            addView(
                input,
                LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
                    topMargin = dp(4)
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
        val valueText = textView("", 13f, palette.body)
        val seekBar = createStyledSeekBar().apply {
            progressTintList = ColorStateList.valueOf(palette.primary)
            thumbTintList = ColorStateList.valueOf(palette.primary)
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
                        textView(label, 14f, palette.title),
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

    private fun createOpacityField(config: OverlayConfig): View {
        val alpha = when (config) {
            is BurnMaskConfig -> config.alpha
            is TouchBlockConfig -> config.alpha
            else -> error("透明度控件只支持防烧屏和防误触配置")
        }
        return createSliderField(
            label = "透明度",
            initial = (alpha.coerceIn(0f, 1f) * MAX_PERCENT).roundToInt(),
            min = 0,
            max = MAX_PERCENT,
            unit = "%",
        ) { value ->
            val normalizedAlpha = value.toFloat() / MAX_PERCENT
            when (currentConfig) {
                is BurnMaskConfig -> updateBurnConfig { it.copy(alpha = normalizedAlpha) }
                is TouchBlockConfig -> updateTouchConfig { it.copy(alpha = normalizedAlpha) }
                else -> Unit
            }
        }
    }

    private fun addTouchControls(parent: LinearLayout, config: TouchBlockConfig) {
        parent.addView(
            CheckBox(context).apply {
                text = "显示区域边界"
                setTextColor(palette.title)
                textSize = 14f
                buttonTintList = ColorStateList.valueOf(palette.primary)
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
        val valueText = textView("$initial$unit", 13f, palette.body)
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(2), 0, 0)
            addView(
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        textView(label, 14f, palette.title),
                        LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(valueText)
                },
            )
            addView(
                createStyledSeekBar().apply {
                    contentDescription = label
                    this.max = max - min
                    progress = initial - min
                    progressTintList = ColorStateList.valueOf(palette.primary)
                    thumbTintList = ColorStateList.valueOf(palette.primary)
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
            val thumbInset = thumb?.intrinsicWidth?.div(2)
                ?.coerceAtLeast(1)
                ?: dp(DEFAULT_SEEK_BAR_INSET_DP)
            setPadding(thumbInset, 0, thumbInset, 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(34))
        }
    }

    private fun createColorField(initialColor: Long): View {
        colorSwatches.clear()
        val selectedValue = textView("", 12f, palette.body).apply {
            typeface = Typeface.MONOSPACE
        }
        colorValueLabel = selectedValue
        val swatchRow = LinearLayout(context).apply {
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
            swatchRow.addView(
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
                swatchRow,
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
                        textView("颜色", 14f, palette.title),
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
                    if (selected) palette.primary else palette.outline,
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
                actionButton(
                    text = "取消",
                    containerColor = palette.neutralContainer,
                    contentColor = palette.onNeutralContainer,
                ) {
                    findFocus()?.let(::hideKeyboard)
                    onCancel?.invoke()
                },
                LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) },
            )
            addView(
                actionButton(
                    text = "保存",
                    containerColor = palette.primary,
                    contentColor = palette.onPrimary,
                ) { saveCurrentConfig() },
                LayoutParams(0, dp(42), 1f).apply { marginStart = dp(8) },
            )
        }
    }

    private fun actionButton(
        text: String,
        containerColor: Int,
        contentColor: Int,
        action: () -> Unit,
    ): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            setTextColor(contentColor)
            backgroundTintList = ColorStateList.valueOf(containerColor)
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { action() }
        }
    }

    private fun createDivider(): View {
        return View(context).apply {
            setBackgroundColor(palette.outlineVariant)
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

    private fun updateConfigName(name: String) {
        val updated = when (val config = currentConfig) {
            is BurnMaskConfig -> config.copy(name = name)
            is TouchBlockConfig -> config.copy(name = name)
            else -> return
        }
        currentConfig = updated
        onConfigChanged?.invoke(updated)
    }

    private fun saveCurrentConfig() {
        val config = currentConfig ?: return
        val normalizedName = config.name.trim().ifBlank { defaultName(config) }
        if (normalizedName != config.name) {
            updateConfigName(normalizedName)
        }
        findFocus()?.let(::hideKeyboard)
        onSave?.invoke()
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
            imageTintList = ColorStateList.valueOf(palette.onPrimaryContainer)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(palette.primaryContainer)
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

    private fun hideKeyboard(view: View) {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun defaultName(config: OverlayConfig): String = when (config) {
        is BurnMaskConfig -> "防烧屏遮罩"
        is TouchBlockConfig -> "防误触区域"
        else -> "悬浮窗配置"
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        const val MIN_SIZE_PERCENT = 1
        const val MAX_RULE_NAME_LENGTH = 40
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
