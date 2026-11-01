package com.zhao.screentoolbox.ui.feature.home

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
import com.zhao.screentoolbox.ui.components.ScreenHeader
import com.zhao.screentoolbox.ui.components.StatusBadge

@Composable
fun HomeScreen(
    state: MainUiState,
    onServiceToggle: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ruleControls = state.runtime.matchedRulePackage != null
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "屏幕工具箱",
                subtitle = "集中管理服务状态与前台应用规则",
            )
        }
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
                        Column {
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
    }
}
