package com.zhao.screentoolbox.system.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zhao.screentoolbox.MainActivity
import com.zhao.screentoolbox.R
import com.zhao.screentoolbox.overlay.OverlayRuntimeState
import com.zhao.screentoolbox.overlay.OverlayService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 创建前台通知渠道，并集中构造服务通知及两个紧急操作。 */
@Singleton
class OverlayNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(state: OverlayRuntimeState): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val unlockIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_TEMPORARY_UNLOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (state.temporaryUnlockUntilEpochMs != null) {
            "防误触已临时解除 30 秒"
        } else {
            context.getString(R.string.notification_text, state.activeConfigIds.size)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, context.getString(R.string.notification_action_unlock), unlockIntent)
            .addAction(0, context.getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    fun update(state: OverlayRuntimeState) {
        ensureChannel()
        manager.notify(NOTIFICATION_ID, build(state))
    }

    companion object {
        const val CHANNEL_ID = "overlay_service"
        const val NOTIFICATION_ID = 1001
    }
}
