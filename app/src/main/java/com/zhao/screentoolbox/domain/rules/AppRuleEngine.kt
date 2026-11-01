package com.zhao.screentoolbox.domain.rules

import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.AppSettings
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayRuleResolution
import com.zhao.screentoolbox.core.model.OverlayType
import javax.inject.Inject

/**
 * 将全局配置、前台应用和临时解除状态解析为服务应显示的窗口集合。
 *
 * 规则命中时规则优先；规则未命中时回退到全局手动启用的配置。编辑器预览优先于
 * 所有规则，确保应用自身界面中的实时预览不受自动关联干扰。
 */
class AppRuleEngine @Inject constructor() {
    fun resolve(
        configs: List<OverlayConfig>,
        rules: List<AppRule>,
        settings: AppSettings,
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

        val matchedRule = if (settings.autoAssociationEnabled) {
            rules.firstOrNull { it.enabled && it.packageName == foregroundPackage }
        } else {
            null
        }

        if (matchedRule != null) {
            val configById = configs.associateBy { it.id }
            val active = matchedRule.configIds.mapNotNull(configById::get)
                .filter { config ->
                    when (config.type) {
                        OverlayType.BURN_MASK -> matchedRule.burnMaskEnabled
                        OverlayType.TOUCH_BLOCK -> matchedRule.touchBlockEnabled && !temporaryUnlockActive
                        OverlayType.WATERMARK -> matchedRule.watermarkEnabled
                    }
                }
            return OverlayRuleResolution(
                activeConfigs = active,
                matchedRulePackage = matchedRule.packageName,
                ruleControlledTypes = OverlayType.entries.toSet(),
            )
        }

        val active = configs.filter { config ->
            config.enabled && when (config.type) {
                OverlayType.BURN_MASK -> settings.burnMaskGloballyEnabled
                OverlayType.TOUCH_BLOCK -> settings.touchBlockGloballyEnabled && !temporaryUnlockActive
                OverlayType.WATERMARK -> settings.watermarkGloballyEnabled
            }
        }
        return OverlayRuleResolution(activeConfigs = active)
    }
}
