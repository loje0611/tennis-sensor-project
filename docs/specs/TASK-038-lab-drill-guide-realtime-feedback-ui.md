# TASK-038 — Lab 세션 드릴 가이드 및 실시간 융합 피드백 UI 구현

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (Phase 3 D그룹: Lab 드릴 선택 바, 세션 제어, 실시간 융합 인과 피드백 및 5단계 운동체인 오버레이 UI 명세) |
| v2 | 2026-08-15 | PM | 실기기 센서 연결 제어(스캔/취소/권한), 센서 미연결 시 세션 차단 토스트 검증 조건(AC-8/AC-9) 구체화 및 LabUiState/Port 구조 동기화 |
| v3 | 2026-08-15 | PM | UX 개선: (1) 센서 자동 연결(Auto-Connect) 및 [측정 시작/종료] 단일 액션 버튼 통합, (2) 개발자 모드(Debug Mode) 활성화 시에만 FPS/ms 성능 오버레이 노출하도록 변경 및 AC-8~AC-10 갱신 |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Phase 3 Lab 모드의 핵심 사용자 인터페이스인 **Lab 드릴 가이드 및 실시간 융합 피드백 UI**를 `:feature:lab` 모듈에 구현하는 작업을 규정합니다.

기존 카메라 미리보기 화면 위에 (1) 정답 드릴 선택 바(`DrillType`), (2) 센서 연결 상태 인디케이터 및 단일 [측정 시작/종료] 버튼, (3) 스윙 직후 갱신되는 실시간 라켓 페이스 각도(`OPEN`/`CLOSED`/`SQUARE`), (4) 5단계 통합 운동 체인 시각화 바, (5) 인과 코칭 피드백 및 통계적 이상치(피로도) 알림 카드를 결합하여 사용자에게 직관적이고 과학적인 스윙 피드백을 제공합니다. 또한 개발/성능 모니터링용 FPS/ms 오버레이는 일반 사용자 경험 보호를 위해 개발자 모드 활성화 시에만 노출되도록 제어합니다.

### 1.2 범위
- `:feature:lab` 모듈 UI 컴포넌트 구현 (`io.github.loje0611.tennisdoc.feature.lab.ui`):
  - `DrillSelectorBar`: 상단 드릴 선택 칩/드롭다운 (`FOREHAND_TOPSPIN`, `FOREHAND_FLAT`, `FOREHAND_SLICE`, `BACKHAND_TOPSPIN`, `BACKHAND_SLICE`, `SERVE`, `FOREHAND_VOLLEY`, `BACKHAND_VOLLEY` 등).
  - `LabSessionControlHeader`: BLE 센서 연결 상태 뱃지("센서 연결됨" / "센서 찾는 중..." / "센서 미연결"), 세션 타이머, 스윙 카운터, **단일 [측정 시작 / 측정 종료] 액션 버튼**. (별도 수동 연결 버튼 제거)
  - `LabRealtimeFeedbackCard`: 하단 반투명 오버레이 카드:
    - 라켓 페이스 상태 뱃지 및 각도 편차 (`OPEN +12°`, `CLOSED -10°`, `SQUARE 0°`).
    - 5단계 운동 체인 가속 순서 인디케이터 (골반 ➔ 어깨 ➔ 손목 ➔ 라켓 ➔ 임팩트) 및 에너지 전달 효율(%).
    - 인과 진단 설명(`causalExplanation`) 및 교정 가이드(`coachingFeedback`).
  - `LabAnomalyAlertBanner`: 개인 Baseline 대비 $z \ge 2.5$ 급변 또는 피로도 누적 시 폼 붕괴 경고 알림.
  - `LabScreen`:
    - 카메라 권한(`CAMERA`) 및 BLE 런타임 권한(`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`) 요청.
    - BLE 권한 허용 및 센서 미연결 상태 시 화면 진입 시 **백그라운드 센서 자동 연결 시도 (Auto-Connect)**.
    - **개발자 모드(`isDebugModeEnabled == true`) 활성화 시에만 FPS/ms 성능 오버레이 표시** (일반 모드에서는 숨김).
- `LabViewModel` 및 `LabSessionPort` 확장:
  - `selectedDrill: StateFlow<DrillType>`
  - `isSessionActive: StateFlow<Boolean>`
  - `sessionDurationSeconds: StateFlow<Long>`
  - `isSensorConnected: StateFlow<Boolean>`
  - `isSensorScanning: StateFlow<Boolean>`
  - `isDebugModeEnabled: StateFlow<Boolean>`
  - `connectSensor()`, `disconnectSensor()` 포트 연동.
  - `startSession(): Boolean`, `finishSession()` 액션과 `SwingAnalysisSessionState` 및 `LabFusionPipeline` 연계.
- 단위 테스트 및 Compose 렌더링 검증:
  - 드릴 선택, 단일 측정 시작/종료, 센서 미연결 시 시작 방어 및 자동 연결 상태 전이 테스트.
  - 개발자 모드 토글에 따른 FPS 오버레이 표시/숨김 렌더링 테스트.
  - 융합 결과 인입 시 피드백 카드 상태 바인딩 테스트.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **드릴 가이드 (Drill Guide)**: 단일 구종(예: 포핸드 탑스핀)을 집중 훈련하기 위해 사용자가 사전에 지정하는 목표 스윙 타입.
- **자동 연결 (Auto-Connect)**: 사용자가 수동으로 연결 버튼을 누를 필요 없이, Lab 화면 진입 시 센서를 자동으로 탐색하여 백그라운드에서 연결을 시도하는 UX 패턴.
- **개발자 모드 (Debug Mode)**: Settings 등에서 연속 탭으로 활성화되는 디버그 모드로, FPS/ms 지연 등 기술적 성능 지표를 개발자/테스터에게만 노출하는 모드.

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 세션 라이프사이클: [`docs/specs/TASK-030-session-lifecycle-ux-refactoring.md`](TASK-030-session-lifecycle-ux-refactoring.md)
- 실시간 파이프라인: [`docs/specs/TASK-037-realtime-fusion-pipeline-integration.md`](TASK-037-realtime-fusion-pipeline-integration.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 드릴 선택기 (`DrillSelectorBar`)
- 사용자가 `DrillType` 목록(예: `FOREHAND_TOPSPIN`, `FOREHAND_FLAT`, `FOREHAND_SLICE`, `BACKHAND_TOPSPIN`, `BACKHAND_SLICE`, `SERVE`, `FOREHAND_VOLLEY`, `BACKHAND_VOLLEY`) 중 하나를 선택할 수 있는 수평 스크롤 칩 또는 드롭다운을 제공한다.
- 세션이 실행 중(`isSessionActive == true`)일 때는 드릴 변경을 비활성화하여 세션 데이터의 일관성을 유지한다.

### FR-2: 세션 시작/종료 제어 및 헤더 (`LabSessionControlHeader`)
- **센서 연결 상태 인디케이터 (상태 표시)**:
  - `isSensorConnected == true`: "센서 연결됨"(녹색 원 뱃지) 표시.
  - `isSensorScanning == true`: "센서 찾는 중..."(황색 원 뱃지) 표시.
  - `isSensorConnected == false` && `isSensorScanning == false`: "센서 미연결"(적색 원 뱃지) 표시.
  - 별도의 수동 "센서 연결" / "연결 취소" 버튼은 제거하며, 뱃지 영역 탭 시 수동 재연결 트리거를 보조적으로 지원할 수 있다.
- **단일 세션 액션 버튼**:
  - **세션 미시작 상태 (`isSessionActive == false`)**:
    - 선택된 드릴 이름("목표: {드릴명}") 표시.
    - **[측정 시작] 버튼(녹색)** 제공.
    - 클릭 시:
      - 센서가 연결되어 있으면 `startSession(SessionType.LAB, selectedDrill)`을 호출하여 세션을 시작하고 [측정 종료] 버튼으로 전환한다.
      - 센서가 미연결 상태이면 `startSession()`은 `false`를 반환하고, 백그라운드 센서 연결을 시도하면서 "센서를 먼저 연결해 주세요" Toast 메시지를 표시한다.
  - **세션 실행 중 상태 (`isSessionActive == true`)**:
    - 실시간 세션 경과 시간(MM:SS), 누적 스윙 수 표시 ("00:00 | 스윙 0회").
    - **[측정 종료] 버튼(적색)** 제공 ➔ 클릭 시 `finishSession()` 호출 및 세션 종료.

### FR-3: 실시간 융합 피드백 카드 (`LabRealtimeFeedbackCard`)
- `latestFusedSwing` 데이터가 수신되면 화면 하단에 팝업 또는 고정 오버레이로 다음 정보를 렌더링한다:
  1. **라켓 페이스 상태**:
     - `SQUARE`: 녹색(Success) 뱃지 + "스퀘어 (0°)"
     - `OPEN`: 주황/적색(Warning) 뱃지 + "열림 (+12°)"
     - `CLOSED`: 파랑/적색(Warning) 뱃지 + "닫힘 (-10°)"
  2. **5단계 운동 체인 게이지**:
     - 골반 ➔ 어깨 ➔ 손목 ➔ 라켓 ➔ 임팩트 5개 아이콘/텍스트 단계.
     - 순차적 가속 시 녹색 연결선, 결함 시 적색 하이라이트.
     - 에너지 전달 효율 점수(예: "에너지 효율: 88%").
  3. **인과 코칭 텍스트**:
     - 주 원인 및 인과 설명 (예: "상체 조기 개방으로 인한 페이스 열림 ➔ 골반 회전 선행 필요").

### FR-4: 이상치 및 피로도 알림 배너 (`LabAnomalyAlertBanner`)
- `latestAnomalyReport`의 `fatigue.isFatigued == true` 또는 `severity == CRITICAL`인 이상치가 감지된 경우:
  - 상단에 주의 배너 표시 (예: "⚠️ 라켓 스피드가 평소 대비 2.8σ 급락했습니다. 휴식을 권장합니다.").

### FR-5: 성능 디버그 오버레이 제어 (FPS/ms 지연)
- 실시간 카메라 프레임 처리 성능(예: `29.8 FPS | 33ms`) 칩은 **`isDebugModeEnabled == true`인 개발자 모드 상태에서만 화면 좌측 상단에 렌더링**한다.
- 일반 사용자 모드(`isDebugModeEnabled == false`)에서는 완전히 숨김(GONE) 처리하여 시각적 인지 부하를 차단한다.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.feature.lab.ui

import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.model.DrillType

data class LabUiState(
    val selectedDrill: DrillType = DrillType.FOREHAND_TOPSPIN,
    val isSessionActive: Boolean = false,
    val sessionDurationSeconds: Long = 0L,
    val swingCount: Int = 0,
    val isSensorConnected: Boolean = false,
    val isSensorScanning: Boolean = false,
    val isDebugModeEnabled: Boolean = false,
    val latestFusedSwing: FusedSwing? = null,
    val latestAnomalyReport: BaselineComparisonReport? = null
)
```

```kotlin
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
    val isSensorScanning: StateFlow<Boolean>
    val isDebugModeEnabled: StateFlow<Boolean>

    fun startSession(type: SessionType = SessionType.LAB, drillType: DrillType): String
    fun finishSession()
    fun connectSensor()
    fun disconnectSensor()
}
```

---

## 5. UI/UX 요구사항
- **시인성 및 가독성**: 카메라 실시간 뷰 위에 오버레이되므로 반투명 어두운 배경(Scrim, alpha 0.6~0.65)을 적용하여 텍스트와 게이지가 명확히 식별되도록 설계.
- **간결한 조작 인터페이스**: 단일 [측정 시작/종료] 버튼과 큰 터치 타겟(최소 48dp)을 제공하여 한 손으로도 직관적인 제어 가능.
- **자동 연결 및 권한 플로우**:
  - Lab 화면 진입 시 카메라 권한(`CAMERA`) 및 OS 버전별 BLE 런타임 권한(`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`)을 순차 요청.
  - 권한이 허용되어 있고 센서가 미연결 상태이면 자동으로 `connectSensor()`를 백그라운드 호출하여 연결을 시도함.

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 부드러운 애니메이션 및 렌더링 성능
- 스윙 피드백 카드 등장/퇴장 시 Compose AnimatedVisibility 적용 (프레임 드랍 없이 60fps 유지).

### 6.2 모듈 의존성 규칙 준수
- `:feature:lab`은 `:core:ui`, `:core:model`, `:core:fusion`, `:core:data`, `:core:vision`을 참조하며 아키텍처 규칙을 위반하지 않음.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **FR-7 BLE 센서 미연결 상태에서 [측정 시작] 클릭 시**:
  - `startSession()`은 `false`를 반환하여 세션을 시작하지 않는다.
  - 자동으로 `connectSensor()`를 백그라운드 호출하여 센서 탐색을 재시도한다.
  - UI 레이어에서 Toast 메시지("센서를 먼저 연결해 주세요")를 출력한다.
- **세션 진행 중 화면 회전/전환**:
  - ViewModel 상태 보존을 통해 타이머와 스윙 카운터 유지.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `DrillSelectorBar`, `LabSessionControlHeader`, `LabRealtimeFeedbackCard`, `LabAnomalyAlertBanner` 컴포저블이 `:feature:lab`에 구현된다.
- [ ] **AC-2**: 드릴 선택기에서 구종 변경 시 `selectedDrill` 상태가 갱신되고, 세션 시작 시 변경이 비활성화된다.
- [ ] **AC-3**: 센서 연결 상태에서 "측정 시작" 클릭 시 `startSession(SessionType.LAB, drillType)`이 호출되고 단일 버튼이 "측정 종료"로 토글된다.
- [ ] **AC-4**: `FusedSwing` 데이터 인입 시 하단 카드에 라켓 페이스 상태(`OPEN`/`CLOSED`/`SQUARE`), 5단계 체인 게이지, 인과 코칭 문구가 렌더링된다.
- [ ] **AC-5**: 피로도/이상치 리포트 인입 시 상단 경고 배너가 표시된다.
- [ ] **AC-6**: `LabViewModelTest` 및 UI 단위 테스트가 100% 통과한다.
- [ ] **AC-7**: `./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.
- [ ] **AC-8**: Lab 화면 진입 시 센서 자동 연결을 시도하며, `LabSessionControlHeader`에는 별도 연결 버튼 없이 센서 상태 인디케이터(연결됨/찾는 중/미연결)와 단일 [측정 시작/종료] 버튼만 노출된다.
- [ ] **AC-9**: 센서 미연결 상태에서 "측정 시작" 클릭 시 `startSession()`이 실패(`false`)하고 세션이 시작되지 않으며 "센서를 먼저 연결해 주세요" Toast가 출력된다.
- [ ] **AC-10**: `isDebugModeEnabled == false`일 때는 FPS/ms 오버레이가 화면에 노출되지 않고, `isDebugModeEnabled == true`일 때만 FPS/ms 오버레이가 렌더링된다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
```
