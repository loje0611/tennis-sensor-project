package io.github.loje0611.tennisdoc

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import io.github.loje0611.tennisdoc.session.SwingAnalysisSessionState
import javax.inject.Inject

@HiltAndroidApp
class TennisDocApplication : Application() {

    @Inject
    lateinit var historyRepository: SwingHistoryRepository

    override fun onCreate() {
        super.onCreate()
        SwingAnalysisSessionState.historyRepository = historyRepository
    }
}
