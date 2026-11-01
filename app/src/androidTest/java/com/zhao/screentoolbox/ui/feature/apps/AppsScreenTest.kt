package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.InstalledAppInfo
import com.zhao.screentoolbox.ui.MainUiState
import com.zhao.screentoolbox.ui.theme.ScreenToolboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingAppRowOpensConfigurationAndShowsConfiguredIcon() {
        val app = InstalledAppInfo(
            packageName = "com.example.target",
            label = "测试应用",
        )
        val state = MainUiState(
            rules = listOf(
                AppRule(
                    packageName = app.packageName,
                    displayName = app.label,
                    enabled = false,
                ),
            ),
            installedApps = listOf(app),
            loadingApps = false,
        )
        var configuredApp: InstalledAppInfo? = null

        composeRule.setContent {
            ScreenToolboxTheme(darkTheme = false) {
                AppsScreen(
                    state = state,
                    onUsagePermission = {},
                    onConfigure = { configuredApp = it },
                )
            }
        }

        composeRule.onNodeWithText("配置").assertDoesNotExist()
        composeRule.onNodeWithText("已关联").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            "已配置规则",
            useUnmergedTree = true,
        ).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("配置测试应用").performClick()
        composeRule.runOnIdle {
            assertEquals(app, configuredApp)
        }
    }
}
