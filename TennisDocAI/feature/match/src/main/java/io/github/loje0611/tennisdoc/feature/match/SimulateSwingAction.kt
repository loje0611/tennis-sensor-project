package io.github.loje0611.tennisdoc.feature.match

/**
 * Match 디버그 스윙 시뮬레이션의 분기 결과.
 * `:app`의 포트 구현이 이 정책에 따라 서비스 요청 또는 상태 직접 갱신을 수행한다.
 */
enum class SimulateSwingAction {
    /** 디버그 모드 꺼짐 — 아무 것도 하지 않음 */
    Ignore,
    /** 파이프라인 실행 중 — ForegroundService에 시뮬레이션 요청 */
    RequestServiceSimulation,
    /** 그 외 — 세션 상태에 레이블 직접 반영 */
    UpdateLabelDirectly,
}

fun resolveSimulateSwingAction(
    debugModeEnabled: Boolean,
    pipelineRunning: Boolean,
): SimulateSwingAction = when {
    !debugModeEnabled -> SimulateSwingAction.Ignore
    pipelineRunning -> SimulateSwingAction.RequestServiceSimulation
    else -> SimulateSwingAction.UpdateLabelDirectly
}
