package com.zhao.screentoolbox.ui.feature.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayType
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
    fun onlyBoundConfigsAreShownAndCanBeEditedOrDeleted() {
        val initial = AppRule(
            packageName = "com.example.target",
            displayName = "测试应用",
            configIds = setOf("mask-1"),
        )
        val boundMask = mask("mask-1", "主防烧屏遮罩")
        val unboundMask = mask("mask-2", "未绑定遮罩")
        var edited: OverlayConfig? = null
        var deleted: OverlayConfig? = null

        composeRule.setContent {
            ScreenToolboxTheme(darkTheme = false) {
                AppRuleEditorScreen(
                    initial = initial,
                    overlays = listOf(boundMask, unboundMask),
                    ruleReferenceCounts = mapOf("mask-1" to 1),
                    onRuleChanged = {},
                    onAddRule = { _, _ -> },
                    onEditConfig = { _, config -> edited = config },
                    onDeleteConfig = { deleted = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("启用规则").assertIsDisplayed()
        composeRule.onNodeWithText("启用自动规则").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("添加规则").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("展开防烧屏规则").performClick()
        composeRule.onNodeWithText(boundMask.name).assertIsDisplayed()
        composeRule.onNodeWithText(unboundMask.name).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("编辑${boundMask.name}").performClick()
        composeRule.runOnIdle {
            assertEquals(boundMask, edited)
        }

        composeRule.onNodeWithContentDescription("删除${boundMask.name}").performClick()
        composeRule.onNodeWithText("删除“${boundMask.name}”").assertIsDisplayed()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.runOnIdle {
            assertEquals(boundMask, deleted)
        }
    }

    @Test
    fun addDialogOffersAllRuleTypesAndPassesCurrentDraft() {
        val initial = AppRule(
            packageName = "com.example.target",
            displayName = "测试应用",
            burnMaskEnabled = false,
        )
        var selectedRule: AppRule? = null
        var selectedType: OverlayType? = null

        composeRule.setContent {
            ScreenToolboxTheme(darkTheme = false) {
                AppRuleEditorScreen(
                    initial = initial,
                    overlays = emptyList(),
                    ruleReferenceCounts = emptyMap(),
                    onRuleChanged = {},
                    onAddRule = { rule, type ->
                        selectedRule = rule
                        selectedType = type
                    },
                    onEditConfig = { _, _ -> },
                    onDeleteConfig = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("添加规则").performClick()
        composeRule.onNodeWithText("添加规则").assertIsDisplayed()
        composeRule.onNodeWithText("为当前应用创建遮罩区域并进入悬浮编辑").assertIsDisplayed()
        composeRule.onNodeWithText("为当前应用创建触摸拦截区域并进入悬浮编辑").assertIsDisplayed()
        composeRule.onNodeWithText("为当前应用进入水印配置页面").assertIsDisplayed()
        composeRule.onNodeWithText("为当前应用进入水印配置页面").performClick()

        composeRule.runOnIdle {
            assertEquals(initial, selectedRule)
            assertEquals(OverlayType.WATERMARK, selectedType)
        }
    }

    @Test
    fun switchesSaveImmediatelyWithoutTopSaveAction() {
        val initial = AppRule(
            packageName = "com.example.target",
            displayName = "测试应用",
        )
        var changed: AppRule? = null

        composeRule.setContent {
            ScreenToolboxTheme(darkTheme = false) {
                AppRuleEditorScreen(
                    initial = initial,
                    overlays = emptyList(),
                    ruleReferenceCounts = emptyMap(),
                    onRuleChanged = { changed = it },
                    onAddRule = { _, _ -> },
                    onEditConfig = { _, _ -> },
                    onDeleteConfig = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("保存").assertDoesNotExist()
        composeRule.onAllNodes(isToggleable()).onFirst().performClick()
        composeRule.runOnIdle {
            assertEquals(initial.copy(enabled = false), changed)
        }
    }

    private fun mask(id: String, name: String) = BurnMaskConfig(
        id = id,
        name = name,
        enabled = true,
        rect = NormalizedRect.FullScreen,
    )
}
