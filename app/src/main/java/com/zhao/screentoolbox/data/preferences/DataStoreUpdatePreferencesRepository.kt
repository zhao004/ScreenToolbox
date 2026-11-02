package com.zhao.screentoolbox.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhao.screentoolbox.core.model.AvailableUpdate
import com.zhao.screentoolbox.core.model.PendingUpdateDownload
import com.zhao.screentoolbox.domain.repository.UpdatePreferences
import com.zhao.screentoolbox.domain.repository.UpdatePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "screen_toolbox_update_preferences",
)

/** 独立保存更新节流信息和可恢复的系统下载任务。 */
@Singleton
class DataStoreUpdatePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : UpdatePreferencesRepository {
    override val preferences: Flow<UpdatePreferences> = context.updateDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map(::toPreferences)

    override suspend fun setAutomaticChecksEnabled(enabled: Boolean) {
        context.updateDataStore.edit { it[Keys.AutomaticChecksEnabled] = enabled }
    }

    override suspend fun recordCheckAttempt(epochMs: Long) {
        context.updateDataStore.edit { it[Keys.LastCheckAtEpochMs] = epochMs }
    }

    override suspend fun setPendingDownload(pending: PendingUpdateDownload) {
        context.updateDataStore.edit { preferences ->
            preferences[Keys.PendingDownloadId] = pending.downloadId
            preferences[Keys.PendingTag] = pending.update.tag
            preferences[Keys.PendingVersionName] = pending.update.versionName
            preferences[Keys.PendingAbi] = pending.update.abi
            preferences[Keys.PendingFileName] = pending.update.fileName
            preferences[Keys.PendingDownloadUrl] = pending.update.downloadUrl
            preferences[Keys.PendingSha256] = pending.update.sha256
        }
    }

    override suspend fun clearPendingDownload() {
        context.updateDataStore.edit { preferences ->
            preferences.remove(Keys.PendingDownloadId)
            preferences.remove(Keys.PendingTag)
            preferences.remove(Keys.PendingVersionName)
            preferences.remove(Keys.PendingAbi)
            preferences.remove(Keys.PendingFileName)
            preferences.remove(Keys.PendingDownloadUrl)
            preferences.remove(Keys.PendingSha256)
        }
    }

    private fun toPreferences(preferences: Preferences): UpdatePreferences {
        val downloadId = preferences[Keys.PendingDownloadId]
        val tag = preferences[Keys.PendingTag]
        val versionName = preferences[Keys.PendingVersionName]
        val abi = preferences[Keys.PendingAbi]
        val fileName = preferences[Keys.PendingFileName]
        val downloadUrl = preferences[Keys.PendingDownloadUrl]
        val sha256 = preferences[Keys.PendingSha256]
        val pending = if (
            downloadId != null &&
            downloadId > 0L &&
            tag != null &&
            versionName != null &&
            abi != null &&
            fileName != null &&
            downloadUrl != null &&
            sha256 != null
        ) {
            PendingUpdateDownload(
                downloadId = downloadId,
                update = AvailableUpdate(
                    tag = tag,
                    versionName = versionName,
                    abi = abi,
                    fileName = fileName,
                    downloadUrl = downloadUrl,
                    sha256 = sha256,
                ),
            )
        } else {
            null
        }
        return UpdatePreferences(
            automaticChecksEnabled = preferences[Keys.AutomaticChecksEnabled] ?: true,
            lastCheckAtEpochMs = preferences[Keys.LastCheckAtEpochMs] ?: 0L,
            pendingDownload = pending,
        )
    }

    private object Keys {
        val AutomaticChecksEnabled = booleanPreferencesKey("automatic_checks_enabled")
        val LastCheckAtEpochMs = longPreferencesKey("last_check_at_epoch_ms")
        val PendingDownloadId = longPreferencesKey("pending_download_id")
        val PendingTag = stringPreferencesKey("pending_tag")
        val PendingVersionName = stringPreferencesKey("pending_version_name")
        val PendingAbi = stringPreferencesKey("pending_abi")
        val PendingFileName = stringPreferencesKey("pending_file_name")
        val PendingDownloadUrl = stringPreferencesKey("pending_download_url")
        val PendingSha256 = stringPreferencesKey("pending_sha256")
    }
}
