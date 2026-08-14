package io.github.loje0611.tennisdoc.core.data.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.loje0611.tennisdoc.core.data.db.TennisDocDatabase
import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import io.github.loje0611.tennisdoc.core.data.db.dao.SwingSessionDao
import io.github.loje0611.tennisdoc.core.data.repository.CalibrationStore
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepositoryImpl
import io.github.loje0611.tennisdoc.core.data.repository.ThemePreferencesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreDataModule {

    @Binds
    @Singleton
    abstract fun bindSwingHistoryRepository(
        impl: SwingHistoryRepositoryImpl
    ): SwingHistoryRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): TennisDocDatabase =
            TennisDocDatabase.getInstance(context)

        @Provides
        @Singleton
        fun provideSwingSessionDao(database: TennisDocDatabase): SwingSessionDao =
            database.swingSessionDao()

        @Provides
        @Singleton
        fun provideLabRawRecordDao(database: TennisDocDatabase): LabRawRecordDao =
            database.labRawRecordDao()

        @Provides
        @Singleton
        fun provideThemePreferencesRepository(@ApplicationContext context: Context): ThemePreferencesRepository =
            ThemePreferencesRepository(context)

        @Provides
        @Singleton
        fun provideCalibrationStore(@ApplicationContext context: Context): CalibrationStore =
            CalibrationStore(context)
    }
}
