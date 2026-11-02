package com.zhao.screentoolbox.system.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.zhao.screentoolbox.core.model.AvailableUpdate
import com.zhao.screentoolbox.core.model.ScreenToolboxReleaseSource
import com.zhao.screentoolbox.core.model.UpdateDownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** 系统下载器的可恢复状态。 */
sealed interface ApkDownloadState {
    data class Running(val progress: UpdateDownloadProgress) : ApkDownloadState
    data class Completed(val file: File) : ApkDownloadState
    data class Failed(val message: String) : ApkDownloadState
}

/** 使用 DownloadManager 承载 APK 下载，避免应用进程退出中断传输。 */
@Singleton
class ApkDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    suspend fun enqueue(update: AvailableUpdate): Long = withContext(Dispatchers.IO) {
        val updateDirectory = UpdateStorage.root(context)
            ?: throw IOException("当前设备存储不可用")
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            throw IOException("无法创建更新下载目录")
        }
        val destination = updateDirectory.resolve(update.fileName)
        if (destination.exists() && !destination.delete()) {
            throw IOException("无法替换旧的更新文件")
        }
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("屏幕工具箱 ${update.tag}")
            .setDescription("正在下载应用更新")
            .setMimeType(APK_MIME_TYPE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "${UpdateStorage.DIRECTORY_NAME}/${update.fileName}",
            )
            .addRequestHeader("User-Agent", ScreenToolboxReleaseSource.USER_AGENT)
        val manager = downloadManager ?: throw IOException("系统下载服务不可用")
        manager.enqueue(request)
    }

    fun observe(downloadId: Long, update: AvailableUpdate): Flow<ApkDownloadState> = flow {
        while (currentCoroutineContext().isActive) {
            val state = query(downloadId, update)
            emit(state)
            if (state !is ApkDownloadState.Running) break
            delay(PROGRESS_POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun cancel(downloadId: Long, update: AvailableUpdate) {
        withContext(Dispatchers.IO) {
            runCatching { downloadManager?.remove(downloadId) }
            runCatching { UpdateStorage.file(context, update)?.delete() }
        }
    }

    suspend fun cleanupExcept(fileName: String? = null) {
        withContext(Dispatchers.IO) {
            val root = UpdateStorage.root(context) ?: return@withContext
            root.listFiles()?.forEach { file ->
                if (fileName == null || file.name != fileName) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    private fun query(downloadId: Long, update: AvailableUpdate): ApkDownloadState {
        val cursor = downloadManager?.query(
            DownloadManager.Query().setFilterById(downloadId),
        ) ?: return ApkDownloadState.Failed("无法读取下载状态")
        return cursor.use {
            if (!it.moveToFirst()) {
                return@use ApkDownloadState.Failed("下载任务已丢失")
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloadedBytes = it
                .getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                .coerceAtLeast(0L)
            val totalBytes = it
                .getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                .takeIf { value -> value > 0L }
            when (status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                -> ApkDownloadState.Running(UpdateDownloadProgress(downloadedBytes, totalBytes))

                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = UpdateStorage.file(context, update)
                    if (file?.isFile == true && file.length() > 0L) {
                        ApkDownloadState.Completed(file)
                    } else {
                        ApkDownloadState.Failed("更新文件不完整，请重新下载")
                    }
                }

                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(
                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON),
                    )
                    ApkDownloadState.Failed(reasonMessage(reason))
                }

                else -> ApkDownloadState.Failed("无法识别下载状态")
            }
        }
    }

    private fun reasonMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足，无法下载更新"
        DownloadManager.ERROR_FILE_ERROR -> "无法写入更新文件"
        DownloadManager.ERROR_HTTP_DATA_ERROR,
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        DownloadManager.ERROR_UNKNOWN,
        -> "更新下载失败，请稍后重试"

        else -> "更新下载中断，请稍后重试"
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val PROGRESS_POLL_INTERVAL_MS = 500L
    }
}
