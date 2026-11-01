package com.zhao.screentoolbox.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.AppSettings
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.InstalledAppInfo
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.core.model.PermissionSnapshot
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.domain.repository.AppRuleRepository
import com.zhao.screentoolbox.domain.repository.OverlayRepository
import com.zhao.screentoolbox.domain.repository.SettingsRepository
import com.zhao.screentoolbox.overlay.OverlayCommand
import com.zhao.screentoolbox.overlay.OverlayCommandBus
import com.zhao.screentoolbox.overlay.OverlayRuntimeState
import com.zhao.screentoolbox.overlay.OverlayRuntimeStore
import com.zhao.screentoolbox.overlay.OverlayService
import com.zhao.screentoolbox.system.packageinfo.InstalledAppRepository
import com.zhao.screentoolbox.system.permission.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val overlays: List<OverlayConfig> = emptyList(),
    val rules: List<AppRule> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val runtime: OverlayRuntimeState = OverlayRuntimeState(),
    val loadingApps: Boolean = true,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overlayRepository: OverlayRepository,
    private val appRuleRepository: AppRuleRepository,
    private val settingsRepository: SettingsRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val permissionManager: PermissionManager,
    private val runtimeStore: OverlayRuntimeStore,
    private val commandBus: OverlayCommandBus,
) : ViewModel() {
    private val installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val permissions = MutableStateFlow(permissionManager.snapshot())
    private val loadingApps = MutableStateFlow(true)
    private val messages = Channel<String>(Channel.BUFFERED)

    val messageEvents = messages.receiveAsFlow()

    private val coreState = combine(
        overlayRepository.overlays,
        appRuleRepository.rules,
        settingsRepository.settings,
        runtimeStore.state,
        permissions,
    ) { overlays, rules, settings, runtime, permissionState ->
        PartialState(overlays, rules, settings, runtime, permissionState)
    }

    val uiState: StateFlow<MainUiState> = combine(
        coreState,
        installedApps,
        loadingApps,
    ) { core, apps, loading ->
        MainUiState(
            overlays = core.overlays,
            rules = core.rules,
            installedApps = apps,
            settings = core.settings,
            permissions = core.permissions,
            runtime = core.runtime,
            loadingApps = loading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(permissions = permissionManager.snapshot()),
    )

    init {
        refreshApps()
    }

    fun refreshAll() {
        permissions.value = permissionManager.snapshot()
        refreshApps()
    }

    fun refreshPermissions() {
        permissions.value = permissionManager.snapshot()
    }

    /** Activity 回前台时恢复被系统回收但用户仍希望启用的前台服务。 */
    fun onAppResumed() {
        permissions.value = permissionManager.snapshot()
        viewModelScope.launch {
            val storedSettings = settingsRepository.settings.first()
            if (storedSettings.serviceEnabled &&
                permissionManager.canDrawOverlays()
            ) {
                runCatching { OverlayService.start(context) }
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            loadingApps.value = true
            runCatching { installedAppRepository.loadLauncherApps() }
                .onSuccess { installedApps.value = it }
                .onFailure { messages.send("读取应用列表失败") }
            loadingApps.value = false
        }
    }

    fun upsertOverlay(config: OverlayConfig) {
        viewModelScope.launch {
            runCatching { overlayRepository.upsert(config) }
                .onFailure { messages.send("保存悬浮窗配置失败") }
        }
    }

    fun deleteOverlay(id: String) {
        viewModelScope.launch {
            runCatching { overlayRepository.delete(id) }
                .onFailure { messages.send("删除悬浮窗失败") }
        }
    }

    fun toggleOverlay(config: OverlayConfig, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { overlayRepository.setEnabled(config.id, enabled) }
                .onFailure { messages.send("更新悬浮窗状态失败") }
        }
    }

    fun setFeatureEnabled(type: OverlayType, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFeatureEnabled(type, enabled)
        }
    }

    fun setAutoAssociationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoAssociationEnabled(enabled)
            if (enabled && !permissionManager.hasUsageAccess()) {
                messages.send("请先授权使用情况访问")
            }
        }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartOnBoot(enabled)
        }
    }

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setServiceEnabled(enabled)
            if (enabled) {
                if (!permissionManager.canDrawOverlays()) {
                    messages.send("请先授权悬浮窗权限")
                    return@launch
                }
                runCatching { OverlayService.start(context) }
                    .onFailure {
                        settingsRepository.setServiceEnabled(false)
                        messages.send("服务启动失败，系统可能限制了后台启动")
                    }
            } else {
                runCatching { OverlayService.stopAll(context) }
                runtimeStore.update { OverlayRuntimeState(serviceRunning = false) }
            }
        }
    }

    fun temporaryUnlock() {
        if (!permissionManager.canDrawOverlays()) {
            viewModelScope.launch { messages.send("请先授权悬浮窗权限") }
            return
        }
        viewModelScope.launch {
            runCatching { OverlayService.temporaryUnlock(context) }
                .onFailure { messages.send("临时解除启动失败") }
        }
    }

    fun emergencyStop() {
        viewModelScope.launch {
            settingsRepository.setServiceEnabled(false)
            runCatching { OverlayService.stopAll(context) }
            runtimeStore.update { OverlayRuntimeState(serviceRunning = false) }
        }
    }

    fun saveRule(rule: AppRule) {
        viewModelScope.launch {
            runCatching { appRuleRepository.save(rule) }
                .onSuccess { messages.send("应用规则保存成功") }
                .onFailure { messages.send("保存应用规则失败") }
        }
    }

    fun previewOverlay(config: OverlayConfig) {
        if (!permissionManager.canDrawOverlays()) {
            viewModelScope.launch { messages.send("请先授权悬浮窗权限后再预览") }
            return
        }
        if (!uiState.value.runtime.serviceRunning) {
            runCatching { OverlayService.start(context) }
        }
        commandBus.send(OverlayCommand.Preview(config))
    }

    /** 新建防烧屏或防误触时直接进入系统悬浮编辑，不跳转应用内编辑页。 */
    fun startRegionEditor(type: OverlayType) {
        if (type != OverlayType.BURN_MASK && type != OverlayType.TOUCH_BLOCK) return
        val config = when (type) {
            OverlayType.BURN_MASK -> BurnMaskConfig(
                id = UUID.randomUUID().toString(),
                name = "防烧屏遮罩",
                enabled = true,
                rect = NormalizedRect(0.15f, 0.2f, 0.7f, 0.2f),
            )

            OverlayType.TOUCH_BLOCK -> TouchBlockConfig(
                id = UUID.randomUUID().toString(),
                name = "防误触区域",
                enabled = true,
                rect = NormalizedRect(0.1f, 0.4f, 0.8f, 0.2f),
            )

            OverlayType.WATERMARK -> return
        }
        launchRegionEditor(config, operation = "新建")
    }

    /** 编辑现有防烧屏或防误触配置时复用系统悬浮编辑面板。 */
    fun startRegionEditor(config: OverlayConfig) {
        if (config.type != OverlayType.BURN_MASK && config.type != OverlayType.TOUCH_BLOCK) {
            return
        }
        launchRegionEditor(config, operation = "编辑")
    }

    private fun launchRegionEditor(config: OverlayConfig, operation: String) {
        if (!permissionManager.canDrawOverlays()) {
            messages.trySend("请先授权悬浮窗权限后再$operation")
            return
        }
        if (config.type == OverlayType.BURN_MASK &&
            !permissionManager.canUseAccessibilityOverlay()
        ) {
            messages.trySend("请先在设置中启用无障碍服务后再${operation}防烧屏遮罩")
            return
        }
        if (!uiState.value.runtime.serviceRunning) {
            runCatching { OverlayService.start(context) }
                .onFailure {
                    messages.trySend("悬浮编辑服务启动失败")
                    return
                }
        }
        if (!commandBus.send(OverlayCommand.StartRegionEditor(config))) {
            messages.trySend("无法启动悬浮编辑面板")
        }
    }

    fun endPreview() {
        commandBus.send(OverlayCommand.EndPreview)
    }

    fun clearRuntimeError() {
        runtimeStore.clearError()
    }

    fun overlaySettingsIntent(): Intent = permissionManager.overlaySettingsIntent()
    fun accessibilitySettingsIntent(): Intent = permissionManager.accessibilitySettingsIntent()
    fun usageAccessIntent(): Intent = permissionManager.usageAccessSettingsIntent()

    private data class PartialState(
        val overlays: List<OverlayConfig>,
        val rules: List<AppRule>,
        val settings: AppSettings,
        val runtime: OverlayRuntimeState,
        val permissions: PermissionSnapshot,
    )
}
