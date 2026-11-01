package com.zhao.screentoolbox.ui.feature.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BorderClear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.core.model.WatermarkConfig
import com.zhao.screentoolbox.ui.MainUiState
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.ScreenHeader
import com.zhao.screentoolbox.ui.components.StatusBadge
import com.zhao.screentoolbox.ui.theme.BrandBlue

@Composable
fun OverlaysScreen(
    state: MainUiState,
    onAdd: (OverlayType) -> Unit,
    onEdit: (OverlayConfig) -> Unit,
    onToggle: (OverlayConfig, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedType by remember { mutableStateOf(OverlayType.BURN_MASK) }
    var pendingDelete by remember { mutableStateOf<OverlayConfig?>(null) }
    val items = state.overlays.filter { it.type == selectedType }
    val ruleControlled = selectedType in state.runtime.ruleControlledTypes

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("悬浮窗", "创建、编辑并控制独立的系统窗口")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TypeChip("防烧屏", OverlayType.BURN_MASK, selectedType) { selectedType = it }
                TypeChip("防误触", OverlayType.TOUCH_BLOCK, selectedType) { selectedType = it }
                TypeChip("水印", OverlayType.WATERMARK, selectedType) { selectedType = it }
            }
        }
        if (ruleControlled) {
            item {
                InfoCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                    Text(
                        "当前应用规则正在控制此类窗口，规则外的开关将保持锁定。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (items.isEmpty()) {
            item {
                InfoCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(typeIcon(selectedType), contentDescription = null, tint = BrandBlue)
                        Spacer(Modifier.padding(6.dp))
                        Text("还没有配置", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "创建后可在页面中精确编辑，并实时预览系统窗口。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.padding(6.dp))
                        Button(onClick = { onAdd(selectedType) }) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text("新建${typeName(selectedType)}")
                        }
                    }
                }
            }
        }
        items(items, key = { it.id }) { config ->
            OverlayConfigRow(
                config = config,
                enabled = !ruleControlled,
                onToggle = { onToggle(config, it) },
                onEdit = { onEdit(config) },
                onDelete = { pendingDelete = config },
            )
        }
        if (items.isNotEmpty()) {
            item {
                Button(onClick = { onAdd(selectedType) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("新建${typeName(selectedType)}")
                }
            }
        }
    }

    pendingDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除“${config.name}”") },
            text = { Text("该配置及其应用绑定会一并删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(config.id)
                        pendingDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun OverlayConfigRow(
    config: OverlayConfig,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(typeIcon(config.type), contentDescription = null, tint = BrandBlue)
            Column(Modifier.weight(1f)) {
                Text(config.name, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(
                        text = if (config.enabled) "已启用" else "已停用",
                        active = config.enabled,
                    )
                    Text(
                        configSummary(config),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = onToggle,
                enabled = enabled,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    type: OverlayType,
    selected: OverlayType,
    onSelect: (OverlayType) -> Unit,
) {
    FilterChip(
        selected = selected == type,
        onClick = { onSelect(type) },
        label = { Text(label) },
    )
}

private fun typeName(type: OverlayType): String = when (type) {
    OverlayType.BURN_MASK -> "防烧屏遮罩"
    OverlayType.TOUCH_BLOCK -> "防误触区域"
    OverlayType.WATERMARK -> "水印"
}

private fun typeIcon(type: OverlayType): ImageVector = when (type) {
    OverlayType.BURN_MASK -> Icons.Outlined.BorderClear
    OverlayType.TOUCH_BLOCK -> Icons.Outlined.Layers
    OverlayType.WATERMARK -> Icons.Outlined.WaterDrop
}

private fun configSummary(config: OverlayConfig): String = when (config) {
    is BurnMaskConfig -> "透明度 ${(config.alpha * 100).toInt()}%"
    is TouchBlockConfig -> "${(config.rect.width * 100).toInt()} × ${(config.rect.height * 100).toInt()}%"
    is WatermarkConfig -> if (config.text.length > 12) config.text.take(12) + "…" else config.text
}
