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
import com.zhao.screentoolbox.core.model.WatermarkConfig
import com.zhao.screentoolbox.core.model.isGlobalRule
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MainUiState(
    val overlays: List<OverlayConfig> = emptyList(),
    val rules: List<AppRule> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val runtime: OverlayRuntimeState = OverlayRuntimeState(),
    val loadingApps: Boolean = true,
)

/** 规则编辑器发起的跨页面或外部应用操作。 */
sealed interface RuleActionEvent {
    data class LaunchApplication(val packageName: String) : RuleActionEvent
    data class OpenWatermark(
        val configId: String,
        val rulePackageName: String,
        val ruleDisplayName: String,
        val attachToRule: Boolean,
    ) : RuleActionEvent
}

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
    private val ruleActions = Channel<RuleActionEvent>(Channel.BUFFERED)
    private val ruleUpdateMutex = Mutex()
    private var pendingWatermarkRule: AppRule? = null

    val messageEvents = messages.receiveAsFlow()
    val ruleActionEvents = ruleActions.receiveAsFlow()

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

    /** 实时保存已绑定配置的规则开关；空规则只在配置保存时一并创建。 */
    fun updateRule(rule: AppRule) {
        if (rule.configIds.isEmpty()) return
        viewModelScope.launch {
            val result = ruleUpdateMutex.withLock {
                runCatching { appRuleRepository.save(rule) }
            }
            if (result.isFailure) {
                messages.send("保存应用规则失败")
            }
        }
    }

    /** 为当前规则新增指定类型的配置，规则只在配置保存成功时落库。 */
    fun addRuleConfig(rule: AppRule, type: OverlayType) {
        pendingWatermarkRule = null
        when (type) {
            OverlayType.WATERMARK -> openWatermarkEditor(rule, configId = "new", attachToRule = true)
            OverlayType.BURN_MASK,
            OverlayType.TOUCH_BLOCK -> {
                val config = createRegionConfig(type, rule.displayName)
                startRegionEditor(
                    rule = rule,
                    config = config,
                    isNewConfig = true,
                    showOpacityControl = true,
                )
            }
        }
    }

    /** 编辑已绑定配置；规则开关随配置保存结果一起提交。 */
    fun editRuleConfig(rule: AppRule, config: OverlayConfig) {
        pendingWatermarkRule = null
        when (config) {
            is WatermarkConfig -> openWatermarkEditor(
                rule = rule,
                configId = config.id,
                attachToRule = false,
            )

            is BurnMaskConfig,
            is TouchBlockConfig -> startRegionEditor(
                rule = rule,
                config = config,
                isNewConfig = false,
                showOpacityControl = false,
            )
        }
    }

    /** 新水印保存成功后绑定发起添加的规则，失败时删除刚写入的孤立配置。 */
    fun saveOverlayForRule(
        config: OverlayConfig,
        rulePackageName: String,
        ruleDisplayName: String,
    ) {
        val pendingRule = pendingWatermarkRule?.takeIf {
            it.packageName == rulePackageName
        }
        pendingWatermarkRule = null
        viewModelScope.launch {
            val result = runCatching {
                overlayRepository.upsert(config)
                if (pendingRule != null) {
                    appRuleRepository.save(
                        pendingRule.copy(configIds = pendingRule.configIds + config.id),
                    )
                } else {
                    appRuleRepository.attachConfig(
                        packageName = rulePackageName,
                        displayName = ruleDisplayName,
                        configId = config.id,
                    )
                }
            }
            if (result.isFailure) {
                runCatching { overlayRepository.delete(config.id) }
                messages.send("保存规则配置失败")
            }
        }
    }

    /** 取消水印编辑时丢弃尚未提交的规则草稿。 */
    fun cancelPendingRuleEdit() {
        pendingWatermarkRule = null
    }

    fun launchIntent(packageName: String): Intent? =
        installedAppRepository.launchIntent(packageName)

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

    private fun createRegionConfig(type: OverlayType, displayName: String): OverlayConfig =
        when (type) {
            OverlayType.BURN_MASK -> BurnMaskConfig(
                id = UUID.randomUUID().toString(),
                name = "$displayName 防烧屏",
                enabled = true,
                rect = NormalizedRect(0.15f, 0.2f, 0.7f, 0.2f),
                alpha = 0.6f,
            )

            OverlayType.TOUCH_BLOCK -> TouchBlockConfig(
                id = UUID.randomUUID().toString(),
                name = "$displayName 防误触",
                enabled = true,
                rect = NormalizedRect(0.1f, 0.4f, 0.8f, 0.2f),
                alpha = 0.2f,
            )

            OverlayType.WATERMARK -> error("水印配置由独立编辑流程创建")
        }

    private fun openWatermarkEditor(
        rule: AppRule,
        configId: String,
        attachToRule: Boolean,
    ) {
        pendingWatermarkRule = rule.takeIf { attachToRule }
        val sent = ruleActions.trySend(
            RuleActionEvent.OpenWatermark(
                configId = configId,
                rulePackageName = rule.packageName,
                ruleDisplayName = rule.displayName,
                attachToRule = attachToRule,
            ),
        ).isSuccess
        if (!sent) {
            messages.trySend("无法打开水印配置页面")
        }
    }

    private fun startRegionEditor(
        rule: AppRule,
        config: OverlayConfig,
        isNewConfig: Boolean,
        showOpacityControl: Boolean,
    ) {
        if (config.type != OverlayType.BURN_MASK && config.type != OverlayType.TOUCH_BLOCK) {
            return
        }
        if (!permissionManager.canDrawOverlays()) {
            messages.trySend("请先授权悬浮窗权限后再编辑规则")
            return
        }
        if (config.type == OverlayType.BURN_MASK &&
            !permissionManager.canUseAccessibilityOverlay()
        ) {
            messages.trySend("请先在设置中启用无障碍服务后再编辑防烧屏规则")
            return
        }
        val targetPackage = rule.packageName.takeUnless { rule.isGlobalRule() }
        if (targetPackage != null && installedAppRepository.launchIntent(targetPackage) == null) {
            messages.trySend("目标应用不可启动，可能已被卸载")
            return
        }

        if (!startRegionEditorSession(
                config = config,
                showOpacityControl = showOpacityControl,
                pendingRule = rule,
                isNewConfig = isNewConfig,
            )
        ) {
            return
        }
        targetPackage?.let {
            if (ruleActions.trySend(RuleActionEvent.LaunchApplication(it)).isFailure) {
                messages.trySend("无法启动目标应用")
            }
        }
    }

    private fun startRegionEditorSession(
        config: OverlayConfig,
        showOpacityControl: Boolean,
        pendingRule: AppRule,
        isNewConfig: Boolean,
    ): Boolean {
        if (!uiState.value.runtime.serviceRunning) {
            val serviceResult = runCatching { OverlayService.start(context) }
            if (serviceResult.isFailure) {
                messages.trySend("悬浮编辑服务启动失败")
                return false
            }
        }
        val sent = commandBus.send(
            OverlayCommand.StartRegionEditor(
                config = config,
                showOpacityControl = showOpacityControl,
                pendingRule = pendingRule,
                isNewConfig = isNewConfig,
            ),
        )
        if (!sent) {
            messages.trySend("无法启动悬浮编辑面板")
            return false
        }
        return true
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
