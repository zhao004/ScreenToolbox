package com.zhao.screentoolbox.domain.repository

import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.AppSettings
import com.zhao.screentoolbox.core.model.OverlayConfig
import kotlinx.coroutines.flow.Flow

/** 悬浮窗配置的唯一持久化入口。 */
interface OverlayRepository {
    val overlays: Flow<List<OverlayConfig>>

    suspend fun upsert(config: OverlayConfig)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun delete(id: String)
}

/** 应用自动关联规则的持久化入口。 */
interface AppRuleRepository {
    val rules: Flow<List<AppRule>>

    suspend fun save(rule: AppRule): Long
    suspend fun delete(id: Long)
}

/** 全局设置使用 DataStore 保存，避免把瞬时运行状态写入数据库。 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setServiceEnabled(enabled: Boolean)
    suspend fun setAutoStartOnBoot(enabled: Boolean)
    suspend fun setFeatureEnabled(type: com.zhao.screentoolbox.core.model.OverlayType, enabled: Boolean)
}

/** 记录不含敏感数据的窗口与服务生命周期事件。 */
interface OperationLogRepository {
    suspend fun record(action: String, detail: String? = null)
}
