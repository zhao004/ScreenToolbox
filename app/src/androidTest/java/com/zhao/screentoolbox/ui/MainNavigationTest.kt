package com.zhao.screentoolbox.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    fun topLevelNavigationOpensOverlayScreen() {
        composeRule.onNodeWithText("屏幕工具箱").assertIsDisplayed()
        composeRule.onNodeWithText("悬浮窗").performClick()
        composeRule.onNodeWithText("创建、编辑并控制独立的系统窗口").assertIsDisplayed()
    }
}
