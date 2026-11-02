package com.zhao.screentoolbox.system.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zhao.screentoolbox.core.model.AvailableUpdate
import com.zhao.screentoolbox.domain.repository.UpdateEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** APK 在交给系统安装器前必须通过的本地校验结果。 */
sealed interface ApkVerificationResult {
    data object Valid : ApkVerificationResult
    data class Invalid(val reason: String) : ApkVerificationResult
}

/** 负责 APK 完整性、版本、包名、签名校验及系统安装入口。 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environment: UpdateEnvironment,
) {
    private val packageManager = context.packageManager

    suspend fun verify(
        update: AvailableUpdate,
        file: File,
    ): ApkVerificationResult = withContext(Dispatchers.IO) {
        try {
            if (!UpdateStorage.isManagedFile(context, file) || !file.isFile) {
                return@withContext ApkVerificationResult.Invalid("更新文件不存在")
            }
            if (file.length() <= 0L || file.length() > MAX_APK_BYTES) {
                return@withContext ApkVerificationResult.Invalid("更新文件大小无效")
            }
            val actualSha256 = sha256(file)
            if (!actualSha256.equals(update.sha256, ignoreCase = true)) {
                return@withContext ApkVerificationResult.Invalid("更新包校验失败，已删除损坏文件")
            }

            val archiveInfo = packageArchiveInfo(file)
                ?: return@withContext ApkVerificationResult.Invalid("无法读取更新包信息")
            if (archiveInfo.packageName != context.packageName) {
                return@withContext ApkVerificationResult.Invalid("更新包应用标识不匹配")
            }
            if (archiveInfo.versionName != update.versionName) {
                return@withContext ApkVerificationResult.Invalid("更新包版本信息不匹配")
            }
            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }
            if (archiveVersionCode <= environment.currentVersionCode) {
                return@withContext ApkVerificationResult.Invalid("更新包版本未高于当前版本")
            }

            val archiveCertificates = signingCertificateDigests(archiveInfo)
            val installedInfo = installedPackageInfo()
                ?: return@withContext ApkVerificationResult.Invalid("无法读取当前应用签名")
            val installedCertificates = signingCertificateDigests(installedInfo)
            if (
                archiveCertificates.isEmpty() ||
                installedCertificates.isEmpty() ||
                archiveCertificates.intersect(installedCertificates).isEmpty()
            ) {
                return@withContext ApkVerificationResult.Invalid("更新包签名与当前应用不一致")
            }
            ApkVerificationResult.Valid
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ApkVerificationResult.Invalid("无法校验更新包，请重新下载")
        }
    }

    fun canRequestPackageInstalls(): Boolean = packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        android.net.Uri.parse("package:${context.packageName}"),
    )

    fun createInstallIntent(file: File): Intent {
        require(UpdateStorage.isManagedFile(context, file)) {
            "更新文件不在受管理目录中"
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            file,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply {
                clipData = ClipData.newRawUri("ScreenToolbox update", uri)
            }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun signingCertificateDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }.orEmpty()
        return signatures
            .map { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .toHex()
            }
            .toSet()
    }

    private fun packageArchiveInfo(file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PACKAGE_INFO_FLAGS),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_INFO_FLAGS.toInt())
        }

    private fun installedPackageInfo(): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PACKAGE_INFO_FLAGS),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PACKAGE_INFO_FLAGS.toInt())
        }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        HEX_DIGITS[(byte.toInt() ushr 4) and 0x0f].toString() +
            HEX_DIGITS[byte.toInt() and 0x0f]
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val MAX_APK_BYTES = 100L * 1024L * 1024L
        val PACKAGE_INFO_FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES.toLong()
        }
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
