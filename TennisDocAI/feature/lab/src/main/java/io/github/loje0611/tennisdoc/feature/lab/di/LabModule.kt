package io.github.loje0611.tennisdoc.feature.lab.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import io.github.loje0611.tennisdoc.feature.lab.pipeline.LabFusionPipeline
import io.github.loje0611.tennisdoc.feature.lab.pipeline.LabFusionPipelineImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LabModule {

    @Provides
    @Singleton
    fun provideLabFusionPipeline(
        labRawRecordDao: LabRawRecordDao
    ): LabFusionPipeline {
        return LabFusionPipelineImpl(
            labRawRecordDao = labRawRecordDao
        )
    }
}
