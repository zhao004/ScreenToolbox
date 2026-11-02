package com.zhao.screentoolbox.di

import android.content.Context
import androidx.room.Room
import com.zhao.screentoolbox.data.database.AppDatabase
import com.zhao.screentoolbox.data.database.AppRuleDao
import com.zhao.screentoolbox.data.database.OperationLogDao
import com.zhao.screentoolbox.data.database.OverlayDao
import com.zhao.screentoolbox.data.preferences.DataStoreSettingsRepository
import com.zhao.screentoolbox.data.preferences.DataStoreUpdatePreferencesRepository
import com.zhao.screentoolbox.data.repository.AppRuleRepositoryImpl
import com.zhao.screentoolbox.data.repository.OverlayRepositoryImpl
import com.zhao.screentoolbox.data.repository.OperationLogRepositoryImpl
import com.zhao.screentoolbox.data.update.GitHubUpdateRepository
import com.zhao.screentoolbox.domain.repository.AppRuleRepository
import com.zhao.screentoolbox.domain.repository.OverlayRepository
import com.zhao.screentoolbox.domain.repository.SettingsRepository
import com.zhao.screentoolbox.domain.repository.OperationLogRepository
import com.zhao.screentoolbox.domain.repository.UpdateClock
import com.zhao.screentoolbox.domain.repository.UpdateEnvironment
import com.zhao.screentoolbox.domain.repository.UpdatePreferencesRepository
import com.zhao.screentoolbox.domain.repository.UpdateRepository
import com.zhao.screentoolbox.system.update.AndroidUpdateEnvironment
import com.zhao.screentoolbox.system.update.SystemUpdateClock
import com.zhao.screentoolbox.system.usage.AppUsageMonitor
import com.zhao.screentoolbox.system.usage.ForegroundAppMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "screen_toolbox.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideOverlayDao(database: AppDatabase): OverlayDao = database.overlayDao()
    @Provides fun provideAppRuleDao(database: AppDatabase): AppRuleDao = database.appRuleDao()
    @Provides fun provideOperationLogDao(database: AppDatabase): OperationLogDao = database.operationLogDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindOverlayRepository(implementation: OverlayRepositoryImpl): OverlayRepository

    @Binds
    @Singleton
    abstract fun bindAppRuleRepository(implementation: AppRuleRepositoryImpl): AppRuleRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(implementation: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(implementation: GitHubUpdateRepository): UpdateRepository

    @Binds
    @Singleton
    abstract fun bindUpdatePreferencesRepository(
        implementation: DataStoreUpdatePreferencesRepository,
    ): UpdatePreferencesRepository

    @Binds
    @Singleton
    abstract fun bindUpdateEnvironment(
        implementation: AndroidUpdateEnvironment,
    ): UpdateEnvironment

    @Binds
    @Singleton
    abstract fun bindUpdateClock(implementation: SystemUpdateClock): UpdateClock

    @Binds
    @Singleton
    abstract fun bindForegroundAppMonitor(implementation: AppUsageMonitor): ForegroundAppMonitor

    @Binds
    @Singleton
    abstract fun bindOperationLogRepository(
        implementation: OperationLogRepositoryImpl,
    ): OperationLogRepository
}
