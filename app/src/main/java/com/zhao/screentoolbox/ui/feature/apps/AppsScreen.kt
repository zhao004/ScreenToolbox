package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhao.screentoolbox.core.model.InstalledAppInfo
import com.zhao.screentoolbox.ui.MainUiState
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.ScreenHeader
import com.zhao.screentoolbox.ui.components.StatusBadge
import com.zhao.screentoolbox.ui.theme.BrandBlue

@Composable
fun AppsScreen(
    state: MainUiState,
    onUsagePermission: () -> Unit,
    onConfigure: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(query, state.installedApps) {
        if (query.isBlank()) {
            state.installedApps
        } else {
            state.installedApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("应用管理", "为指定应用绑定独立悬浮窗规则")
        }
        if (!state.permissions.usageAccessGranted) {
            item {
                InfoCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("需要使用情况访问", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "授权后应用规则才能识别前台应用。该权限仅用于本地判定，不会上传数据。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onUsagePermission) { Text("前往授权") }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索应用") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
            )
        }
        if (state.loadingApps) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (filteredApps.isEmpty()) {
            item {
                InfoCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Apps, contentDescription = null)
                        Text("没有匹配的可启动应用")
                    }
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                AppRuleRow(
                    app = app,
                    hasRule = state.rules.any { it.packageName == app.packageName && it.enabled },
                    onClick = { onConfigure(app) },
                )
            }
        }
        item {
            Text(
                "多窗口或部分厂商系统无法始终准确判断前台应用，规则状态以最近一次前台事件为准。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRuleRow(
    app: InstalledAppInfo,
    hasRule: Boolean,
    onClick: () -> Unit,
) {
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.label.take(1).uppercase(),
                    color = BrandBlue,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasRule) StatusBadge("已关联", true)
            OutlinedButton(onClick = onClick) { Text("配置") }
        }
    }
}
