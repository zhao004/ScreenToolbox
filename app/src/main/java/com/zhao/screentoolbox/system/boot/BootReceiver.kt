package com.zhao.screentoolbox.system.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhao.screentoolbox.domain.repository.SettingsRepository
import com.zhao.screentoolbox.overlay.OverlayService
import com.zhao.screentoolbox.system.permission.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 开机恢复仅作为用户主动开启后的尽力能力，不绕过系统后台启动限制。 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var permissionManager: PermissionManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.settings.first()
                if (settings.serviceEnabled &&
                    settings.autoStartOnBoot &&
                    permissionManager.canDrawOverlays()
                ) {
                    runCatching { OverlayService.start(context) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
