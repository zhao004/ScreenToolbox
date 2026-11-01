package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onDelete: ((Long) -> Unit)?,
    onBack: () -> Unit,
) {
    var draft by remember(initial.packageName) { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(initial.displayName) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        onSave(draft)
                        onBack()
                    },
            ) {
                    Icon(Icons.Outlined.Check, contentDescription = "保存")
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
                FeatureRuleHeader(
                    title = "防烧屏",
                    checked = draft.burnMaskEnabled,
                    onChecked = { draft = draft.copy(burnMaskEnabled = it) },
                )
            }
            items(overlays.filter { it.type == OverlayType.BURN_MASK }, key = { "burn-${it.id}" }) {
                ConfigSelectionRow(
                    config = it,
                    checked = it.id in draft.configIds,
                    enabled = draft.burnMaskEnabled,
                    onChecked = { checked -> draft = draft.withConfig(it.id, checked) },
                )
            }
            item {
                FeatureRuleHeader(
                    title = "防误触",
                    checked = draft.touchBlockEnabled,
                    onChecked = { draft = draft.copy(touchBlockEnabled = it) },
                )
            }
            items(overlays.filter { it.type == OverlayType.TOUCH_BLOCK }, key = { "touch-${it.id}" }) {
                ConfigSelectionRow(
                    config = it,
                    checked = it.id in draft.configIds,
                    enabled = draft.touchBlockEnabled,
                    onChecked = { checked -> draft = draft.withConfig(it.id, checked) },
                )
            }
            item {
                FeatureRuleHeader(
                    title = "全局水印",
                    checked = draft.watermarkEnabled,
                    onChecked = { draft = draft.copy(watermarkEnabled = it) },
                )
            }
            items(overlays.filter { it.type == OverlayType.WATERMARK }, key = { "watermark-${it.id}" }) {
                ConfigSelectionRow(
                    config = it,
                    checked = it.id in draft.configIds,
                    enabled = draft.watermarkEnabled,
                    onChecked = { checked -> draft = draft.withConfig(it.id, checked) },
                )
            }
            item {
                Button(
                    onClick = {
                        onSave(draft)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存规则")
                }
            }
            if (onDelete != null && initial.id > 0) {
                item {
                    Button(
                        onClick = {
                            onDelete(initial.id)
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除规则")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRuleHeader(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Switch(checked = checked, onCheckedChange = onChecked)
        }
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
