package io.github.loje0611.tennisdoc.core.analysis.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.loje0611.tennisdoc.core.analysis.impl.CoachingCommentGeneratorImpl
import io.github.loje0611.tennisdoc.core.model.CoachingCommentGenerator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreAnalysisModule {

    @Binds
    @Singleton
    abstract fun bindCoachingCommentGenerator(
        impl: CoachingCommentGeneratorImpl,
    ): CoachingCommentGenerator
}
