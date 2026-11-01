package com.zhao.screentoolbox.ui

import android.content.Context
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.AppSettings
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_DISPLAY_NAME
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_PACKAGE
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.core.model.PermissionSnapshot
import com.zhao.screentoolbox.domain.repository.AppRuleRepository
import com.zhao.screentoolbox.domain.repository.OverlayRepository
import com.zhao.screentoolbox.domain.repository.SettingsRepository
import com.zhao.screentoolbox.overlay.OverlayCommandBus
import com.zhao.screentoolbox.overlay.OverlayRuntimeStore
import com.zhao.screentoolbox.system.packageinfo.InstalledAppRepository
import com.zhao.screentoolbox.system.permission.PermissionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val overlayRepository = mockk<OverlayRepository>()
    private val appRuleRepository = mockk<AppRuleRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val installedAppRepository = mockk<InstalledAppRepository>()
    private val permissionManager = mockk<PermissionManager>()
    private val runtimeStore = OverlayRuntimeStore().apply {
        update { it.copy(serviceRunning = true) }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { overlayRepository.overlays } returns flowOf(emptyList())
        every { appRuleRepository.rules } returns flowOf(emptyList())
        every { settingsRepository.settings } returns flowOf(AppSettings())
        every { permissionManager.snapshot() } returns PermissionSnapshot(
            overlayGranted = true,
            accessibilityGranted = true,
            usageAccessGranted = true,
            notificationGranted = true,
        )
        every { permissionManager.canDrawOverlays() } returns true
        every { permissionManager.canUseAccessibilityOverlay() } returns true
        coEvery { installedAppRepository.loadLauncherApps() } returns emptyList()
        coEvery { appRuleRepository.save(any()) } returns 1L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startingRegionConfigurationDoesNotPersistRuleBeforeEditorSave() = runTest(dispatcher) {
        val viewModel = mainViewModel()

        viewModel.addRuleConfig(globalRule(), OverlayType.BURN_MASK)
        advanceUntilIdle()

        coVerify(exactly = 0) { appRuleRepository.save(any()) }
    }

    @Test
    fun openingWatermarkDoesNotPersistRuleBeforeConfigurationSave() = runTest(dispatcher) {
        val viewModel = mainViewModel()

        viewModel.addRuleConfig(globalRule(), OverlayType.WATERMARK)
        advanceUntilIdle()

        coVerify(exactly = 0) { appRuleRepository.save(any()) }
    }

    private fun mainViewModel() = MainViewModel(
        context = context,
        overlayRepository = overlayRepository,
        appRuleRepository = appRuleRepository,
        settingsRepository = settingsRepository,
        installedAppRepository = installedAppRepository,
        permissionManager = permissionManager,
        runtimeStore = runtimeStore,
        commandBus = OverlayCommandBus(),
    )

    private fun globalRule() = AppRule(
        packageName = GLOBAL_RULE_PACKAGE,
        displayName = GLOBAL_RULE_DISPLAY_NAME,
    )
}
