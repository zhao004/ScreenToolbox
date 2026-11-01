package com.zhao.screentoolbox.system.permission

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.zhao.screentoolbox.core.model.PermissionSnapshot
import com.zhao.screentoolbox.overlay.accessibility.OverlayAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 集中检查 Android 特殊权限，避免页面各自使用不同判定规则。 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun snapshot(): PermissionSnapshot = PermissionSnapshot(
        overlayGranted = canDrawOverlays(),
        accessibilityGranted = canUseAccessibilityOverlay(),
        usageAccessGranted = hasUsageAccess(),
        notificationGranted = canPostNotifications(),
    )

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun canUseAccessibilityOverlay(): Boolean {
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
            ?: return false
        return accessibilityManager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                val serviceInfo = service.resolveInfo.serviceInfo
                serviceInfo.packageName == context.packageName &&
                    serviceInfo.name == OverlayAccessibilityService::class.java.name
            }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun overlaySettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
