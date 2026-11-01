package com.zhao.screentoolbox.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.zhao.screentoolbox.core.model.AppSettings
import com.zhao.screentoolbox.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "screen_toolbox_settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            AppSettings(
                serviceEnabled = preferences[Keys.ServiceEnabled] ?: false,
                autoStartOnBoot = preferences[Keys.AutoStartOnBoot] ?: false,
            )
        }

    override suspend fun setServiceEnabled(enabled: Boolean) = put(Keys.ServiceEnabled, enabled)
    override suspend fun setAutoStartOnBoot(enabled: Boolean) = put(Keys.AutoStartOnBoot, enabled)

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private object Keys {
        val ServiceEnabled = booleanPreferencesKey("service_enabled")
        val AutoStartOnBoot = booleanPreferencesKey("auto_start_on_boot")
    }
}
