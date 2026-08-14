package io.github.loje0611.tennisdoc.feature.lab.session

import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import kotlinx.coroutines.flow.StateFlow

interface LabSessionPort {
    val isSessionActive: StateFlow<Boolean>
    val activeSessionId: StateFlow<String?>
    val sessionDurationSeconds: StateFlow<Long>
    val swingCount: StateFlow<Int>
    val isSensorConnected: StateFlow<Boolean>

    fun startSession(type: SessionType = SessionType.LAB, drillType: DrillType): String
    fun finishSession()
}
