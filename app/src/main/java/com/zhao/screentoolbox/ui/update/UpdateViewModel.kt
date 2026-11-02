package com.zhao.screentoolbox.ui.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhao.screentoolbox.core.model.AvailableUpdate
import com.zhao.screentoolbox.core.model.PendingUpdateDownload
import com.zhao.screentoolbox.core.model.UpdateDownloadProgress
import com.zhao.screentoolbox.data.update.UpdateCheckException
import com.zhao.screentoolbox.domain.repository.UpdateCheckResult
import com.zhao.screentoolbox.domain.repository.UpdateClock
import com.zhao.screentoolbox.domain.repository.UpdateEnvironment
import com.zhao.screentoolbox.domain.repository.UpdatePreferencesRepository
import com.zhao.screentoolbox.domain.repository.UpdateRepository
import com.zhao.screentoolbox.system.update.ApkDownloadManager
import com.zhao.screentoolbox.system.update.ApkDownloadState
import com.zhao.screentoolbox.system.update.ApkInstaller
import com.zhao.screentoolbox.system.update.ApkVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.semver4j.Semver

/** 设置页与更新对话框共享的单向更新状态。 */
enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    VERIFYING,
    READY_TO_INSTALL,
    ERROR,
}

data class UpdateUiState(
    val currentVersionName: String,
    val automaticChecksEnabled: Boolean = true,
    val lastCheckAtEpochMs: Long = 0L,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val availableUpdate: AvailableUpdate? = null,
    val downloadProgress: UpdateDownloadProgress? = null,
    val errorMessage: String? = null,
    val dialogVisible: Boolean = false,
    val isDebugBuild: Boolean = false,
)

/** 需要由 Activity 执行的系统跳转或一次性提示。 */
sealed interface UpdateEvent {
    data class OpenInstaller(val intent: Intent) : UpdateEvent
    data class OpenUnknownSourcesSettings(val intent: Intent) : UpdateEvent
    data class OpenReleasePage(val url: String) : UpdateEvent
    data class Message(val text: String) : UpdateEvent
}

/**
 * 协调网络检查、系统下载、APK 校验和安装权限流程。
 *
 * 下载本身由 DownloadManager 持有；进程重建后根据 DataStore 中的任务 ID
 * 恢复观察，不需要重新下载。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val preferencesRepository: UpdatePreferencesRepository,
    private val downloadManager: ApkDownloadManager,
    private val apkInstaller: ApkInstaller,
    private val environment: UpdateEnvironment,
    private val clock: UpdateClock,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        UpdateUiState(
            currentVersionName = environment.currentVersionName,
            isDebugBuild = environment.isDebugBuild,
        ),
    )
    val uiState: StateFlow<UpdateUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<UpdateEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val checkMutex = Mutex()
    private var downloadJob: Job? = null
    private var pendingDownload: PendingUpdateDownload? = null
    private var readyFile: File? = null
    private var waitingForUnknownSources = false
    private var initialPreferencesLoaded = false

    init {
        observePreferences()
    }

    /** Activity 首次组合时调用；是否联网由持久化节流和自动检查开关决定。 */
    fun checkAutomatically() {
        requestCheck(manual = false)
    }

    fun checkManually() {
        requestCheck(manual = true)
    }

    fun setAutomaticChecksEnabled(enabled: Boolean) {
        if (mutableUiState.value.isDebugBuild) return
        val previous = mutableUiState.value.automaticChecksEnabled
        mutableUiState.update { it.copy(automaticChecksEnabled = enabled) }
        viewModelScope.launch {
            runCatching {
                preferencesRepository.setAutomaticChecksEnabled(enabled)
            }.onFailure {
                mutableUiState.update { it.copy(automaticChecksEnabled = previous) }
                sendMessage("保存自动检查设置失败")
            }
        }
    }

    fun downloadUpdate() {
        val update = mutableUiState.value.availableUpdate ?: return
        if (mutableUiState.value.phase in BUSY_PHASES) return
        mutableUiState.update {
            it.copy(
                phase = UpdatePhase.DOWNLOADING,
                downloadProgress = UpdateDownloadProgress(0L, null),
                errorMessage = null,
                dialogVisible = true,
            )
        }
        viewModelScope.launch {
            var downloadId: Long? = null
            try {
                downloadManager.cleanupExcept(update.fileName)
                downloadId = downloadManager.enqueue(update)
                val pending = PendingUpdateDownload(downloadId, update)
                pendingDownload = pending
                readyFile = null
                preferencesRepository.setPendingDownload(pending)
                startObservingDownload(pending)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                downloadId?.let { id ->
                    runCatching { downloadManager.cancel(id, update) }
                }
                markDownloadError(update, safeErrorMessage(exception))
            }
        }
    }

    fun cancelDownload() {
        val update = mutableUiState.value.availableUpdate ?: return
        val pending = pendingDownload
        downloadJob?.cancel()
        downloadJob = null
        mutableUiState.update {
            it.copy(
                phase = UpdatePhase.AVAILABLE,
                downloadProgress = null,
                errorMessage = null,
                dialogVisible = true,
            )
        }
        viewModelScope.launch {
            if (pending != null) {
                runCatching { downloadManager.cancel(pending.downloadId, pending.update) }
            }
            runCatching { preferencesRepository.clearPendingDownload() }
            pendingDownload = null
            readyFile = null
            downloadManager.cleanupExcept()
            if (update.tag != mutableUiState.value.availableUpdate?.tag) {
                mutableUiState.update { it.copy(phase = UpdatePhase.IDLE) }
            }
        }
    }

    fun requestInstall() {
        if (mutableUiState.value.phase != UpdatePhase.READY_TO_INSTALL) return
        val update = mutableUiState.value.availableUpdate ?: return
        val file = readyFile ?: return
        mutableUiState.update {
            it.copy(phase = UpdatePhase.VERIFYING, errorMessage = null, dialogVisible = true)
        }
        viewModelScope.launch {
            when (val verification = apkInstaller.verify(update, file)) {
                ApkVerificationResult.Valid -> {
                    if (!apkInstaller.canRequestPackageInstalls()) {
                        waitingForUnknownSources = true
                        mutableUiState.update {
                            it.copy(phase = UpdatePhase.READY_TO_INSTALL)
                        }
                        eventChannel.send(
                            UpdateEvent.OpenUnknownSourcesSettings(
                                apkInstaller.unknownSourcesSettingsIntent(),
                            ),
                        )
                    } else {
                        waitingForUnknownSources = false
                        launchInstaller(file)
                    }
                }

                is ApkVerificationResult.Invalid -> {
                    discardInvalidDownload(update, verification.reason)
                }
            }
        }
    }

    /** 用户从“安装未知应用”设置返回后继续此前被权限中断的安装。 */
    fun onUnknownSourcesReturned() {
        if (!waitingForUnknownSources) return
        if (apkInstaller.canRequestPackageInstalls()) {
            waitingForUnknownSources = false
            requestInstall()
        } else {
            sendMessage("请允许屏幕工具箱安装未知应用")
        }
    }

    fun showUpdateDialog() {
        if (mutableUiState.value.phase != UpdatePhase.IDLE) {
            mutableUiState.update { it.copy(dialogVisible = true) }
        }
    }

    fun dismissDialog() {
        mutableUiState.update { it.copy(dialogVisible = false) }
    }

    fun openReleasePage() {
        val url = mutableUiState.value.availableUpdate?.releasePageUrl ?: return
        eventChannel.trySend(UpdateEvent.OpenReleasePage(url))
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.preferences
                .catch {
                    sendMessage("读取更新设置失败")
                }
                .collect { preferences ->
                    mutableUiState.update {
                        it.copy(
                            automaticChecksEnabled = preferences.automaticChecksEnabled,
                            lastCheckAtEpochMs = preferences.lastCheckAtEpochMs,
                        )
                    }
                    val pending = preferences.pendingDownload
                    if (!initialPreferencesLoaded) {
                        initialPreferencesLoaded = true
                        if (pending == null) {
                            downloadManager.cleanupExcept()
                        }
                    }
                    if (pending != null) {
                        restorePendingDownload(pending)
                    }
                }
        }
    }

    private suspend fun restorePendingDownload(pending: PendingUpdateDownload) {
        if (pendingDownload?.downloadId == pending.downloadId) return
        if (isAlreadyInstalled(pending.update)) {
            downloadManager.cleanupExcept()
            preferencesRepository.clearPendingDownload()
            pendingDownload = null
            readyFile = null
            mutableUiState.update {
                it.copy(
                    phase = UpdatePhase.IDLE,
                    availableUpdate = null,
                    downloadProgress = null,
                    errorMessage = null,
                    dialogVisible = false,
                )
            }
            return
        }
        pendingDownload = pending
        mutableUiState.update {
            it.copy(
                phase = UpdatePhase.DOWNLOADING,
                availableUpdate = pending.update,
                downloadProgress = UpdateDownloadProgress(0L, null),
                errorMessage = null,
                dialogVisible = true,
            )
        }
        startObservingDownload(pending)
    }

    private fun requestCheck(manual: Boolean) {
        if (environment.isDebugBuild) {
            if (manual) sendMessage("开发版本不检查更新")
            return
        }
        if (mutableUiState.value.phase in BUSY_PHASES) {
            if (manual) sendMessage("更新任务正在进行中")
            return
        }
        if (!checkMutex.tryLock()) return
        viewModelScope.launch {
            try {
                val preferences = preferencesRepository.preferences.first()
                val now = clock.nowEpochMs()
                if (!manual && !preferences.automaticChecksEnabled) return@launch
                val elapsedSinceLastCheck = now - preferences.lastCheckAtEpochMs
                if (
                    !manual &&
                    elapsedSinceLastCheck >= 0L &&
                    elapsedSinceLastCheck < AUTO_CHECK_INTERVAL_MS
                ) {
                    return@launch
                }
                if (preferences.pendingDownload != null) {
                    restorePendingDownload(preferences.pendingDownload)
                    return@launch
                }

                runCatching { preferencesRepository.recordCheckAttempt(now) }
                mutableUiState.update {
                    it.copy(
                        phase = UpdatePhase.CHECKING,
                        lastCheckAtEpochMs = now,
                        errorMessage = null,
                        dialogVisible = false,
                    )
                }
                when (
                    val result = updateRepository.checkForUpdate(
                        currentVersionName = environment.currentVersionName,
                        supportedAbis = environment.supportedAbis,
                    )
                ) {
                    is UpdateCheckResult.Available -> {
                        mutableUiState.update {
                            it.copy(
                                phase = UpdatePhase.AVAILABLE,
                                availableUpdate = result.update,
                                downloadProgress = null,
                                errorMessage = null,
                                dialogVisible = true,
                            )
                        }
                    }

                    UpdateCheckResult.UpToDate -> {
                        mutableUiState.update {
                            it.copy(
                                phase = UpdatePhase.UP_TO_DATE,
                                availableUpdate = null,
                                downloadProgress = null,
                                errorMessage = null,
                                dialogVisible = false,
                            )
                        }
                        if (manual) sendMessage("当前已是最新版本")
                    }

                    is UpdateCheckResult.UnsupportedDevice -> {
                        markCheckError(result.reason, manual)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                markCheckError(safeErrorMessage(exception), manual)
            } finally {
                checkMutex.unlock()
            }
        }
    }

    private fun startObservingDownload(pending: PendingUpdateDownload) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            try {
                downloadManager
                    .observe(pending.downloadId, pending.update)
                    .collect { state ->
                        when (state) {
                            is ApkDownloadState.Running -> {
                                mutableUiState.update {
                                    it.copy(
                                        phase = UpdatePhase.DOWNLOADING,
                                        availableUpdate = pending.update,
                                        downloadProgress = state.progress,
                                        errorMessage = null,
                                    )
                                }
                            }

                            is ApkDownloadState.Completed -> verifyDownloadedFile(
                                update = pending.update,
                                file = state.file,
                            )

                            is ApkDownloadState.Failed -> {
                                downloadManager.cancel(pending.downloadId, pending.update)
                                preferencesRepository.clearPendingDownload()
                                pendingDownload = null
                                readyFile = null
                                markDownloadError(pending.update, state.message)
                            }
                        }
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                markDownloadError(pending.update, safeErrorMessage(exception))
            }
        }
    }

    private suspend fun verifyDownloadedFile(update: AvailableUpdate, file: File) {
        mutableUiState.update {
            it.copy(
                phase = UpdatePhase.VERIFYING,
                availableUpdate = update,
                downloadProgress = null,
                errorMessage = null,
                dialogVisible = true,
            )
        }
        when (val verification = apkInstaller.verify(update, file)) {
            ApkVerificationResult.Valid -> {
                downloadManager.cleanupExcept(file.name)
                readyFile = file
                mutableUiState.update {
                    it.copy(
                        phase = UpdatePhase.READY_TO_INSTALL,
                        errorMessage = null,
                        dialogVisible = true,
                    )
                }
            }

            is ApkVerificationResult.Invalid -> discardInvalidDownload(
                update = update,
                reason = verification.reason,
            )
        }
    }

    private suspend fun discardInvalidDownload(update: AvailableUpdate, reason: String) {
        pendingDownload?.let { pending ->
            runCatching { downloadManager.cancel(pending.downloadId, pending.update) }
        }
        runCatching { downloadManager.cleanupExcept() }
        runCatching { preferencesRepository.clearPendingDownload() }
        pendingDownload = null
        readyFile = null
        markDownloadError(update, reason)
    }

    private suspend fun launchInstaller(file: File) {
        val intent = try {
            apkInstaller.createInstallIntent(file)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            mutableUiState.update {
                it.copy(
                    phase = UpdatePhase.ERROR,
                    errorMessage = safeErrorMessage(exception),
                    dialogVisible = true,
                )
            }
            return
        }
        mutableUiState.update {
            it.copy(phase = UpdatePhase.READY_TO_INSTALL, dialogVisible = true)
        }
        eventChannel.send(UpdateEvent.OpenInstaller(intent))
    }

    private fun markCheckError(message: String, manual: Boolean) {
        mutableUiState.update {
            it.copy(
                phase = UpdatePhase.ERROR,
                errorMessage = message,
                dialogVisible = false,
            )
        }
        if (manual) sendMessage(message)
    }

    private fun markDownloadError(update: AvailableUpdate, message: String) {
        mutableUiState.update {
            it.copy(
                phase = UpdatePhase.ERROR,
                availableUpdate = update,
                downloadProgress = null,
                errorMessage = message,
                dialogVisible = true,
            )
        }
    }

    private fun isAlreadyInstalled(update: AvailableUpdate): Boolean {
        val current = Semver.parse(environment.currentVersionName) ?: return false
        val candidate = Semver.parse(update.versionName) ?: return true
        return !candidate.isGreaterThan(current)
    }

    private fun safeErrorMessage(exception: Throwable): String = when (exception) {
        is UpdateCheckException -> exception.message ?: "检查更新失败，请稍后重试"
        is IOException -> exception.message ?: "更新任务失败，请稍后重试"
        else -> "更新任务失败，请稍后重试"
    }

    private fun sendMessage(text: String) {
        eventChannel.trySend(UpdateEvent.Message(text))
    }

    private companion object {
        const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        val BUSY_PHASES = setOf(
            UpdatePhase.CHECKING,
            UpdatePhase.DOWNLOADING,
            UpdatePhase.VERIFYING,
            UpdatePhase.READY_TO_INSTALL,
        )
    }
}
