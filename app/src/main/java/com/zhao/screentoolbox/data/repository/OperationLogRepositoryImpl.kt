package com.zhao.screentoolbox.data.repository

import com.zhao.screentoolbox.data.database.OperationLogDao
import com.zhao.screentoolbox.data.database.OperationLogEntity
import com.zhao.screentoolbox.domain.repository.OperationLogRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationLogRepositoryImpl @Inject constructor(
    private val dao: OperationLogDao,
) : OperationLogRepository {
    override suspend fun record(action: String, detail: String?) {
        dao.insert(
            OperationLogEntity(
                action = action.take(80),
                detail = detail?.take(240),
                timestamp = System.currentTimeMillis(),
            ),
        )
        dao.trim()
    }
}
