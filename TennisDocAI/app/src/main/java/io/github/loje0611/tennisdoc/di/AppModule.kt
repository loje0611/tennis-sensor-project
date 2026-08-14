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
}
