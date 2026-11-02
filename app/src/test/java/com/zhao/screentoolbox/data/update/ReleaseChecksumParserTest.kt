package com.zhao.screentoolbox.data.update

import com.zhao.screentoolbox.domain.repository.UpdateCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseChecksumParserTest {
    @Test
    fun arm64IsPreferredWhenBothReleaseApksAreCompatible() {
        val result = ReleaseChecksumParser.parse(
            checksumText = checksumText(),
            currentVersionName = "1.0.0",
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )

        val update = (result as UpdateCheckResult.Available).update
        assertEquals("v1.1.0", update.tag)
        assertEquals("1.1.0", update.versionName)
        assertEquals("arm64-v8a", update.abi)
        assertEquals(SHA_ARM64, update.sha256)
        assertTrue(update.downloadUrl.endsWith("/v1.1.0/$ARM64_FILE"))
    }

    @Test
    fun compatibleFallbackAbiIsSelected() {
        val result = ReleaseChecksumParser.parse(
            checksumText = checksumText(),
            currentVersionName = "1.0.0",
            supportedAbis = listOf("armeabi-v7a"),
        )

        val update = (result as UpdateCheckResult.Available).update
        assertEquals("armeabi-v7a", update.abi)
        assertEquals(SHA_V7A, update.sha256)
    }

    @Test
    fun unsupportedDeviceDoesNotProduceDownloadUrl() {
        val result = ReleaseChecksumParser.parse(
            checksumText = checksumText(),
            currentVersionName = "1.0.0",
            supportedAbis = listOf("x86_64"),
        )

        assertTrue(result is UpdateCheckResult.UnsupportedDevice)
    }

    @Test
    fun sameReleaseIsReportedAsUpToDate() {
        val result = ReleaseChecksumParser.parse(
            checksumText = checksumText(),
            currentVersionName = "1.1.0",
            supportedAbis = listOf("arm64-v8a"),
        )

        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun malformedChecksumIsRejected() {
        assertThrows(UpdateCheckException::class.java) {
            ReleaseChecksumParser.parse(
                checksumText = checksumText().replaceFirst("  ./", "  "),
                currentVersionName = "1.0.0",
                supportedAbis = listOf("arm64-v8a"),
            )
        }
    }

    @Test
    fun duplicateAbiIsRejected() {
        val duplicated = checksumText() + "\n" +
            "$SHA_ARM64  ./$ARM64_FILE"

        assertThrows(UpdateCheckException::class.java) {
            ReleaseChecksumParser.parse(
                checksumText = duplicated,
                currentVersionName = "1.0.0",
                supportedAbis = listOf("arm64-v8a"),
            )
        }
    }

    private fun checksumText(): String =
        "$SHA_ARM64  ./$ARM64_FILE\n" +
            "$SHA_V7A  ./$V7A_FILE"

    private companion object {
        const val SHA_ARM64 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SHA_V7A = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val ARM64_FILE = "ScreenToolbox-v1.1.0-arm64-v8a.apk"
        const val V7A_FILE = "ScreenToolbox-v1.1.0-armeabi-v7a.apk"
    }
}
