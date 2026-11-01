package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.ui.theme.ScreenToolboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRuleEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rulePanelsExpandCollapseAndTopSaveActionPersists() {
        val initial = AppRule(
            packageName = "com.example.target",
            displayName = "测试应用",
            configIds = setOf("mask-1"),
        )
        val mask = BurnMaskConfig(
            id = "mask-1",
            name = "主防烧屏遮罩",
            enabled = true,
            rect = NormalizedRect.FullScreen,
        )
        var saved: AppRule? = null
        var backCount = 0

        composeRule.setContent {
            ScreenToolboxTheme(darkTheme = false) {
                AppRuleEditorScreen(
                    initial = initial,
                    overlays = listOf(mask),
                    onSave = { saved = it },
                    onBack = { backCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("防烧屏").assertIsDisplayed()
        composeRule.onNodeWithText("防误触").assertIsDisplayed()
        composeRule.onNodeWithText("全局水印").assertIsDisplayed()
        composeRule.onNodeWithText("保存规则").assertDoesNotExist()
        composeRule.onNodeWithText("删除规则").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("保存").assertIsDisplayed()
        composeRule.onNodeWithText(mask.name).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("展开防烧屏规则").performClick()
        composeRule.onNodeWithText(mask.name).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("收起防烧屏规则").performClick()
        composeRule.onNodeWithText(mask.name).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("保存").performClick()
        composeRule.runOnIdle {
            assertEquals(initial.packageName, saved?.packageName)
            assertEquals(0, backCount)
        }
    }

    @Test
    fun saveActionIsHiddenWithoutSelectedRules() {
        val initial = AppRule(
            packageName = "com.example.target",
            displayName = "测试应用",
        )
        val mask = BurnMaskConfig(
            id = "mask-1",
            name = "主防烧屏遮罩",
            enabled = true,
            rect = NormalizedRect.FullScreen,
        )

        composeRule.setContent {
            ScreenToolboxTheme(darkTheme = false) {
                AppRuleEditorScreen(
                    initial = initial,
                    overlays = listOf(mask),
                    onSave = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("保存").assertDoesNotExist()
    }
}
