package com.zhao.screentoolbox.domain.rules

import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.AppSettings
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.core.model.WatermarkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRuleEngineTest {
    private val engine = AppRuleEngine()
    private val burn = BurnMaskConfig("burn", "遮罩", true, NormalizedRect(0f, 0f, 1f, 0.1f))
    private val touch = TouchBlockConfig("touch", "防误触", true, NormalizedRect(0f, 0.8f, 1f, 0.2f))
    private val watermark = WatermarkConfig("watermark", "水印", true)
    private val configs = listOf(burn, touch, watermark)

    @Test
    fun matchingRuleTakesPriorityAndCanEnableGloballyDisabledConfig() {
        val rule = AppRule(
            id = 1,
            packageName = "target.app",
            displayName = "Target",
            configIds = setOf("burn", "touch", "watermark"),
        )
        val resolution = engine.resolve(
            configs = configs.map {
                when (it) {
                    is BurnMaskConfig -> it.copy(enabled = false)
                    is TouchBlockConfig -> it.copy(enabled = false)
                    is WatermarkConfig -> it.copy(enabled = false)
                }
            },
            rules = listOf(rule),
            settings = AppSettings(),
            foregroundPackage = "target.app",
            ownPackage = "self.app",
            temporaryUnlockActive = false,
            preview = null,
        )

        assertEquals("target.app", resolution.matchedRulePackage)
        assertEquals(setOf("burn", "touch", "watermark"), resolution.activeConfigs.map { it.id }.toSet())
        assertEquals(3, resolution.ruleControlledTypes.size)
    }

    @Test
    fun temporaryUnlockOnlyRemovesTouchBlockers() {
        val resolution = engine.resolve(
            configs = configs,
            rules = emptyList(),
            settings = AppSettings(),
            foregroundPackage = "other.app",
            ownPackage = "self.app",
            temporaryUnlockActive = true,
            preview = null,
        )

        assertEquals(setOf("burn", "watermark"), resolution.activeConfigs.map { it.id }.toSet())
    }

    @Test
    fun boundConfigIsExcludedFromGlobalFallback() {
        val rule = AppRule(
            id = 1,
            packageName = "target.app",
            displayName = "Target",
            configIds = setOf("burn"),
        )

        val resolution = engine.resolve(
            configs = configs,
            rules = listOf(rule),
            settings = AppSettings(),
            foregroundPackage = "other.app",
            ownPackage = "self.app",
            temporaryUnlockActive = false,
            preview = null,
        )

        assertEquals(setOf("touch", "watermark"), resolution.activeConfigs.map { it.id }.toSet())
    }

    @Test
    fun disabledRuleDoesNotMatchButKeepsItsConfigsScoped() {
        val rule = AppRule(
            id = 1,
            packageName = "target.app",
            displayName = "Target",
            enabled = false,
            configIds = setOf("watermark"),
        )

        val resolution = engine.resolve(
            configs = configs,
            rules = listOf(rule),
            settings = AppSettings(),
            foregroundPackage = "target.app",
            ownPackage = "self.app",
            temporaryUnlockActive = false,
            preview = null,
        )

        assertEquals(null, resolution.matchedRulePackage)
        assertEquals(setOf("burn", "touch"), resolution.activeConfigs.map { it.id }.toSet())
    }

    @Test
    fun ownPackageHidesRegularOverlays() {
        val resolution = engine.resolve(
            configs = configs,
            rules = emptyList(),
            settings = AppSettings(),
            foregroundPackage = "self.app",
            ownPackage = "self.app",
            temporaryUnlockActive = false,
            preview = null,
        )

        assertTrue(resolution.activeConfigs.isEmpty())
    }

    @Test
    fun previewOverridesEveryRule() {
        val preview = watermark.copy(id = "preview")
        val resolution = engine.resolve(
            configs = configs,
            rules = emptyList(),
            settings = AppSettings(),
            foregroundPackage = "self.app",
            ownPackage = "self.app",
            temporaryUnlockActive = true,
            preview = preview,
        )

        assertEquals(listOf("preview"), resolution.activeConfigs.map { it.id })
    }
}
