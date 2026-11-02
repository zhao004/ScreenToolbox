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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_PACKAGE
import com.zhao.screentoolbox.ui.MainUiState
import com.zhao.screentoolbox.ui.components.AppScreenContainer
import com.zhao.screentoolbox.ui.components.ErrorBanner
import com.zhao.screentoolbox.ui.components.IconTile
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.PermissionRow
import com.zhao.screentoolbox.ui.components.ScreenHeader
import com.zhao.screentoolbox.ui.components.StatusBadge
import com.zhao.screentoolbox.ui.theme.AppSpacing
import com.zhao.screentoolbox.ui.update.UpdatePhase
import com.zhao.screentoolbox.ui.update.UpdateUiState

/**
 * 设置页集中展示服务运行态、系统权限与自动化开关。
 *
 * 权限未授予时由对应行直接进入系统设置，页面不缓存或补写权限状态。
 */
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
    updateState: UpdateUiState,
    onCheckUpdates: () -> Unit,
    onShowUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ruleControls = state.runtime.matchedRulePackage != null
    AppScreenContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppSpacing.md,
                top = AppSpacing.lg,
                end = AppSpacing.md,
                bottom = AppSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            item { ScreenHeader("设置", "权限、前台服务、自动化与版本更新") }
            state.runtime.lastError?.let { error ->
                item { ErrorBanner(text = error, onDismiss = onDismissError) }
            }
            item {
                ForegroundServiceCard(
                    state = state,
                    ruleControls = ruleControls,
                    onServiceToggle = onServiceToggle,
                )
            }
            item {
                PermissionsCard(
                    state = state,
                    onOverlayPermission = onOverlayPermission,
                    onAccessibilityPermission = onAccessibilityPermission,
                    onUsagePermission = onUsagePermission,
                    onNotificationPermission = onNotificationPermission,
                )
            }
            item {
                AutomationCard(
                    checked = state.settings.autoStartOnBoot,
                    onCheckedChange = onBootToggle,
                )
            }
            item {
                SoftwareUpdateCard(
                    state = updateState,
                    onCheckUpdates = onCheckUpdates,
                    onShowDialog = onShowUpdateDialog,
                )
            }
        }
    }
}

@Composable
private fun ForegroundServiceCard(
    state: MainUiState,
    ruleControls: Boolean,
    onServiceToggle: (Boolean) -> Unit,
) {
    InfoCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                IconTile(
                    imageVector = Icons.Outlined.Layers,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                )
                Column(Modifier.weight(1f)) {
                    Text("前台服务", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "统一承载全部悬浮窗",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.settings.serviceEnabled,
                    onCheckedChange = onServiceToggle,
                )
            }
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
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
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = when {
                        state.runtime.matchedRulePackage == GLOBAL_RULE_PACKAGE ->
                            "全局规则正在生效"

                        ruleControls -> "应用规则正在控制当前配置"
                        else -> "前台应用：$it"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            state.runtime.temporaryUnlockUntilEpochMs?.let { until ->
                Spacer(Modifier.height(AppSpacing.xs))
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

@Composable
private fun PermissionsCard(
    state: MainUiState,
    onOverlayPermission: () -> Unit,
    onAccessibilityPermission: () -> Unit,
    onUsagePermission: () -> Unit,
    onNotificationPermission: () -> Unit,
) {
    InfoCard {
        Column {
            Text("系统权限", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "未授权的权限会在这里提示",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppSpacing.xs),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            PermissionRow(
                title = "悬浮窗",
                granted = state.permissions.overlayGranted,
                onRequest = onOverlayPermission,
                icon = Icons.Outlined.Layers,
            )
            PermissionRow(
                title = "无障碍服务（防烧屏穿透）",
                granted = state.permissions.accessibilityGranted,
                onRequest = onAccessibilityPermission,
                icon = Icons.Outlined.TouchApp,
            )
            PermissionRow(
                title = "使用情况访问",
                granted = state.permissions.usageAccessGranted,
                onRequest = onUsagePermission,
                icon = Icons.Outlined.Insights,
            )
            PermissionRow(
                title = "通知",
                granted = state.permissions.notificationGranted,
                onRequest = onNotificationPermission,
                icon = Icons.Outlined.Notifications,
            )
        }
    }
}

@Composable
private fun AutomationCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    InfoCard {
        Column {
            Text("自动化", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppSpacing.xs),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                IconTile(
                    imageVector = Icons.Outlined.RestartAlt,
                    size = 40.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text("开机自动恢复", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "系统允许时在开机后恢复服务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}

@Composable
private fun SoftwareUpdateCard(
    state: UpdateUiState,
    onCheckUpdates: () -> Unit,
    onShowDialog: () -> Unit,
) {
    val update = state.availableUpdate
    val phase = state.phase
    val statusText = when {
        state.isDebugBuild -> "开发版本不检查更新"
        phase == UpdatePhase.CHECKING -> "正在检查更新"
        phase == UpdatePhase.UP_TO_DATE -> "当前已是最新版本"
        phase == UpdatePhase.AVAILABLE && update != null -> "发现新版本 ${update.versionName}"
        phase == UpdatePhase.DOWNLOADING && update != null -> "正在下载 ${update.versionName}"
        phase == UpdatePhase.VERIFYING && update != null -> "正在校验 ${update.versionName}"
        phase == UpdatePhase.READY_TO_INSTALL && update != null ->
            "${update.versionName} 已准备安装"

        phase == UpdatePhase.ERROR -> state.errorMessage ?: "更新任务失败"
        else -> null
    }
    val statusActive = phase == UpdatePhase.UP_TO_DATE ||
        phase == UpdatePhase.AVAILABLE ||
        phase == UpdatePhase.READY_TO_INSTALL
    val statusWarning = phase == UpdatePhase.ERROR
    val actionText = when {
        state.isDebugBuild -> ""
        phase == UpdatePhase.CHECKING -> "检查中"
        update != null -> "查看详情"
        phase == UpdatePhase.ERROR -> "重试"
        else -> "检查更新"
    }

    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                IconTile(imageVector = Icons.Outlined.SystemUpdateAlt)
                Column(Modifier.weight(1f)) {
                    Text("软件更新", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "当前版本 ${state.currentVersionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(
                    text = when {
                        state.isDebugBuild -> "开发版"
                        phase == UpdatePhase.ERROR -> "需要处理"
                        phase == UpdatePhase.DOWNLOADING -> "下载中"
                        phase == UpdatePhase.VERIFYING -> "校验中"
                        phase == UpdatePhase.READY_TO_INSTALL -> "待安装"
                        phase == UpdatePhase.AVAILABLE -> "可更新"
                        else -> "稳定版"
                    },
                    active = statusActive,
                    warning = statusWarning,
                )
            }
            statusText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusWarning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (
                phase == UpdatePhase.DOWNLOADING ||
                phase == UpdatePhase.VERIFYING
            ) {
                val fraction = state.downloadProgress?.fraction
                if (phase == UpdatePhase.VERIFYING || fraction == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppSpacing.xs),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            if (!state.isDebugBuild) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            when {
                                update != null && phase != UpdatePhase.CHECKING -> onShowDialog()
                                phase == UpdatePhase.ERROR -> onCheckUpdates()
                                phase == UpdatePhase.IDLE ||
                                    phase == UpdatePhase.UP_TO_DATE -> onCheckUpdates()
                            }
                        },
                        enabled = phase != UpdatePhase.CHECKING && actionText.isNotEmpty(),
                    ) {
                        Text(actionText)
                    }
                }
            }
        }
    }
}
