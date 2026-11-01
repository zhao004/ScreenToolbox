package com.zhao.screentoolbox.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_DISPLAY_NAME
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_PACKAGE
import com.zhao.screentoolbox.core.model.WatermarkConfig
import com.zhao.screentoolbox.ui.feature.apps.AppRuleEditorScreen
import com.zhao.screentoolbox.ui.feature.apps.AppsScreen
import com.zhao.screentoolbox.ui.feature.home.HomeScreen
import com.zhao.screentoolbox.ui.feature.overlays.WatermarkEditorScreen
import com.zhao.screentoolbox.ui.feature.settings.SettingsScreen
import java.util.UUID

private object Routes {
    const val Home = "home"
    const val Apps = "apps"
    const val Settings = "settings"
    const val WatermarkEditor = "watermark/{id}/{rulePackage}/{ruleLabel}/{attach}"
    const val AppRuleEditor = "rule/{packageName}/{label}"

    fun watermark(
        id: String,
        rulePackageName: String,
        ruleDisplayName: String,
        attachToRule: Boolean,
    ) = "watermark/${Uri.encode(id)}/${Uri.encode(rulePackageName)}/" +
        "${Uri.encode(ruleDisplayName)}/$attachToRule"

    fun rule(packageName: String, label: String) =
        "rule/${Uri.encode(packageName)}/${Uri.encode(label)}"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.Home, "首页", Icons.Outlined.Home),
    BottomDestination(Routes.Apps, "应用", Icons.Outlined.Apps),
    BottomDestination(Routes.Settings, "设置", Icons.Outlined.Settings),
)

@Composable
fun ScreenToolboxRoot(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshPermissions()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messageEvents.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(viewModel, navController) {
        viewModel.ruleActionEvents.collect { action ->
            when (action) {
                is RuleActionEvent.LaunchApplication -> {
                    val launchIntent = viewModel.launchIntent(action.packageName)
                    if (launchIntent == null) {
                        snackbar.showSnackbar("无法启动目标应用")
                    } else {
                        runCatching { context.startActivity(launchIntent) }
                            .onFailure { snackbar.showSnackbar("无法启动目标应用") }
                    }
                }

                is RuleActionEvent.OpenWatermark -> {
                    navController.navigate(
                        Routes.watermark(
                            id = action.configId,
                            rulePackageName = action.rulePackageName,
                            ruleDisplayName = action.ruleDisplayName,
                            attachToRule = action.attachToRule,
                        ),
                    )
                }
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    state = state,
                    onServiceToggle = viewModel::setServiceEnabled,
                    onDismissError = viewModel::clearRuntimeError,
                )
            }
            composable(Routes.Apps) {
                AppsScreen(
                    state = state,
                    onUsagePermission = {
                        context.startActivity(viewModel.usageAccessIntent())
                    },
                    onConfigure = { app ->
                        navController.navigate(Routes.rule(app.packageName, app.label))
                    },
                    onConfigureGlobal = {
                        navController.navigate(
                            Routes.rule(GLOBAL_RULE_PACKAGE, GLOBAL_RULE_DISPLAY_NAME),
                        )
                    },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    state = state,
                    onBootToggle = viewModel::setAutoStartOnBoot,
                    onOverlayPermission = {
                        context.startActivity(viewModel.overlaySettingsIntent())
                    },
                    onAccessibilityPermission = {
                        context.startActivity(viewModel.accessibilitySettingsIntent())
                    },
                    onUsagePermission = {
                        context.startActivity(viewModel.usageAccessIntent())
                    },
                    onNotificationPermission = requestNotificationPermission,
                )
            }
            composable(Routes.WatermarkEditor) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val rulePackageName = entry.arguments?.getString("rulePackage").orEmpty()
                val ruleDisplayName = entry.arguments?.getString("ruleLabel").orEmpty()
                val attachToRule = entry.arguments?.getString("attach").toBoolean()
                val existing = state.overlays.filterIsInstance<WatermarkConfig>().firstOrNull { it.id == id }
                val initial = if (id == "new") {
                    remember(id, rulePackageName) {
                        WatermarkConfig(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            enabled = true,
                        )
                    }
                } else {
                    existing
                }
                if (initial != null) {
                    WatermarkEditorScreen(
                        initial = initial,
                        onPreview = viewModel::previewOverlay,
                        onEndPreview = viewModel::endPreview,
                        onSave = { config ->
                            if (attachToRule) {
                                viewModel.saveOverlayForRule(
                                    config = config,
                                    rulePackageName = rulePackageName,
                                    ruleDisplayName = ruleDisplayName,
                                )
                            } else {
                                viewModel.upsertOverlay(config)
                            }
                        },
                        onBack = {
                            viewModel.cancelPendingRuleEdit()
                            navController.popBackStack()
                        },
                    )
                }
            }
            composable(Routes.AppRuleEditor) { entry ->
                val packageName = entry.arguments?.getString("packageName").orEmpty()
                val label = entry.arguments?.getString("label").orEmpty()
                val existing = state.rules.firstOrNull { it.packageName == packageName }
                val initial = existing ?: AppRule(
                    packageName = packageName,
                    displayName = label.ifBlank { packageName },
                )
                val ruleReferenceCounts = remember(state.rules) {
                    state.rules
                        .flatMap { it.configIds }
                        .groupingBy { it }
                        .eachCount()
                }
                AppRuleEditorScreen(
                    initial = initial,
                    overlays = state.overlays,
                    ruleReferenceCounts = ruleReferenceCounts,
                    onSave = viewModel::saveRule,
                    onAddRule = viewModel::addRuleConfig,
                    onEditConfig = viewModel::editRuleConfig,
                    onDeleteConfig = { config -> viewModel.deleteOverlay(config.id) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
