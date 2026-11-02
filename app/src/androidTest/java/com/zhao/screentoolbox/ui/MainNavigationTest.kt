package com.zhao.screentoolbox.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun navigationStartsOnAppsAndOmitsHomeAndOverlayPages() {
        composeRule.onNodeWithText("应用管理").assertIsDisplayed()
        composeRule.onNodeWithText("悬浮窗").assertDoesNotExist()
        composeRule.onNodeWithText("首页").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("配置全局应用").assertIsDisplayed()
        composeRule.onNodeWithText("为指定应用或全局应用配置叠加规则").assertIsDisplayed()
    }

    @Test
    fun settingsOmitsGlobalAssociationAndQuickActions() {
        composeRule.onNode(hasText("设置") and isSelectable()).performClick()

        composeRule.onNodeWithText("权限、前台服务、自动化与版本更新").assertIsDisplayed()
        composeRule.onNodeWithText("前台服务").assertIsDisplayed()
        composeRule.onNodeWithText("开机自动恢复").assertIsDisplayed()
        composeRule.onNodeWithText("软件更新").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("应用自动关联").assertDoesNotExist()
        composeRule.onNodeWithText("快捷操作").assertDoesNotExist()
        composeRule.onNodeWithText("解除 30 秒").assertDoesNotExist()
        composeRule.onNodeWithText("关闭全部").assertDoesNotExist()
    }
}
