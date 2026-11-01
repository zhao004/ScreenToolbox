package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BorderClear
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleEditorScreen(
    initial: AppRule,
    overlays: List<OverlayConfig>,
    onSave: (AppRule) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(initial.packageName) { mutableStateOf(initial) }
    var burnMaskExpanded by rememberSaveable(initial.packageName) { mutableStateOf(false) }
    var touchBlockExpanded by rememberSaveable(initial.packageName) { mutableStateOf(false) }
    var watermarkExpanded by rememberSaveable(initial.packageName) { mutableStateOf(false) }
    val burnMaskConfigs = overlays.filter { it.type == OverlayType.BURN_MASK }
    val touchBlockConfigs = overlays.filter { it.type == OverlayType.TOUCH_BLOCK }
    val watermarkConfigs = overlays.filter { it.type == OverlayType.WATERMARK }
    val hasSelectedRules = overlays.any { it.id in draft.configIds }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(initial.displayName) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (hasSelectedRules) {
                    IconButton(onClick = { onSave(draft) }) {
                        Icon(Icons.Outlined.Save, contentDescription = "保存")
                    }
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
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
                            Text("启用自动规则", style = MaterialTheme.typography.titleMedium)
                            Text(
                                initial.packageName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.copy(enabled = it) },
                        )
                    }
                }
            }
            item {
                ExpansionTile(
                    title = "防烧屏",
                    subtitle = ruleSummary(burnMaskConfigs, draft.configIds),
                    icon = Icons.Outlined.BorderClear,
                    iconActive = draft.burnMaskEnabled,
                    expanded = burnMaskExpanded,
                    onExpandedChange = { burnMaskExpanded = !burnMaskExpanded },
                    action = {
                        Switch(
                            checked = draft.burnMaskEnabled,
                            onCheckedChange = { draft = draft.copy(burnMaskEnabled = it) },
                        )
                    },
                ) {
                    RuleConfigList(
                        configs = burnMaskConfigs,
                        selectedConfigIds = draft.configIds,
                        enabled = draft.burnMaskEnabled,
                        onConfigChecked = { config, checked ->
                            draft = draft.withConfig(config.id, checked)
                        },
                    )
                }
            }
            item {
                ExpansionTile(
                    title = "防误触",
                    subtitle = ruleSummary(touchBlockConfigs, draft.configIds),
                    icon = Icons.Outlined.Layers,
                    iconActive = draft.touchBlockEnabled,
                    expanded = touchBlockExpanded,
                    onExpandedChange = { touchBlockExpanded = !touchBlockExpanded },
                    action = {
                        Switch(
                            checked = draft.touchBlockEnabled,
                            onCheckedChange = { draft = draft.copy(touchBlockEnabled = it) },
                        )
                    },
                ) {
                    RuleConfigList(
                        configs = touchBlockConfigs,
                        selectedConfigIds = draft.configIds,
                        enabled = draft.touchBlockEnabled,
                        onConfigChecked = { config, checked ->
                            draft = draft.withConfig(config.id, checked)
                        },
                    )
                }
            }
            item {
                ExpansionTile(
                    title = "全局水印",
                    subtitle = ruleSummary(watermarkConfigs, draft.configIds),
                    icon = Icons.Outlined.WaterDrop,
                    iconActive = draft.watermarkEnabled,
                    expanded = watermarkExpanded,
                    onExpandedChange = { watermarkExpanded = !watermarkExpanded },
                    action = {
                        Switch(
                            checked = draft.watermarkEnabled,
                            onCheckedChange = { draft = draft.copy(watermarkEnabled = it) },
                        )
                    },
                ) {
                    RuleConfigList(
                        configs = watermarkConfigs,
                        selectedConfigIds = draft.configIds,
                        enabled = draft.watermarkEnabled,
                        onConfigChecked = { config, checked ->
                            draft = draft.withConfig(config.id, checked)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpansionTile(
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
    selectedConfigIds: Set<String>,
    enabled: Boolean,
    onConfigChecked: (OverlayConfig, Boolean) -> Unit,
) {
    if (configs.isEmpty()) {
        Text(
            "请先在悬浮窗页面创建配置",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        configs.forEach { config ->
            ConfigSelectionRow(
                config = config,
                checked = config.id in selectedConfigIds,
                enabled = enabled,
                onChecked = { checked -> onConfigChecked(config, checked) },
            )
        }
    }
}

private fun ruleSummary(configs: List<OverlayConfig>, selectedConfigIds: Set<String>): String {
    val selectedCount = configs.count { it.id in selectedConfigIds }
    return if (configs.isEmpty()) {
        "暂无可用配置"
    } else {
        "已选 $selectedCount / ${configs.size} 项"
    }
}

@Composable
private fun ConfigSelectionRow(
    config: OverlayConfig,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChecked(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
        )
        Column(Modifier.weight(1f)) {
            Text(config.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (config.enabled) "全局配置已启用" else "全局配置已停用，规则仍可临时启用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusBadge(if (checked) "已选择" else "未选择", checked)
    }
}

private fun AppRule.withConfig(configId: String, selected: Boolean): AppRule {
    val next = configIds.toMutableSet()
    if (selected) next += configId else next -= configId
    return copy(configIds = next)
}
