package com.zhao.screentoolbox.overlay

import android.view.View
import android.view.WindowManager
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** 统一窗口控制接口，业务控制器不得绕过该契约直接使用 WindowManager。 */
interface OverlayController {
    fun show(id: String)
    fun hide(id: String)
    fun update(id: String)
    fun remove(id: String)
    fun removeAll()
}

data class OverlayEntry(
    val view: View,
    val params: WindowManager.LayoutParams,
    var config: OverlayConfig,
)

sealed interface OverlayCommand {
    data class Preview(val config: OverlayConfig) : OverlayCommand
    data object EndPreview : OverlayCommand
    data object TemporaryUnlock : OverlayCommand
    data object EndTemporaryUnlock : OverlayCommand
}

/**
 * 进程内命令通道。
 *
 * 服务启动前发送的预览命令会留在缓冲中，服务开始收集后立即消费，避免 UI 与
 * 前台服务启动时的竞态。
 */
@Singleton
class OverlayCommandBus @Inject constructor() {
    private val channel = Channel<OverlayCommand>(capacity = Channel.BUFFERED)
    val commands = channel.receiveAsFlow()

    fun send(command: OverlayCommand): Boolean = channel.trySend(command).isSuccess
}

data class OverlayGeometryEvent(
    val configId: String,
    val rect: NormalizedRect,
    val revision: Long,
)

data class OverlayRuntimeState(
    val serviceRunning: Boolean = false,
    val activeConfigIds: Set<String> = emptySet(),
    val foregroundPackage: String? = null,
    val matchedRulePackage: String? = null,
    val ruleControlledTypes: Set<OverlayType> = emptySet(),
    val temporaryUnlockUntilEpochMs: Long? = null,
    val lastError: String? = null,
    val geometryEvent: OverlayGeometryEvent? = null,
)

/** 运行状态只保存在内存中，配置持久化仍由 Repository 负责。 */
@Singleton
class OverlayRuntimeStore @Inject constructor() {
    private val mutableState = MutableStateFlow(OverlayRuntimeState())
    val state: StateFlow<OverlayRuntimeState> = mutableState.asStateFlow()
    private var geometryRevision = 0L

    fun update(transform: (OverlayRuntimeState) -> OverlayRuntimeState) {
        mutableState.value = transform(mutableState.value)
    }

    fun reportGeometry(configId: String, rect: NormalizedRect) {
        geometryRevision += 1
        update {
            it.copy(
                geometryEvent = OverlayGeometryEvent(
                    configId = configId,
                    rect = rect,
                    revision = geometryRevision,
                ),
            )
        }
    }

    fun clearError() = update { it.copy(lastError = null) }
}
