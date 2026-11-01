package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_PACKAGE
import com.zhao.screentoolbox.core.model.InstalledAppInfo
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.ui.MainUiState
import com.zhao.screentoolbox.ui.components.AppScreenContainer
import com.zhao.screentoolbox.ui.components.InfoCard
import com.zhao.screentoolbox.ui.components.ScreenHeader
import com.zhao.screentoolbox.ui.components.StatusBadge
import com.zhao.screentoolbox.ui.theme.AppSpacing
import com.zhao.screentoolbox.ui.theme.appSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用管理首页。
 *
 * 搜索值只用于本地列表筛选，规则状态仍完全来自 [MainUiState]。
 */
@Composable
fun AppsScreen(
    state: MainUiState,
    onUsagePermission: () -> Unit,
    onConfigure: (InstalledAppInfo) -> Unit,
    onConfigureGlobal: () -> Unit,
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
    val activeRulePackages = remember(state.rules, state.overlays) {
        val configById = state.overlays.associateBy { it.id }
        state.rules
            .asSequence()
            .filter { it.hasActiveConfig(configById) }
            .mapTo(mutableSetOf()) { it.packageName }
    }

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
            item {
                ScreenHeader("应用管理", "为指定应用或全局应用配置叠加规则")
            }
            if (!state.permissions.usageAccessGranted) {
                item {
                    UsagePermissionCard(onUsagePermission)
                }
            }
            item {
                AppSearchField(
                    query = query,
                    onQueryChange = { query = it },
                )
            }
            item {
                GlobalRuleRow(
                    hasRule = GLOBAL_RULE_PACKAGE in activeRulePackages,
                    onClick = onConfigureGlobal,
                )
            }
            if (state.loadingApps) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.xl),
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
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        ) {
                            Icon(Icons.Outlined.Apps, contentDescription = null)
                            Text(
                                text = "没有匹配的可启动应用",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.xs, start = AppSpacing.xxs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已安装应用",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = filteredApps.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(
                    items = filteredApps,
                    key = { _, app -> app.packageName },
                ) { _, app ->
                    AppRuleRow(
                        app = app,
                        hasRule = app.packageName in activeRulePackages,
                        onClick = { onConfigure(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UsagePermissionCard(onRequest: () -> Unit) {
    InfoCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(
                text = "需要使用情况访问",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "授权后应用规则才能识别前台应用，数据仅在本地判定。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onRequest) { Text("前往授权") }
        }
    }
}

@Composable
private fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "搜索应用" },
        placeholder = {
            Text(
                text = "搜索应用",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

private fun AppRule.hasActiveConfig(configById: Map<String, OverlayConfig>): Boolean {
    if (!enabled) return false
    return configIds.any { configId ->
        val config = configById[configId] ?: return@any false
        when (config.type) {
            OverlayType.BURN_MASK -> burnMaskEnabled
            OverlayType.TOUCH_BLOCK -> touchBlockEnabled
            OverlayType.WATERMARK -> watermarkEnabled
        }
    }
}

@Composable
private fun GlobalRuleRow(
    hasRule: Boolean,
    onClick: () -> Unit,
) {
    InfoCard(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "配置全局应用" },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("全局应用", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "所有前台应用叠加生效的规则",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasRule) {
                StatusBadge(text = "已启用", active = true)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val semanticColors = MaterialTheme.appSemanticColors
    InfoCard(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "配置${app.label}" },
        containerColor = if (hasRule) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            AppIcon(app)
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasRule) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(semanticColors.success),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "已配置规则",
                        tint = semanticColors.onSuccess,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
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
                context.packageManager
                    .getApplicationIcon(app.packageName)
                    .toBitmap(iconSizePx, iconSizePx)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    val iconModifier = Modifier
        .size(42.dp)
        .clip(MaterialTheme.shapes.medium)
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
                text = app.label.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
