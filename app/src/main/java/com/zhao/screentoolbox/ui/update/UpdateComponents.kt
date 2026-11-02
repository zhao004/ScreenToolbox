package com.zhao.screentoolbox.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zhao.screentoolbox.ui.theme.AppSpacing
import java.util.Locale

/** 以单一 Material 3 Dialog 呈现检查和下载的完整更新流程。 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    if (!state.dialogVisible || state.phase == UpdatePhase.IDLE) return
    val update = state.availableUpdate
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (state.phase) {
                    UpdatePhase.AVAILABLE -> "发现新版本"
                    UpdatePhase.DOWNLOADING -> "正在下载更新"
                    UpdatePhase.VERIFYING -> "正在校验更新"
                    UpdatePhase.READY_TO_INSTALL -> "更新已准备就绪"
                    UpdatePhase.ERROR -> "更新未完成"
                    UpdatePhase.CHECKING -> "正在检查更新"
                    UpdatePhase.UP_TO_DATE -> "当前已是最新版本"
                    UpdatePhase.IDLE -> ""
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                if (update != null) {
                    Text(
                        text = "当前版本 ${state.currentVersionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "新版本 ${update.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                when (state.phase) {
                    UpdatePhase.AVAILABLE -> {
                        Text(
                            text = "更新包将从 GitHub Releases 下载，完成后由系统确认安装。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = onOpenReleasePage,
                            enabled = update != null,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                            )
                            Text(
                                text = "查看版本说明",
                                modifier = Modifier.padding(start = AppSpacing.xs),
                            )
                        }
                    }

                    UpdatePhase.DOWNLOADING -> DownloadProgressContent(state)
                    UpdatePhase.VERIFYING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(AppSpacing.xxs))
                            Text("正在校验文件完整性和应用签名")
                        }
                    }

                    UpdatePhase.READY_TO_INSTALL -> Text(
                        text = "更新包已通过完整性、版本和签名校验，可以交给系统安装。",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    UpdatePhase.ERROR -> Text(
                        text = state.errorMessage ?: "更新任务失败，请稍后重试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    UpdatePhase.CHECKING,
                    UpdatePhase.UP_TO_DATE,
                    UpdatePhase.IDLE,
                    -> Unit
                }
            }
        },
        confirmButton = {
            when (state.phase) {
                UpdatePhase.AVAILABLE -> {
                    TextButton(onClick = onDownload) { Text("下载更新") }
                }

                UpdatePhase.DOWNLOADING -> {
                    TextButton(onClick = onDismiss) { Text("后台继续") }
                }

                UpdatePhase.READY_TO_INSTALL -> {
                    TextButton(onClick = onInstall) { Text("立即安装") }
                }

                UpdatePhase.ERROR -> {
                    TextButton(onClick = if (update == null) onRetry else onDownload) {
                        Text(if (update == null) "重试" else "重新下载")
                    }
                }

                UpdatePhase.VERIFYING,
                UpdatePhase.CHECKING,
                UpdatePhase.UP_TO_DATE,
                UpdatePhase.IDLE,
                -> Unit
            }
        },
        dismissButton = {
            when (state.phase) {
                UpdatePhase.AVAILABLE,
                UpdatePhase.READY_TO_INSTALL,
                -> TextButton(onClick = onDismiss) { Text("稍后") }

                UpdatePhase.DOWNLOADING -> {
                    TextButton(onClick = onCancelDownload) { Text("取消下载") }
                }

                UpdatePhase.VERIFYING,
                UpdatePhase.ERROR,
                UpdatePhase.CHECKING,
                UpdatePhase.UP_TO_DATE,
                -> TextButton(onClick = onDismiss) { Text("关闭") }

                UpdatePhase.IDLE -> Unit
            }
        },
    )
}

@Composable
private fun DownloadProgressContent(state: UpdateUiState) {
    val progress = state.downloadProgress
    val fraction = progress?.fraction
    if (fraction == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        text = if (progress == null) {
            "正在连接下载服务器"
        } else if (progress.totalBytes != null) {
            "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}"
        } else {
            "已下载 ${formatBytes(progress.downloadedBytes)}"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024.0) return String.format(Locale.getDefault(), "%.1f KiB", kib)
    val mib = kib / 1024.0
    return String.format(Locale.getDefault(), "%.1f MiB", mib)
}
