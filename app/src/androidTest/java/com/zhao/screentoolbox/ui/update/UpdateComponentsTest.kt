package com.zhao.screentoolbox.ui.update

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhao.screentoolbox.core.model.AvailableUpdate
import com.zhao.screentoolbox.core.model.UpdateDownloadProgress
import com.zhao.screentoolbox.ui.theme.ScreenToolboxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun availableUpdateDialogOffersDownloadAndReleaseNotes() {
        var downloadClicked = false
        composeRule.setContent {
            ScreenToolboxTheme {
                UpdateDialog(
                    state = UpdateUiState(
                        currentVersionName = "1.0.0",
                        phase = UpdatePhase.AVAILABLE,
                        availableUpdate = update(),
                        dialogVisible = true,
                    ),
                    onDismiss = {},
                    onDownload = { downloadClicked = true },
                    onCancelDownload = {},
                    onInstall = {},
                    onRetry = {},
                    onOpenReleasePage = {},
                )
            }
        }

        composeRule.onNodeWithText("发现新版本").assertIsDisplayed()
        composeRule.onNodeWithText("新版本 1.1.0").assertIsDisplayed()
        composeRule.onNodeWithText("查看版本说明").assertIsDisplayed()
        composeRule.onNodeWithText("下载更新").performClick()

        assertTrue(downloadClicked)
    }

    @Test
    fun downloadingDialogShowsProgressAndCancellation() {
        composeRule.setContent {
            ScreenToolboxTheme {
                UpdateDialog(
                    state = UpdateUiState(
                        currentVersionName = "1.0.0",
                        phase = UpdatePhase.DOWNLOADING,
                        availableUpdate = update(),
                        downloadProgress = UpdateDownloadProgress(5L * 1024L * 1024L, 10L * 1024L * 1024L),
                        dialogVisible = true,
                    ),
                    onDismiss = {},
                    onDownload = {},
                    onCancelDownload = {},
                    onInstall = {},
                    onRetry = {},
                    onOpenReleasePage = {},
                )
            }
        }

        composeRule.onNodeWithText("正在下载更新").assertIsDisplayed()
        composeRule.onNodeWithText("5.0 MiB / 10.0 MiB").assertIsDisplayed()
        composeRule.onNodeWithText("取消下载").assertIsDisplayed()
    }

    private fun update() = AvailableUpdate(
        tag = "v1.1.0",
        versionName = "1.1.0",
        abi = "arm64-v8a",
        fileName = "ScreenToolbox-v1.1.0-arm64-v8a.apk",
        downloadUrl =
        "https://github.com/zhao004/ScreenToolbox/releases/download/" +
            "v1.1.0/ScreenToolbox-v1.1.0-arm64-v8a.apk",
        sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    )
}
