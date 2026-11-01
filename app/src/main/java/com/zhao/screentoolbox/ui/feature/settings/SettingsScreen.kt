package com.zhao.screentoolbox.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.zhao.screentoolbox.ui.MainUiState
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.PermissionRow
import com.zhao.screentoolbox.ui.components.ScreenHeader

@Composable
fun SettingsScreen(
    state: MainUiState,
    onBootToggle: (Boolean) -> Unit,
    onOverlayPermission: () -> Unit,
    onAccessibilityPermission: () -> Unit,
    onUsagePermission: () -> Unit,
    onNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("设置", "权限和自动化选项") }
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
