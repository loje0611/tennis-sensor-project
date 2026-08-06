package io.github.loje0611.tennisdoc.di

import android.content.Context
import io.github.loje0611.tennisdoc.data.db.TennisDocDatabase
import io.github.loje0611.tennisdoc.data.repository.CalibrationStore
import io.github.loje0611.tennisdoc.data.repository.SwingHistoryRepository
import io.github.loje0611.tennisdoc.data.repository.ThemePreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TennisDocDatabase =
        TennisDocDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideHistoryRepository(database: TennisDocDatabase): SwingHistoryRepository =
        SwingHistoryRepository(database)

    @Provides
    @Singleton
    fun provideThemePreferencesRepository(@ApplicationContext context: Context): ThemePreferencesRepository =
        ThemePreferencesRepository(context)

    @Provides
    @Singleton
    fun provideCalibrationStore(@ApplicationContext context: Context): CalibrationStore =
        CalibrationStore(context)
}
