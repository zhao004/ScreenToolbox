package com.zhao.screentoolbox.domain.repository

import com.zhao.screentoolbox.core.model.AvailableUpdate
import com.zhao.screentoolbox.core.model.PendingUpdateDownload
import kotlinx.coroutines.flow.Flow

/** 更新检查结果只区分有新版本、已是最新和当前设备无兼容包。 */
sealed interface UpdateCheckResult {
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class UnsupportedDevice(val reason: String) : UpdateCheckResult
}

/** 从固定发布源读取并校验最新版本信息。 */
interface UpdateRepository {
    suspend fun checkForUpdate(
        currentVersionName: String,
        supportedAbis: List<String>,
    ): UpdateCheckResult
}

/** 更新检查与未完成下载的本地持久化状态。 */
data class UpdatePreferences(
    val automaticChecksEnabled: Boolean = true,
    val lastCheckAtEpochMs: Long = 0L,
    val pendingDownload: PendingUpdateDownload? = null,
)

/** 更新状态独立使用 DataStore，避免与悬浮窗运行设置耦合。 */
interface UpdatePreferencesRepository {
    val preferences: Flow<UpdatePreferences>

    suspend fun setAutomaticChecksEnabled(enabled: Boolean)
    suspend fun recordCheckAttempt(epochMs: Long)
    suspend fun setPendingDownload(pending: PendingUpdateDownload)
    suspend fun clearPendingDownload()
}

/** 为 ViewModel 提供可替换的构建版本和设备 ABI 信息。 */
interface UpdateEnvironment {
    val currentVersionName: String
    val currentVersionCode: Long
    val supportedAbis: List<String>
    val isDebugBuild: Boolean
}

/** 可替换时钟让 24 小时节流能够进行确定性测试。 */
fun interface UpdateClock {
    fun nowEpochMs(): Long
}
