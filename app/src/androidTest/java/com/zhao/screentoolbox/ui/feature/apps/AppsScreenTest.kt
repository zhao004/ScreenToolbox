package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_DISPLAY_NAME
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_PACKAGE
import com.zhao.screentoolbox.core.model.InstalledAppInfo
import com.zhao.screentoolbox.core.model.NormalizedRect
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
        val mask = BurnMaskConfig(
            id = "mask",
            name = "遮罩",
            enabled = true,
            rect = NormalizedRect.FullScreen,
        )
        val state = MainUiState(
            overlays = listOf(mask),
            rules = listOf(
                AppRule(
                    packageName = app.packageName,
                    displayName = app.label,
                    configIds = setOf(mask.id),
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
                    onConfigureGlobal = {},
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

    @Test
    fun emptyOrDisabledRulesDoNotShowConfiguredIcon() {
        val first = InstalledAppInfo("com.example.empty", "空规则应用")
        val second = InstalledAppInfo("com.example.disabled", "停用规则应用")
        val mask = BurnMaskConfig(
            id = "mask",
            name = "遮罩",
            enabled = true,
            rect = NormalizedRect.FullScreen,
        )
        val state = MainUiState(
            overlays = listOf(mask),
            rules = listOf(
                AppRule(
                    packageName = first.packageName,
                    displayName = first.label,
                ),
                AppRule(
                    packageName = second.packageName,
                    displayName = second.label,
                    enabled = false,
                    configIds = setOf(mask.id),
                ),
            ),
            installedApps = listOf(first, second),
            loadingApps = false,
        )

        composeRule.setContent {
            ScreenToolboxTheme(darkTheme = false) {
                AppsScreen(
                    state = state,
                    onUsagePermission = {},
                    onConfigure = {},
                    onConfigureGlobal = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "已配置规则",
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    @Test
    fun globalApplicationEntryStaysVisibleAndOpensGlobalRule() {
        val state = MainUiState(
            rules = listOf(
                AppRule(
                    packageName = GLOBAL_RULE_PACKAGE,
                    displayName = GLOBAL_RULE_DISPLAY_NAME,
                    configIds = setOf("watermark"),
                ),
            ),
            installedApps = emptyList(),
            loadingApps = false,
        )
        var globalOpened = false

        composeRule.setContent {
            ScreenToolboxTheme(darkTheme = false) {
                AppsScreen(
                    state = state,
                    onUsagePermission = {},
                    onConfigure = {},
                    onConfigureGlobal = { globalOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("全局应用").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("配置全局应用").performClick()
        composeRule.runOnIdle {
            assertEquals(true, globalOpened)
        }
    }
}
