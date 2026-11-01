package com.zhao.screentoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhao.screentoolbox.ui.ScreenToolboxRoot
import com.zhao.screentoolbox.ui.theme.ScreenToolboxTheme
import com.zhao.screentoolbox.system.usage.AppVisibilityStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appVisibilityStore: AppVisibilityStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenToolboxTheme {
                ScreenToolboxRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appVisibilityStore.ownUiVisible = true
    }

    override fun onStop() {
        appVisibilityStore.ownUiVisible = false
        super.onStop()
    }
}
