package com.zhao.screentoolbox.system.update

import com.zhao.screentoolbox.domain.repository.UpdateClock
import javax.inject.Inject
import javax.inject.Singleton

/** 使用系统墙上时间执行更新检查节流。 */
@Singleton
class SystemUpdateClock @Inject constructor() : UpdateClock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
