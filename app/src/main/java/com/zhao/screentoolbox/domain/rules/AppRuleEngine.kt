package com.zhao.screentoolbox.domain.rules

import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayRuleResolution
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.core.model.isGlobalRule
import javax.inject.Inject

/**
 * 将全局应用规则、前台应用规则和临时解除状态解析为窗口集合。
 *
 * 全局规则始终参与解析，应用规则在匹配前台包名时叠加；两者按配置 ID 去重。
 * 旧版未绑定任何规则的配置不再回退显示。
 * 编辑器预览优先于所有规则，确保应用自身界面中的实时预览不受自动关联干扰。
 */
class AppRuleEngine @Inject constructor() {
    fun resolve(
        configs: List<OverlayConfig>,
        rules: List<AppRule>,
        foregroundPackage: String?,
        ownPackage: String,
        temporaryUnlockActive: Boolean,
        preview: OverlayConfig?,
    ): OverlayRuleResolution {
        if (preview != null) {
            return OverlayRuleResolution(activeConfigs = listOf(preview))
        }
        if (foregroundPackage == ownPackage) {
            return OverlayRuleResolution(activeConfigs = emptyList())
        }

        val configById = configs.associateBy { it.id }
        val globalRule = rules.firstOrNull { it.isGlobalRule() && it.enabled }
        val appRule = rules.firstOrNull {
            !it.isGlobalRule() && it.enabled && it.packageName == foregroundPackage
        }
        val activeById = linkedMapOf<String, OverlayConfig>()

        val globalConfigs = globalRule?.configsForRule(configById, temporaryUnlockActive).orEmpty()
        globalConfigs.forEach { config ->
            activeById[config.id] = config
        }
        val appConfigs = appRule?.configsForRule(configById, temporaryUnlockActive).orEmpty()
        appConfigs.forEach { config ->
            activeById[config.id] = config
        }

        val active = activeById.values.toList()
        return OverlayRuleResolution(
            activeConfigs = active,
            matchedRulePackage = appRule?.packageName?.takeIf { appConfigs.isNotEmpty() }
                ?: globalRule?.packageName?.takeIf { globalConfigs.isNotEmpty() },
            ruleControlledTypes = active.mapTo(linkedSetOf()) { it.type },
        )
    }

    private fun AppRule.configsForRule(
        configById: Map<String, OverlayConfig>,
        temporaryUnlockActive: Boolean,
    ): List<OverlayConfig> = configIds.mapNotNull(configById::get).filter { config ->
        when (config.type) {
            OverlayType.BURN_MASK -> burnMaskEnabled
            OverlayType.TOUCH_BLOCK -> touchBlockEnabled && !temporaryUnlockActive
            OverlayType.WATERMARK -> watermarkEnabled
        }
    }
}
