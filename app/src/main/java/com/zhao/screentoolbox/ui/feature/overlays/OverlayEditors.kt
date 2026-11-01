package com.zhao.screentoolbox.ui.feature.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhao.screentoolbox.core.model.WatermarkConfig
import com.zhao.screentoolbox.core.model.WatermarkMode
import com.zhao.screentoolbox.ui.components.InfoCard

@Composable
fun WatermarkEditorScreen(
    initial: WatermarkConfig,
    onPreview: (WatermarkConfig) -> Unit,
    onEndPreview: () -> Unit,
    onSave: (WatermarkConfig) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    LaunchedEffect(draft) { onPreview(draft) }
    DisposableEffect(Unit) {
        onDispose(onEndPreview)
    }
    EditorFrame(
        title = if (initial.name.isBlank()) "新建水印" else "编辑水印",
        onBack = {
            onEndPreview()
            onBack()
        },
        onSave = {
            onEndPreview()
            onSave(draft)
            onBack()
        },
    ) {
        NameField(draft.name) { draft = draft.copy(name = it) }
        OutlinedTextField(
            value = draft.text,
            onValueChange = { value -> draft = draft.copy(text = value.take(120)) },
            label = { Text("水印文字") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.mode == WatermarkMode.SINGLE,
                onClick = { draft = draft.copy(mode = WatermarkMode.SINGLE) },
                label = { Text("单条") },
            )
            FilterChip(
                selected = draft.mode == WatermarkMode.TILED,
                onClick = { draft = draft.copy(mode = WatermarkMode.TILED) },
                label = { Text("平铺") },
            )
        }
        ColorField(draft.colorArgb) { draft = draft.copy(colorArgb = it) }
        ValueSlider("字体大小", draft.textSizeSp, 10f, 96f, "sp") {
            draft = draft.copy(textSizeSp = it)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(400, 500, 600, 700).forEach { weight ->
                FilterChip(
                    selected = draft.fontWeight == weight,
                    onClick = { draft = draft.copy(fontWeight = weight) },
                    label = { Text(weight.toString()) },
                )
            }
        }
        PercentSlider("不透明度", draft.alpha) { draft = draft.copy(alpha = it) }
        ValueSlider("旋转角度", draft.rotationDegrees, -90f, 90f, "°") {
            draft = draft.copy(rotationDegrees = it)
        }
        if (draft.mode == WatermarkMode.TILED) {
            ValueSlider("平铺间距", draft.tileSpacingDp, 32f, 240f, "dp") {
                draft = draft.copy(tileSpacingDp = it)
            }
        } else {
            PercentSlider("水平位置", draft.positionXRatio) {
                draft = draft.copy(positionXRatio = it)
            }
            PercentSlider("垂直位置", draft.positionYRatio) {
                draft = draft.copy(positionYRatio = it)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorFrame(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = onSave) {
                    Icon(Icons.Outlined.Save, contentDescription = "保存")
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(40)) },
        label = { Text("名称") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun PercentSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    ValueSlider(label, value, 0f, 1f, "%", divisor = 0.01f, onChange = onChange)
}

@Composable
private fun ValueSlider(
    label: String,
    value: Float,
    rangeStart: Float,
    rangeEnd: Float,
    unit: String,
    divisor: Float = 1f,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                "${(value / divisor).toInt()}$unit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(rangeStart, rangeEnd),
            onValueChange = onChange,
            valueRange = rangeStart..rangeEnd,
        )
    }
}

@Composable
private fun ColorField(colorArgb: Long, onColor: (Long) -> Unit) {
    val presets = listOf(0xFF000000, 0xFFFFFFFF, 0xFF2563EB, 0xFFEF4444, 0xFF16A34A, 0xFFF59E0B)
    var hexText by remember(colorArgb) {
        mutableStateOf("#${colorArgb.toString(16).uppercase().padStart(8, '0')}")
    }
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("颜色", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                presets.forEach { color ->
                    val selected = color == colorArgb
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(color.toInt()), CircleShape)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape,
                            )
                            .clickable { onColor(color) },
                    )
                }
            }
            OutlinedTextField(
                value = hexText,
                onValueChange = { value ->
                    hexText = value
                    parseArgb(value)?.let(onColor)
                },
                label = { Text("ARGB 十六进制") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

private fun parseArgb(input: String): Long? {
    val clean = input.removePrefix("#").trim()
    if (clean.length !in listOf(6, 8) || clean.any { it.digitToIntOrNull(16) == null }) return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) value or 0xFF000000 else value
}
