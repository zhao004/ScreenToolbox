package com.zhao.screentoolbox.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import com.zhao.screentoolbox.BuildConfig
import com.zhao.screentoolbox.ui.MainUiState
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.PermissionRow
import com.zhao.screentoolbox.ui.components.ScreenHeader
import com.zhao.screentoolbox.ui.components.StatusBadge
import com.zhao.screentoolbox.ui.theme.DangerRed

@Composable
fun SettingsScreen(
    state: MainUiState,
    onServiceToggle: (Boolean) -> Unit,
    onAutoAssociationToggle: (Boolean) -> Unit,
    onBootToggle: (Boolean) -> Unit,
    onOverlayPermission: () -> Unit,
    onUsagePermission: () -> Unit,
    onNotificationPermission: () -> Unit,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmStop by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("设置", "权限、自动化和服务运行选项") }
        item {
            InfoCard {
                Column {
                    SettingsSwitchRow(
                        title = "启用悬浮窗服务",
                        description = "启动前台服务并加载所有已启用配置",
                        checked = state.settings.serviceEnabled,
                        onCheckedChange = onServiceToggle,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("运行状态", style = MaterialTheme.typography.titleMedium)
                        StatusBadge(
                            if (state.runtime.serviceRunning) "运行中" else "已停止",
                            state.runtime.serviceRunning,
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
                        title = "应用自动关联",
                        description = "进入已绑定应用时切换到对应规则",
                        checked = state.settings.autoAssociationEnabled,
                        onCheckedChange = onAutoAssociationToggle,
                    )
                    SettingsSwitchRow(
                        title = "开机自动恢复",
                        description = "系统允许时在开机后恢复服务",
                        checked = state.settings.autoStartOnBoot,
                        onCheckedChange = onBootToggle,
                    )
                }
            }
        }
        item {
            InfoCard(containerColor = DangerRed.copy(alpha = 0.08f)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("紧急操作", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "立即停止服务并移除所有系统悬浮窗。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { confirmStop = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    ) {
                        Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                        Text("关闭全部悬浮窗")
                    }
                }
            }
        }
        item {
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("兼容性说明", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "非触摸悬浮窗可能受 Android 12 及以上触摸安全策略影响；开机恢复和前台识别也受系统及厂商后台限制。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "ScreenToolbox ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("关闭全部悬浮窗") },
            text = { Text("前台服务会停止，所有正在显示的窗口将立即移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStop = false
                        onEmergencyStop()
                    },
                ) { Text("确认关闭") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("取消") }
            },
        )
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
