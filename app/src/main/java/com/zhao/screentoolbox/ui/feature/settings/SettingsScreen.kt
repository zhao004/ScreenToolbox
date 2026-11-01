package com.zhao.screentoolbox.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_PACKAGE
import com.zhao.screentoolbox.ui.MainUiState
import com.zhao.screentoolbox.ui.components.ErrorBanner
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.PermissionRow
import com.zhao.screentoolbox.ui.components.ScreenHeader
import com.zhao.screentoolbox.ui.components.StatusBadge

@Composable
fun SettingsScreen(
    state: MainUiState,
    onServiceToggle: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onBootToggle: (Boolean) -> Unit,
    onOverlayPermission: () -> Unit,
    onAccessibilityPermission: () -> Unit,
    onUsagePermission: () -> Unit,
    onNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ruleControls = state.runtime.matchedRulePackage != null
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("设置", "权限、前台服务与自动化选项") }
        state.runtime.lastError?.let { error ->
            item { ErrorBanner(text = error, onDismiss = onDismissError) }
        }
        item {
            InfoCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("前台服务", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "统一承载全部悬浮窗",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.settings.serviceEnabled,
                            onCheckedChange = onServiceToggle,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(
                            text = if (state.runtime.serviceRunning) "运行中" else "未运行",
                            active = state.runtime.serviceRunning,
                        )
                        StatusBadge(
                            text = "${state.runtime.activeConfigIds.size} 个窗口",
                            active = state.runtime.activeConfigIds.isNotEmpty(),
                        )
                    }
                    state.runtime.foregroundPackage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when {
                                state.runtime.matchedRulePackage == GLOBAL_RULE_PACKAGE ->
                                    "全局规则正在生效"

                                ruleControls -> "应用规则正在控制当前配置"
                                else -> "前台应用：$it"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.runtime.temporaryUnlockUntilEpochMs?.let { until ->
                        Spacer(Modifier.height(8.dp))
                        val seconds = ((until - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                        Text(
                            text = "防误触临时解除中，约 $seconds 秒后恢复",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            InfoCard {
                Column {
                    Text("系统权限", style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    PermissionRow("悬浮窗", state.permissions.overlayGranted, onOverlayPermission)
                    PermissionRow(
                        "无障碍服务（防烧屏穿透）",
                        state.permissions.accessibilityGranted,
                        onAccessibilityPermission,
                    )
                    PermissionRow("使用情况访问", state.permissions.usageAccessGranted, onUsagePermission)
                    PermissionRow("通知", state.permissions.notificationGranted, onNotificationPermission)
                }
            }
        }
        item {
            InfoCard {
                Column {
                    Text("自动化", style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingsSwitchRow(
                        title = "开机自动恢复",
                        description = "系统允许时在开机后恢复服务",
                        checked = state.settings.autoStartOnBoot,
                        onCheckedChange = onBootToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
