package io.github.loje0611.tennisdoc.di

import android.content.Context
import io.github.loje0611.tennisdoc.analysis.CoachingCommentGeneratorImpl
import io.github.loje0611.tennisdoc.core.data.db.TennisDocDatabase
import io.github.loje0611.tennisdoc.core.data.repository.CalibrationStore
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import io.github.loje0611.tennisdoc.core.data.repository.ThemePreferencesRepository
import io.github.loje0611.tennisdoc.core.model.CoachingCommentGenerator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindCoachingCommentGenerator(
        impl: CoachingCommentGeneratorImpl,
    ): CoachingCommentGenerator

    companion object {
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
}
