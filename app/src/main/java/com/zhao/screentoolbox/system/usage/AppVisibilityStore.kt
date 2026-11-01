package com.zhao.screentoolbox.system.usage

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记录本应用 Activity 是否实际处于可见状态。
 *
 * UsageStats 在系统服务启动或通知操作期间可能短暂返回本应用包名，该信号不能
 * 直接当作前台应用，否则会错误隐藏全部窗口。
 */
@Singleton
class AppVisibilityStore @Inject constructor() {
    @Volatile
    var ownUiVisible: Boolean = false
}
