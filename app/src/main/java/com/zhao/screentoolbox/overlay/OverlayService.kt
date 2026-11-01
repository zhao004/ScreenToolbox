package com.zhao.screentoolbox.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.AppSettings
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.domain.repository.AppRuleRepository
import com.zhao.screentoolbox.domain.repository.SettingsRepository
import com.zhao.screentoolbox.domain.rules.AppRuleEngine
import com.zhao.screentoolbox.domain.repository.OverlayRepository
import com.zhao.screentoolbox.domain.repository.OperationLogRepository
import com.zhao.screentoolbox.system.notification.OverlayNotificationManager
import com.zhao.screentoolbox.system.permission.PermissionManager
import com.zhao.screentoolbox.system.usage.ForegroundAppMonitor
import com.zhao.screentoolbox.system.usage.AppVisibilityStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 悬浮窗运行服务。
 *
 * 服务持有窗口管理器和全部运行态，配置变化、前台应用变化、临时解除均通过同一
 * 协调流程重新计算期望窗口集合，避免业务模块各自创建窗口。
 */
@AndroidEntryPoint
class OverlayService : Service() {
    @Inject lateinit var overlayRepository: OverlayRepository
    @Inject lateinit var appRuleRepository: AppRuleRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appRuleEngine: AppRuleEngine
    @Inject lateinit var foregroundAppMonitor: ForegroundAppMonitor
    @Inject lateinit var appVisibilityStore: AppVisibilityStore
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var overlayManager: OverlayManager
    @Inject lateinit var runtimeStore: OverlayRuntimeStore
    @Inject lateinit var commandBus: OverlayCommandBus
    @Inject lateinit var notificationManager: OverlayNotificationManager
    @Inject lateinit var operationLogRepository: OperationLogRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            serviceScope.launch {
                reconcile()
                log("DISPLAY_CHANGED", "display=$displayId")
            }
        }
    }
    private var collectionStarted = false
    private var configs: List<OverlayConfig> = emptyList()
    private var rules: List<AppRule> = emptyList()
    private var settings = AppSettings()
    private var foregroundPackage: String? = null
    private var previewConfig: OverlayConfig? = null
    private var temporaryUnlockUntil: Long? = null
    private var unlockJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.ensureChannel()
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { startAsForeground(runtimeStore.state.value) }
            .onFailure {
                runtimeStore.update { state ->
                    state.copy(lastError = "前台服务启动失败: ${it.javaClass.simpleName}")
                }
                stopSelf()
                return START_NOT_STICKY
            }

        when (intent?.action) {
            ACTION_STOP_ALL -> {
                serviceScope.launch {
                    settingsRepository.setServiceEnabled(false)
                    operationLogRepository.record("EMERGENCY_STOP")
                    stopAllInternal()
                }
                return START_NOT_STICKY
            }

            ACTION_TEMPORARY_UNLOCK -> beginTemporaryUnlock()
        }

        runtimeStore.update { it.copy(serviceRunning = true, lastError = null) }
        if (!collectionStarted) {
            collectionStarted = true
            startCollectors()
        }
        updateNotification()
        reconcile()
        log("SERVICE_START")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCollectors() {
        serviceScope.launch {
            overlayRepository.overlays.collect {
                configs = it
                reconcile()
            }
        }
        serviceScope.launch {
            appRuleRepository.rules.collect {
                rules = it
                reconcile()
            }
        }
        serviceScope.launch {
            settingsRepository.settings.collect {
                settings = it
                reconcile()
            }
        }
        serviceScope.launch {
            commandBus.commands.collect { command ->
                when (command) {
                    is OverlayCommand.StartRegionEditor -> {
                        startRegionEditor(command.config)
                    }

                    is OverlayCommand.Preview -> {
                        overlayManager.removeEditorPanel()
                        previewConfig = command.config
                        reconcile()
                    }

                    OverlayCommand.EndPreview -> {
                        overlayManager.removeEditorPanel()
                        previewConfig = null
                        reconcile()
                        if (!settings.serviceEnabled) {
                            stopAllInternal()
                        }
                    }

                    OverlayCommand.TemporaryUnlock -> beginTemporaryUnlock()
                    OverlayCommand.EndTemporaryUnlock -> endTemporaryUnlock()
                }
            }
        }
        serviceScope.launch {
            while (isActive) {
                val rawPackage = runCatching {
                    foregroundAppMonitor.currentForegroundPackage()
                }.getOrNull()
                val nextPackage = when {
                    appVisibilityStore.ownUiVisible -> packageName
                    rawPackage == packageName -> null
                    else -> rawPackage
                }
                if (nextPackage != foregroundPackage) {
                    foregroundPackage = nextPackage
                    reconcile()
                }
                delay(FOREGROUND_POLL_INTERVAL_MS)
            }
        }
    }

    /** 创建区域预览和控制面板，后续样式变化仍通过同一服务协调。 */
    private fun startRegionEditor(config: OverlayConfig) {
        if (config.type != OverlayType.BURN_MASK && config.type != OverlayType.TOUCH_BLOCK) {
            return
        }
        previewConfig = config
        reconcile()
        overlayManager.showEditorPanel(
            config = config,
            onConfigChanged = { updated ->
                previewConfig = updated
                reconcile()
            },
            onSave = {
                serviceScope.launch { saveRegionEditor() }
            },
            onCancel = {
                serviceScope.launch { cancelRegionEditor() }
            },
        )
        log("REGION_EDITOR_START", config.type.name)
    }

    private suspend fun saveRegionEditor() {
        val config = previewConfig ?: return
        if (config.type != OverlayType.BURN_MASK && config.type != OverlayType.TOUCH_BLOCK) {
            return
        }
        runCatching { overlayRepository.upsert(config) }
            .onFailure {
                runtimeStore.update { state ->
                    state.copy(lastError = "保存悬浮编辑配置失败")
                }
                log("REGION_EDITOR_SAVE_FAILED", config.type.name)
                return
            }
        overlayManager.removeEditorPanel()
        previewConfig = null
        log("REGION_EDITOR_SAVE", config.type.name)
        if (settings.serviceEnabled) {
            reconcile()
        } else {
            stopAllInternal()
        }
    }

    private suspend fun cancelRegionEditor() {
        overlayManager.removeEditorPanel()
        previewConfig = null
        log("REGION_EDITOR_CANCEL")
        if (settings.serviceEnabled) {
            reconcile()
        } else {
            stopAllInternal()
        }
    }

    /** 重新计算期望窗口，窗口差异由 OverlayManager 统一落地。 */
    private fun reconcile() {
        if (!permissionManager.canDrawOverlays()) {
            previewConfig = null
            overlayManager.removeAll()
            runtimeStore.update {
                it.copy(
                    serviceRunning = true,
                    activeConfigIds = emptySet(),
                    foregroundPackage = foregroundPackage,
                    lastError = "悬浮窗权限已被关闭",
                )
            }
            updateNotification()
            return
        }
        if (settings.autoAssociationEnabled &&
            foregroundPackage != null &&
            !permissionManager.hasUsageAccess()
        ) {
            runtimeStore.update {
                it.copy(lastError = "使用情况访问权限不可用，无法识别前台应用")
            }
        }
        val temporaryActive = temporaryUnlockUntil?.let { System.currentTimeMillis() < it } == true
        val resolution = appRuleEngine.resolve(
            configs = configs,
            rules = rules,
            settings = settings,
            foregroundPackage = foregroundPackage,
            ownPackage = packageName,
            temporaryUnlockActive = temporaryActive,
            preview = previewConfig,
        )
        overlayManager.reconcile(
            resolution.activeConfigs,
            editingConfigId = previewConfig?.id,
        )
        runtimeStore.update {
            it.copy(
                serviceRunning = true,
                activeConfigIds = resolution.activeConfigs.mapTo(linkedSetOf()) { config -> config.id },
                foregroundPackage = foregroundPackage,
                matchedRulePackage = resolution.matchedRulePackage,
                ruleControlledTypes = resolution.ruleControlledTypes,
                temporaryUnlockUntilEpochMs = temporaryUnlockUntil,
            )
        }
        updateNotification()
    }

    private fun beginTemporaryUnlock() {
        val unlockUntil = SystemClock.elapsedRealtime() + TEMPORARY_UNLOCK_MS
        temporaryUnlockUntil = System.currentTimeMillis() + TEMPORARY_UNLOCK_MS
        log("TEMPORARY_UNLOCK_START")
        runtimeStore.update {
            it.copy(temporaryUnlockUntilEpochMs = temporaryUnlockUntil)
        }
        reconcile()
        unlockJob?.cancel()
        unlockJob = serviceScope.launch {
            delay(TEMPORARY_UNLOCK_MS)
            endTemporaryUnlock()
        }
        Log.i(TAG, "防误触临时解除至 $unlockUntil")
    }

    private fun endTemporaryUnlock() {
        unlockJob?.cancel()
        unlockJob = null
        temporaryUnlockUntil = null
        log("TEMPORARY_UNLOCK_END")
        runtimeStore.update { it.copy(temporaryUnlockUntilEpochMs = null) }
        reconcile()
    }

    private suspend fun stopAllInternal() {
        unlockJob?.cancel()
        unlockJob = null
        temporaryUnlockUntil = null
        previewConfig = null
        overlayManager.removeAll()
        runtimeStore.update {
            OverlayRuntimeState(serviceRunning = false)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun log(action: String, detail: String? = null) {
        serviceScope.launch { operationLogRepository.record(action, detail) }
    }

    private fun startAsForeground(state: OverlayRuntimeState) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            OverlayNotificationManager.NOTIFICATION_ID,
            notificationManager.build(state),
            type,
        )
    }

    private fun updateNotification() {
        notificationManager.update(runtimeStore.state.value)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch {
            reconcile()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unlockJob?.cancel()
        overlayManager.removeAll()
        displayManager.unregisterDisplayListener(displayListener)
        runtimeStore.update { state ->
            state.copy(
                serviceRunning = false,
                activeConfigIds = emptySet(),
                temporaryUnlockUntilEpochMs = null,
            )
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val FOREGROUND_POLL_INTERVAL_MS = 1_000L
        private const val TEMPORARY_UNLOCK_MS = 30_000L
        const val ACTION_START = "com.zhao.screentoolbox.action.START"
        const val ACTION_STOP_ALL = "com.zhao.screentoolbox.action.STOP_ALL"
        const val ACTION_TEMPORARY_UNLOCK = "com.zhao.screentoolbox.action.TEMPORARY_UNLOCK"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java).setAction(ACTION_START),
            )
        }

        fun stopAll(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_STOP_ALL),
            )
        }

        fun temporaryUnlock(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java).setAction(ACTION_TEMPORARY_UNLOCK),
            )
        }
    }
}
