package com.zhao.screentoolbox.system.update

import android.os.Build
import com.zhao.screentoolbox.BuildConfig
import com.zhao.screentoolbox.domain.repository.UpdateEnvironment
import javax.inject.Inject
import javax.inject.Singleton

/** 读取当前构建版本和设备运行时可安装的 ABI。 */
@Singleton
class AndroidUpdateEnvironment @Inject constructor() : UpdateEnvironment {
    override val currentVersionName: String = BuildConfig.VERSION_NAME
    override val currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()
    override val supportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()
    override val isDebugBuild: Boolean = BuildConfig.DEBUG
}
