package com.zhao.screentoolbox.system.packageinfo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.zhao.screentoolbox.core.model.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class InstalledAppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 仅枚举带 Launcher 入口的应用，符合最小软件包可见性声明。 */
    suspend fun loadLauncherApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packageManager = context.packageManager
        val collator = Collator.getInstance(Locale.getDefault())
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        resolveInfos
            .asSequence()
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                    ?: packageName
                InstalledAppInfo(packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedWith { first, second -> collator.compare(first.label, second.label) }
            .toList()
    }
}
