package com.zhao.screentoolbox.ui.update

import com.zhao.screentoolbox.core.model.AvailableUpdate
import com.zhao.screentoolbox.core.model.PendingUpdateDownload
import com.zhao.screentoolbox.domain.repository.UpdateCheckResult
import com.zhao.screentoolbox.domain.repository.UpdateClock
import com.zhao.screentoolbox.domain.repository.UpdateEnvironment
import com.zhao.screentoolbox.domain.repository.UpdatePreferences
import com.zhao.screentoolbox.domain.repository.UpdatePreferencesRepository
import com.zhao.screentoolbox.domain.repository.UpdateRepository
import com.zhao.screentoolbox.system.update.ApkDownloadManager
import com.zhao.screentoolbox.system.update.ApkDownloadState
import com.zhao.screentoolbox.system.update.ApkInstaller
import com.zhao.screentoolbox.system.update.ApkVerificationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val updateRepository = mockk<UpdateRepository>()
    private val downloadManager = mockk<ApkDownloadManager>()
    private val apkInstaller = mockk<ApkInstaller>()
    private val environment = FakeUpdateEnvironment()
    private val clock = UpdateClock { NOW }
    private lateinit var preferencesRepository: FakeUpdatePreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferencesRepository = FakeUpdatePreferencesRepository()
        coEvery { downloadManager.cleanupExcept(any()) } just runs
        coEvery { downloadManager.enqueue(any()) } returns DOWNLOAD_ID
        every { downloadManager.observe(DOWNLOAD_ID, UPDATE) } returns
            flowOf(ApkDownloadState.Running(com.zhao.screentoolbox.core.model.UpdateDownloadProgress(1L, 2L)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun automaticCheckWithinTwentyFourHoursIsSkippedButManualCheckRuns() =
        runTest(dispatcher) {
            preferencesRepository = FakeUpdatePreferencesRepository(
                UpdatePreferences(lastCheckAtEpochMs = NOW - 1_000L),
            )
            coEvery {
                updateRepository.checkForUpdate("1.0.0", listOf("arm64-v8a"))
            } returns UpdateCheckResult.UpToDate
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.checkAutomatically()
            advanceUntilIdle()
            coVerify(exactly = 0) {
                updateRepository.checkForUpdate(any(), any())
            }

            viewModel.checkManually()
            advanceUntilIdle()
            coVerify(exactly = 1) {
                updateRepository.checkForUpdate("1.0.0", listOf("arm64-v8a"))
            }
            assertEquals(UpdatePhase.UP_TO_DATE, viewModel.uiState.value.phase)
        }

    @Test
    fun automaticCheckAfterTwentyFourHoursRunsAndOpensAvailableDialog() =
        runTest(dispatcher) {
            preferencesRepository = FakeUpdatePreferencesRepository(
                UpdatePreferences(lastCheckAtEpochMs = NOW - AUTO_CHECK_INTERVAL_MS),
            )
            coEvery {
                updateRepository.checkForUpdate("1.0.0", listOf("arm64-v8a"))
            } returns UpdateCheckResult.Available(UPDATE)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.checkAutomatically()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                updateRepository.checkForUpdate("1.0.0", listOf("arm64-v8a"))
            }
            assertEquals(UpdatePhase.AVAILABLE, viewModel.uiState.value.phase)
            assertTrue(viewModel.uiState.value.dialogVisible)
        }

    @Test
    fun availableReleaseOpensDialogAndStartsDownload() = runTest(dispatcher) {
        coEvery {
            updateRepository.checkForUpdate("1.0.0", listOf("arm64-v8a"))
        } returns UpdateCheckResult.Available(UPDATE)
        val file = mockk<File>(relaxed = true)
        every { downloadManager.observe(DOWNLOAD_ID, UPDATE) } returns
            flowOf(ApkDownloadState.Completed(file))
        coEvery { apkInstaller.verify(UPDATE, file) } returns ApkVerificationResult.Valid
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.checkManually()
        advanceUntilIdle()
        assertEquals(UpdatePhase.AVAILABLE, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.dialogVisible)

        viewModel.downloadUpdate()
        advanceUntilIdle()

        assertEquals(
            "unexpected state: ${viewModel.uiState.value}",
            UpdatePhase.READY_TO_INSTALL,
            viewModel.uiState.value.phase,
        )
        assertTrue(viewModel.uiState.value.dialogVisible)
        assertEquals(DOWNLOAD_ID, preferencesRepository.value.pendingDownload?.downloadId)
    }

    @Test
    fun debugBuildNeverChecksNetwork() = runTest(dispatcher) {
        environment.isDebugBuild = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.checkManually()
        advanceUntilIdle()

        coVerify(exactly = 0) { updateRepository.checkForUpdate(any(), any()) }
    }

    private fun viewModel() = UpdateViewModel(
        updateRepository = updateRepository,
        preferencesRepository = preferencesRepository,
        downloadManager = downloadManager,
        apkInstaller = apkInstaller,
        environment = environment,
        clock = clock,
    )

    private class FakeUpdateEnvironment : UpdateEnvironment {
        override val currentVersionName: String = "1.0.0"
        override val currentVersionCode: Long = 1L
        override val supportedAbis: List<String> = listOf("arm64-v8a")
        override var isDebugBuild: Boolean = false
    }

    private class FakeUpdatePreferencesRepository(
        initial: UpdatePreferences = UpdatePreferences(),
    ) : UpdatePreferencesRepository {
        private val state = MutableStateFlow(initial)
        override val preferences: Flow<UpdatePreferences> = state
        val value: UpdatePreferences
            get() = state.value

        override suspend fun recordCheckAttempt(epochMs: Long) {
            state.value = state.value.copy(lastCheckAtEpochMs = epochMs)
        }

        override suspend fun setPendingDownload(pending: PendingUpdateDownload) {
            state.value = state.value.copy(pendingDownload = pending)
        }

        override suspend fun clearPendingDownload() {
            state.value = state.value.copy(pendingDownload = null)
        }
    }

    private companion object {
        const val NOW = 10_000_000L
        const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        const val DOWNLOAD_ID = 42L
        val UPDATE = AvailableUpdate(
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
}
