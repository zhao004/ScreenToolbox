package com.zhao.screentoolbox.data.repository

import androidx.room.withTransaction
import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.data.database.AppDatabase
import com.zhao.screentoolbox.data.database.AppRuleConfigCrossRef
import com.zhao.screentoolbox.data.database.AppRuleEntity
import com.zhao.screentoolbox.domain.repository.AppRuleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AppRuleRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
) : AppRuleRepository {
    private val dao = database.appRuleDao()

    override val rules: Flow<List<AppRule>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** 规则主体和配置绑定必须在同一事务中更新，避免自动关联读取到中间状态。 */
    override suspend fun save(rule: AppRule): Long {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = dao.getByPackage(rule.packageName)
            val id = dao.upsertRule(
                AppRuleEntity(
                    id = existing?.rule?.id ?: rule.id,
                    packageName = rule.packageName,
                    displayName = rule.displayName,
                    enabled = rule.enabled,
                    burnMaskEnabled = rule.burnMaskEnabled,
                    touchBlockEnabled = rule.touchBlockEnabled,
                    watermarkEnabled = rule.watermarkEnabled,
                    createdAt = existing?.rule?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            val persistedId = if (id > 0) id else existing?.rule?.id ?: rule.id
            dao.deleteCrossRefs(persistedId)
            if (rule.configIds.isNotEmpty()) {
                dao.insertCrossRefs(rule.configIds.map { AppRuleConfigCrossRef(persistedId, it) })
            }
            persistedId
        }
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }
}
