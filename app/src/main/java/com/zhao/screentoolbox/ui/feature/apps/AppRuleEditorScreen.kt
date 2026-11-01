package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BorderClear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.core.model.WatermarkConfig
import com.zhao.screentoolbox.core.model.isGlobalRule
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.StatusBadge

/**
 * 应用或全局应用的规则配置页。
 *
 * 页面只展示当前规则已绑定的配置，新增配置统一从右下角添加入口发起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleEditorScreen(
    initial: AppRule,
    overlays: List<OverlayConfig>,
    ruleReferenceCounts: Map<String, Int>,
    onRuleChanged: (AppRule) -> Unit,
    onAddRule: (AppRule, OverlayType) -> Unit,
    onEditConfig: (AppRule, OverlayConfig) -> Unit,
    onDeleteConfig: (OverlayConfig) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(initial.packageName) { mutableStateOf(initial) }
    var burnMaskExpanded by rememberSaveable(initial.packageName) { mutableStateOf(false) }
    var touchBlockExpanded by rememberSaveable(initial.packageName) { mutableStateOf(false) }
    var watermarkExpanded by rememberSaveable(initial.packageName) { mutableStateOf(false) }
    var showAddDialog by rememberSaveable(initial.packageName) { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<OverlayConfig?>(null) }
    val attachedConfigs = overlays.filter { it.id in draft.configIds }
    val burnMaskConfigs = attachedConfigs.filter { it.type == OverlayType.BURN_MASK }
    val touchBlockConfigs = attachedConfigs.filter { it.type == OverlayType.TOUCH_BLOCK }
    val watermarkConfigs = attachedConfigs.filter { it.type == OverlayType.WATERMARK }
    val isGlobal = initial.isGlobalRule()
    val updateRule: (AppRule) -> Unit = { updated ->
        draft = updated
        onRuleChanged(updated)
    }

    LaunchedEffect(initial.configIds) {
        if (draft.configIds != initial.configIds) {
            draft = draft.copy(configIds = initial.configIds)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(if (isGlobal) "全局应用" else initial.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    InfoCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("启用规则", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (isGlobal) {
                                        "全局规则会叠加到应用专属规则"
                                    } else {
                                        initial.packageName
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = draft.enabled,
                                onCheckedChange = { updateRule(draft.copy(enabled = it)) },
                            )
                        }
                    }
                }
                item {
                    RuleSection(
                        title = "防烧屏",
                        subtitle = ruleSummary(burnMaskConfigs),
                        icon = Icons.Outlined.BorderClear,
                        iconActive = draft.burnMaskEnabled,
                        expanded = burnMaskExpanded,
                        onExpandedChange = { burnMaskExpanded = !burnMaskExpanded },
                        action = {
                            Switch(
                                checked = draft.burnMaskEnabled,
                                onCheckedChange = { updateRule(draft.copy(burnMaskEnabled = it)) },
                            )
                        },
                    ) {
                        RuleConfigList(
                            configs = burnMaskConfigs,
                            onEdit = { onEditConfig(draft, it) },
                            onDelete = { pendingDelete = it },
                        )
                    }
                }
                item {
                    RuleSection(
                        title = "防误触",
                        subtitle = ruleSummary(touchBlockConfigs),
                        icon = Icons.Outlined.Layers,
                        iconActive = draft.touchBlockEnabled,
                        expanded = touchBlockExpanded,
                        onExpandedChange = { touchBlockExpanded = !touchBlockExpanded },
                        action = {
                            Switch(
                                checked = draft.touchBlockEnabled,
                                onCheckedChange = { updateRule(draft.copy(touchBlockEnabled = it)) },
                            )
                        },
                    ) {
                        RuleConfigList(
                            configs = touchBlockConfigs,
                            onEdit = { onEditConfig(draft, it) },
                            onDelete = { pendingDelete = it },
                        )
                    }
                }
                item {
                    RuleSection(
                        title = "全局水印",
                        subtitle = ruleSummary(watermarkConfigs),
                        icon = Icons.Outlined.WaterDrop,
                        iconActive = draft.watermarkEnabled,
                        expanded = watermarkExpanded,
                        onExpandedChange = { watermarkExpanded = !watermarkExpanded },
                        action = {
                            Switch(
                                checked = draft.watermarkEnabled,
                                onCheckedChange = { updateRule(draft.copy(watermarkEnabled = it)) },
                            )
                        },
                    ) {
                        RuleConfigList(
                            configs = watermarkConfigs,
                            onEdit = { onEditConfig(draft, it) },
                            onDelete = { pendingDelete = it },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "添加规则")
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            isGlobal = isGlobal,
            onDismiss = { showAddDialog = false },
            onSelect = { type ->
                showAddDialog = false
                onAddRule(draft, type)
            },
        )
    }

    pendingDelete?.let { config ->
        val referenceCount = (ruleReferenceCounts[config.id] ?: 0).coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除“${config.name}”") },
            text = {
                Text("该配置将从 $referenceCount 条规则中移除并永久删除，此操作无法撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConfig(config)
                        pendingDelete = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AddRuleDialog(
    isGlobal: Boolean,
    onDismiss: () -> Unit,
    onSelect: (OverlayType) -> Unit,
) {
    val scope = if (isGlobal) "全局应用" else "当前应用"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加规则") },
        text = {
            Column {
                RuleTypeOption(
                    title = "防烧屏",
                    description = "为${scope}创建遮罩区域并进入悬浮编辑",
                    icon = Icons.Outlined.BorderClear,
                    onClick = { onSelect(OverlayType.BURN_MASK) },
                )
                HorizontalDivider()
                RuleTypeOption(
                    title = "防误触",
                    description = "为${scope}创建触摸拦截区域并进入悬浮编辑",
                    icon = Icons.Outlined.Layers,
                    onClick = { onSelect(OverlayType.TOUCH_BLOCK) },
                )
                HorizontalDivider()
                RuleTypeOption(
                    title = "全局水印",
                    description = "为${scope}进入水印配置页面",
                    icon = Icons.Outlined.WaterDrop,
                    onClick = { onSelect(OverlayType.WATERMARK) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun RuleTypeOption(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuleSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconActive: Boolean,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    action: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    InfoCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onExpandedChange)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (iconActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                action()
                IconButton(onClick = onExpandedChange) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Outlined.KeyboardArrowUp
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) {
                            "收起${title}规则"
                        } else {
                            "展开${title}规则"
                        },
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 4.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun RuleConfigList(
    configs: List<OverlayConfig>,
    onEdit: (OverlayConfig) -> Unit,
    onDelete: (OverlayConfig) -> Unit,
) {
    if (configs.isEmpty()) {
        Text(
            "暂无规则，点击右下角添加",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        configs.forEach { config ->
            BoundConfigRow(
                config = config,
                onEdit = { onEdit(config) },
                onDelete = { onDelete(config) },
            )
        }
    }
}

@Composable
private fun BoundConfigRow(
    config: OverlayConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                StatusBadge("已绑定", true)
                Text(
                    configSummary(config),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "编辑${config.name}",
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "删除${config.name}",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun ruleSummary(configs: List<OverlayConfig>): String =
    if (configs.isEmpty()) "暂无配置" else "已配置 ${configs.size} 项"

private fun configSummary(config: OverlayConfig): String = when (config) {
    is BurnMaskConfig -> "透明度 ${(config.alpha * 100).toInt()}%"
    is TouchBlockConfig -> "${(config.rect.width * 100).toInt()} × ${(config.rect.height * 100).toInt()}%"
    is WatermarkConfig -> if (config.text.length > 12) {
        config.text.take(12) + "…"
    } else {
        config.text
    }
}
