package com.zhao.screentoolbox.domain.rules

import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_DISPLAY_NAME
import com.zhao.screentoolbox.core.model.GLOBAL_RULE_PACKAGE
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
    private val globalRule = AppRule(
        id = 1,
        packageName = GLOBAL_RULE_PACKAGE,
        displayName = GLOBAL_RULE_DISPLAY_NAME,
        configIds = setOf("burn", "touch"),
    )
    private val appRule = AppRule(
        id = 2,
        packageName = "target.app",
        displayName = "Target",
        configIds = setOf("touch", "watermark"),
    )

    @Test
    fun globalAndMatchingAppRulesAreMergedAndDeduplicated() {
        val resolution = engine.resolve(
            configs = configs,
            rules = listOf(globalRule, appRule),
            foregroundPackage = "target.app",
            ownPackage = "self.app",
            temporaryUnlockActive = false,
            preview = null,
        )

        assertEquals("target.app", resolution.matchedRulePackage)
        assertEquals(
            linkedSetOf("burn", "touch", "watermark"),
            resolution.activeConfigs.mapTo(linkedSetOf()) { it.id },
        )
        assertEquals(3, resolution.activeConfigs.size)
    }

    @Test
    fun globalRuleRemainsActiveWithoutMatchingAppRule() {
        val resolution = engine.resolve(
            configs = configs,
            rules = listOf(globalRule, appRule),
            foregroundPackage = "other.app",
            ownPackage = "self.app",
            temporaryUnlockActive = false,
            preview = null,
        )

        assertEquals(GLOBAL_RULE_PACKAGE, resolution.matchedRulePackage)
        assertEquals(setOf("burn", "touch"), resolution.activeConfigs.mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun disabledRulesDoNotContribute() {
        val resolution = engine.resolve(
            configs = configs,
            rules = listOf(
                globalRule.copy(enabled = false),
                appRule.copy(enabled = false),
            ),
            foregroundPackage = "target.app",
            ownPackage = "self.app",
            temporaryUnlockActive = false,
            preview = null,
        )

        assertTrue(resolution.activeConfigs.isEmpty())
        assertEquals(null, resolution.matchedRulePackage)
    }

    @Test
    fun temporaryUnlockRemovesTouchBlockersFromMergedRules() {
        val resolution = engine.resolve(
            configs = configs,
            rules = listOf(globalRule, appRule),
            foregroundPackage = "target.app",
            ownPackage = "self.app",
            temporaryUnlockActive = true,
            preview = null,
        )

        assertEquals(setOf("burn", "watermark"), resolution.activeConfigs.mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun eachRuleAppliesItsOwnTypeSwitches() {
        val resolution = engine.resolve(
            configs = configs,
            rules = listOf(
                globalRule.copy(burnMaskEnabled = false),
                appRule.copy(watermarkEnabled = false, touchBlockEnabled = false),
            ),
            foregroundPackage = "target.app",
            ownPackage = "self.app",
            temporaryUnlockActive = false,
            preview = null,
        )

        assertEquals(setOf("touch"), resolution.activeConfigs.mapTo(mutableSetOf()) { it.id })
        assertEquals(GLOBAL_RULE_PACKAGE, resolution.matchedRulePackage)
    }

    @Test
    fun unboundConfigsNoLongerFallbackToGlobalDisplay() {
        val resolution = engine.resolve(
            configs = configs,
            rules = listOf(globalRule.copy(configIds = setOf("burn"))),
            foregroundPackage = "other.app",
            ownPackage = "self.app",
            temporaryUnlockActive = false,
            preview = null,
        )

        assertEquals(listOf("burn"), resolution.activeConfigs.map { it.id })
    }

    @Test
    fun ownPackageHidesRegularRules() {
        val resolution = engine.resolve(
            configs = configs,
            rules = listOf(globalRule, appRule),
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
            rules = listOf(globalRule, appRule),
            foregroundPackage = "self.app",
            ownPackage = "self.app",
            temporaryUnlockActive = true,
            preview = preview,
        )

        assertEquals(listOf("preview"), resolution.activeConfigs.map { it.id })
    }
}
