package com.zhao.screentoolbox.ui.feature.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BorderClear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
    val addLabel = "新建${typeName(selectedType)}"

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 20.dp,
                end = 16.dp,
                bottom = 112.dp,
            ),
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
                    InfoCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
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
                            Icon(
                                typeIcon(selectedType),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.padding(6.dp))
                            Text("还没有配置", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "创建后可在页面中精确编辑，并实时预览系统窗口。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(items, key = { it.id }) { config ->
                val boundAppCount = state.rules.count { config.id in it.configIds }
                OverlayConfigRow(
                    config = config,
                    canToggleGlobally = !ruleControlled && boundAppCount == 0,
                    boundAppCount = boundAppCount,
                    onToggle = { onToggle(config, it) },
                    onEdit = { onEdit(config) },
                    onDelete = { pendingDelete = config },
                )
            }
        }

        FloatingActionButton(
            onClick = { onAdd(selectedType) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .semantics { contentDescription = addLabel },
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
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
    canToggleGlobally: Boolean,
    boundAppCount: Int,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    InfoCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        typeIcon(config.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        config.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusBadge(
                            text = when {
                                boundAppCount > 0 -> "应用限定"
                                config.enabled -> "全局启用"
                                else -> "已停用"
                            },
                            active = boundAppCount > 0 || config.enabled,
                        )
                        Text(
                            if (boundAppCount > 0) {
                                "${configSummary(config)} · $boundAppCount 个应用"
                            } else {
                                configSummary(config)
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (boundAppCount == 0) {
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = onToggle,
                        enabled = canToggleGlobally,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("编辑")
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
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
