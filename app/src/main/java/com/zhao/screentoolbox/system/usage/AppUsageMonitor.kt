package com.zhao.screentoolbox.system.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 使用 UsageStats 轮询最近的前台事件，单元测试可通过接口替换。 */
interface ForegroundAppMonitor {
    suspend fun currentForegroundPackage(): String?
}

@Singleton
class AppUsageMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForegroundAppMonitor {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    /**
     * 返回最近进入前台的应用包名。
     *
     * 查询窗口保留 6 秒以容忍厂商事件延迟；屏幕熄灭时直接返回 null，避免无意义轮询。
     */
    override suspend fun currentForegroundPackage(): String? {
        if (powerManager?.isInteractive != true) return null
        val manager = usageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - EVENT_LOOKBACK_MS
        return try {
            readLatestEvent(manager, beginTime, endTime)
                ?: readLatestUsage(manager, endTime)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun readLatestEvent(
        manager: UsageStatsManager,
        beginTime: Long,
        endTime: Long,
    ): String? {
        val events = manager.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForeground = isForegroundEvent(event.eventType)
            if (isForeground && event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }

    @Suppress("DEPRECATION")
    private fun isForegroundEvent(eventType: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            eventType == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }

    private fun readLatestUsage(manager: UsageStatsManager, endTime: Long): String? {
        return manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            endTime - FALLBACK_LOOKBACK_MS,
            endTime,
        )
            ?.asSequence()
            ?.filter { it.lastTimeUsed > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    private companion object {
        const val EVENT_LOOKBACK_MS = 6_000L
        const val FALLBACK_LOOKBACK_MS = 60_000L
    }
}
