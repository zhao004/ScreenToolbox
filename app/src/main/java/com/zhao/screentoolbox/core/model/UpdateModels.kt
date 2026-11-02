package com.zhao.screentoolbox.core.model

/** ScreenToolbox 固定 GitHub Release 地址的唯一来源。 */
internal object ScreenToolboxReleaseSource {
    const val CHECKSUM_URL =
        "https://github.com/zhao004/ScreenToolbox/releases/latest/download/SHA256SUMS.txt"
    const val USER_AGENT = "ScreenToolbox-Android-Updater"

    fun releasePageUrl(tag: String): String =
        "https://github.com/zhao004/ScreenToolbox/releases/tag/$tag"

    fun apkDownloadUrl(tag: String, fileName: String): String =
        "https://github.com/zhao004/ScreenToolbox/releases/download/$tag/$fileName"
}

/** GitHub Release 中与当前设备匹配的更新包。 */
data class AvailableUpdate(
    val tag: String,
    val versionName: String,
    val abi: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
) {
    /** 发布说明页面由固定仓库和受校验的版本标签构造。 */
    val releasePageUrl: String
        get() = ScreenToolboxReleaseSource.releasePageUrl(tag)
}

/** 已提交给系统下载器的更新任务。 */
data class PendingUpdateDownload(
    val downloadId: Long,
    val update: AvailableUpdate,
)

/** 下载器单次进度快照，总大小未知时 [totalBytes] 为空。 */
data class UpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
) {
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}
