package com.zhao.screentoolbox.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhao.screentoolbox.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigationOmitsOverlayPageAndShowsGlobalApplicationEntry() {
        composeRule.onNodeWithText("屏幕工具箱").assertIsDisplayed()
        composeRule.onNodeWithText("悬浮窗").assertDoesNotExist()
        composeRule.onNodeWithText("应用").performClick()
        composeRule.onNodeWithContentDescription("配置全局应用").assertIsDisplayed()
        composeRule.onNodeWithText("为指定应用或全局应用配置叠加规则").assertIsDisplayed()
    }

    @Test
    fun settingsOmitsGlobalAssociationAndQuickActions() {
        composeRule.onNodeWithText("设置").performClick()

        composeRule.onNodeWithText("权限和自动化选项").assertIsDisplayed()
        composeRule.onNodeWithText("开机自动恢复").assertIsDisplayed()
        composeRule.onNodeWithText("应用自动关联").assertDoesNotExist()
        composeRule.onNodeWithText("快捷操作").assertDoesNotExist()
        composeRule.onNodeWithText("解除 30 秒").assertDoesNotExist()
        composeRule.onNodeWithText("关闭全部").assertDoesNotExist()
    }
}
