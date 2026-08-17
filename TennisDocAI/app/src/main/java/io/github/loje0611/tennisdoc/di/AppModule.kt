package io.github.loje0611.tennisdoc.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.loje0611.tennisdoc.feature.lab.session.LabSessionPort
import io.github.loje0611.tennisdoc.feature.match.MatchSessionPort
import io.github.loje0611.tennisdoc.session.LabSessionPortImpl
import io.github.loje0611.tennisdoc.session.MatchSessionPortImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMatchSessionPort(
        impl: MatchSessionPortImpl,
    ): MatchSessionPort

    @Binds
    @Singleton
    abstract fun bindLabSessionPort(
        impl: LabSessionPortImpl,
    ): LabSessionPort

    companion object {
        @dagger.Provides
        @Singleton
        fun provideStructuredReportParser(): io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser {
            return io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser()
        }

        @dagger.Provides
        @Singleton
        fun provideCompositeAiCoachService(): io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService {
            return io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService()
        }
    }
}
