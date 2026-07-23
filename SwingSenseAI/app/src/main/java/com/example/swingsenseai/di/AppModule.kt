package com.example.swingsenseai.di

import android.content.Context
import com.example.swingsenseai.data.db.SwingSenseDatabase
import com.example.swingsenseai.data.repository.CalibrationStore
import com.example.swingsenseai.data.repository.SwingHistoryRepository
import com.example.swingsenseai.data.repository.ThemePreferencesRepository
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
    fun provideDatabase(@ApplicationContext context: Context): SwingSenseDatabase =
        SwingSenseDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideHistoryRepository(database: SwingSenseDatabase): SwingHistoryRepository =
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
