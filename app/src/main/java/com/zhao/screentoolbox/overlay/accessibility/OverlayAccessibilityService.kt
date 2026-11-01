package com.zhao.screentoolbox.overlay.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.zhao.screentoolbox.overlay.OverlayCommand
import com.zhao.screentoolbox.overlay.OverlayCommandBus
import com.zhao.screentoolbox.overlay.OverlayManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 提供受信任的无障碍悬浮窗宿主。
 *
 * 服务不读取窗口内容，只负责创建可穿透触摸的完全不透明防烧屏窗口。
 */
@AndroidEntryPoint
class OverlayAccessibilityService : AccessibilityService() {
    @Inject lateinit var overlayManager: OverlayManager
    @Inject lateinit var commandBus: OverlayCommandBus

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager.attachAccessibilityHost(this)
        commandBus.send(OverlayCommand.AccessibilityHostChanged)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        detachOverlayHost()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        detachOverlayHost()
        super.onDestroy()
    }

    private fun detachOverlayHost() {
        overlayManager.detachAccessibilityHost()
        commandBus.send(OverlayCommand.AccessibilityHostChanged)
    }
}
