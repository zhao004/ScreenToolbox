package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.zhao.screentoolbox.core.model.InstalledAppInfo
import com.zhao.screentoolbox.ui.MainUiState
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.ScreenHeader
import com.zhao.screentoolbox.ui.theme.appSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
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
                InfoCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
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
                    hasRule = state.rules.any { it.packageName == app.packageName },
                    onClick = { onConfigure(app) },
                )
            }
        }
    }
}

@Composable
private fun AppRuleRow(
    app: InstalledAppInfo,
    hasRule: Boolean,
    onClick: () -> Unit,
) {
    val semanticColors = MaterialTheme.appSemanticColors
    InfoCard(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "配置${app.label}" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app)
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasRule) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(semanticColors.success),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "已配置规则",
                        tint = semanticColors.onSuccess,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: InstalledAppInfo) {
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { 42.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(
        initialValue = null,
        app.packageName,
        iconSizePx,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap(iconSizePx, iconSizePx)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    val iconModifier = Modifier
        .size(42.dp)
        .clip(RoundedCornerShape(10.dp))
    val appIcon = icon

    if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = null,
            modifier = iconModifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = iconModifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.label.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
