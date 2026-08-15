# TASK-041 — Lab 카메라 모드별 특화 UX 구현 (전면 셀프 트레이닝 vs 후면 코칭 모드)

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-15 | PM | 최초 작성 (카메라 전/후면 전환, 전면 셀프 모드: 5초 카운트다운·대형 HUD·테두리 펄스·TTS 음성·전신 가이드, 후면 코칭 모드: 무지연·컴팩트 UI·음소거, 종료 결과 다이얼로그 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Phase 3 Lab 모드에서 사용자의 실제 테니스 훈련 환경(삼각대 거치 셀프 트레이닝 vs 코치/동료 촬영)에 최적화된 **카메라 모드별 특화 UI/UX 및 상호작용 시스템**을 `:feature:lab` 모듈에 구현하는 작업을 규정합니다.

스마트폰 전면 카메라(셀프 트레이닝 모드) 사용 시 **좌우 미러링**, **5초 셋업 카운트다운**, 원거리 시인성을 위한 **스윙 직후 대형 HUD 및 테두리 컬러 펄스**, **TTS 음성 코칭**, **전신 프레이밍 가이드**를 제공합니다. 반면 후면 카메라(코칭/관찰 모드) 사용 시에는 코치의 즉각적인 지도를 위해 **카운트다운 없는 즉시 시작**, **컴팩트 정밀 UI**, **TTS 음소거**로 전환됩니다. 또한 훈련 종료 시 즉시 성적 요약과 리플레이 뷰어로 이어지는 **원스톱 훈련 리포트 다이얼로그**를 제공합니다.

### 1.2 범위
- `:feature:lab` 모듈 UI 및 상호작용 컴포넌트 구현 (`io.github.loje0611.tennisdoc.feature.lab.ui`):
  - `CameraFacingToggle`: 상단 헤더의 전/후면 카메라 전환 토글 버튼 (`DEFAULT_FRONT_CAMERA` ↔ `DEFAULT_BACK_CAMERA`).
  - `SetupCountdownOverlay`: 셀프 트레이닝 모드에서 측정 시작 시 동작하는 **5초 카운트다운 (`5 ➔ 4 ➔ 3 ➔ 2 ➔ 1 ➔ 시작!`)** 오버레이 및 초 단위 사운드/비프음.
  - `FarFieldFeedbackOverlay`: 스윙 감지 직후 3초간 표시되는 **초대형 HUD 팝업 (라켓 페이스 상태, 에너지 효율 %)** 및 **화면 테두리 컬러 펄스(정타 초록 / 결함 빨강)**.
  - `BodyFramingGuide`: 전면 카메라 대기 상태에서 전신 안착 여부를 시각화하는 반투명 인체 실루엣 박스 및 `READY` 인디케이터.
  - `SessionCompletionDialog`: 측정 종료 즉시 팝업되는 세션 요약 모달 (정타율 %, 평균 효율 %, 스윙 수, `[리플레이 보기]` 및 `[새 훈련]` 버튼).
- `LabViewModel` 및 상태 머신 확장:
  - `cameraFacingMode: StateFlow<CameraFacingMode>` (`FRONT` 기본값 / `BACK`).
  - `countdownSeconds: StateFlow<Int?>` (5~1초 카운트다운 상태 관리).
  - `farFieldHudState: StateFlow<FarFieldHudState?>` (스윙 직후 3초 타이머 및 페이드아웃).
  - `isSessionCompleted: StateFlow<Boolean>` (종료 다이얼로그 트리거).
- 전면 카메라 미러링(좌우 반전) 처리:
  - 전면 카메라 시 카메라 프리뷰 및 `PoseOverlayCanvas`에 수평 반전(`scale(-1f, 1f)`) 적용.
- 단위 테스트 및 Compose UI 검증.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **셀프 트레이닝 모드 (전면 카메라 `FRONT`)**: 삼각대에 거치하여 2~3m 거리에서 사용자가 폰 액정을 거울처럼 바라보며 훈련하는 모드.
- **코칭/관찰 모드 (후면 카메라 `BACK`)**: 코치나 동료가 스마트폰을 손에 들고 스윙자를 근거리에서 관찰/촬영하는 모드.
- **대형 HUD (Far-Field HUD)**: 원거리에서도 한눈에 식별할 수 있도록 큼직한 폰트와 고대비 색상으로 3초간 표시되는 헤드업 디스플레이.

### 2.2 참고 문서
- Lab UI 기본 명세: [`docs/specs/TASK-038-lab-drill-guide-realtime-feedback-ui.md`](TASK-038-lab-drill-guide-realtime-feedback-ui.md)
- 동기 리플레이 명세: [`docs/specs/TASK-039-synchronized-replay-diagnostic-viewer.md`](TASK-039-synchronized-replay-diagnostic-viewer.md)
- History 세션 연동: [`docs/specs/TASK-040-history-lab-session-detail-replay-navigation.md`](TASK-040-history-lab-session-detail-replay-navigation.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 카메라 전/후면 전환 제어 (`CameraFacingToggle`)
- 세션 제어 헤더 또는 화면 상단 우측에 카메라 전환 토글 버튼(`🔄`)을 배치한다.
- 클릭 시 `CameraFacingMode.FRONT` ↔ `CameraFacingMode.BACK`을 상호 전환한다. (기본값: `FRONT`)
- **미러링 규칙**:
  - `FRONT`: 카메라 프리뷰와 스켈레톤 관절 캔버스에 수평 반전(Horizontal Mirror)을 적용하여 사용자가 거울을 보듯 자연스럽게 자세를 잡을 수 있도록 한다.
  - `BACK`: 미러링을 적용하지 않고 실제 촬영자 시점의 원본 좌표계를 유지한다.

### FR-2: 셀프 트레이닝 모드 — 5초 셋업 카운트다운 (`SetupCountdownOverlay`)
- `cameraFacingMode == FRONT` 상태에서 [측정 시작] 클릭 시:
  - 센서 연결 상태 확인 후, 세션을 즉시 시작하지 않고 **5초 카운트다운(`5 ➔ 4 ➔ 3 ➔ 2 ➔ 1 ➔ 시작!`)**을 화면 중앙에 초대형 숫자로 렌더링한다.
  - 매 초마다 짧은 비프음을 출력하고, 0초("시작!") 도달 시 시작 차임벨과 함께 `startSession()`을 실행한다.
  - 카운트다운 진행 중에는 하단에 [취소] 버튼을 노출하여 언제든 셋업을 중단할 수 있다.
- `cameraFacingMode == BACK` 상태에서는 카운트다운 없이 [측정 시작] 클릭 즉시 `startSession()`이 실행된다.

### FR-3: 셀프 트레이닝 모드 — 스윙 직후 대형 HUD & 테두리 컬러 펄스 (`FarFieldFeedbackOverlay`)
- `cameraFacingMode == FRONT` 상태에서 새로운 스윙(`FusedSwing`)이 감지된 경우:
  1. **초대형 HUD 팝업 (3초간 유지)**:
     - 화면 중앙/상단에 대형 라켓 페이스 뱃지 (`SQUARE 0°` 녹색, `OPEN +12°` 주황, `CLOSED -10°` 빨강)와 에너지 효율(`92%`)을 36sp 이상의 큰 글씨로 표시.
  2. **화면 테두리 컬러 펄스 (1.5초간 점멸)**:
     - 정타(`SQUARE`): 화면 가장자리 전체에 **초록색 네온 펄스 테두리** 점멸.
     - 결함(`OPEN`/`CLOSED`): 화면 가장자리에 **주황/빨간색 네온 펄스 테두리** 점멸.
  3. 3초 경과 후 부드러운 Alpha 페이드아웃 애니메이션과 함께 기본 대기 상태로 복귀.
- `cameraFacingMode == BACK` 상태에서는 대형 HUD 오버레이를 띄우지 않고 기존 하단 컴팩트 카드(`LabRealtimeFeedbackCard`)로만 세부 지표를 렌더링한다.

### FR-4: 음성 코칭 및 사운드 모드 제어
- `cameraFacingMode == FRONT`: 스윙 완료 즉시 1줄 음성 코칭(TTS)을 활성화한다 (예: *"스퀘어, 훌륭한 궤적입니다!"*).
- `cameraFacingMode == BACK`: 코치의 현장 육성 지도를 방해하지 않도록 TTS 음성을 음소거(Mute)하고, 임팩트 감지 비프음만 짧게 출력한다.

### FR-5: 전신 실루엣 프레이밍 가이드 (`BodyFramingGuide`)
- `cameraFacingMode == FRONT`이며 세션 미시작 상태(`isSessionActive == false`)일 때:
  - 카메라 뷰 중앙에 반투명한 인체 실루엣 가이드 라인을 표시한다.
  - 33개 관절 중 주요 신체 부위(머리, 어깨, 골반, 발목)가 모두 프레임 내에 안정적으로 검출되면 `🟢 READY (준비 완료)` 인디케이터를 점등한다.
- `cameraFacingMode == BACK` 상태에서는 프레이밍 가이드 박스를 숨긴다.

### FR-6: 훈련 종료 시 '원스톱 훈련 리포트 다이얼로그' (`SessionCompletionDialog`)
- [측정 종료] 클릭 시 `finishSession()` 완료 후 즉시 훈련 리포트 모달 다이얼로그를 표시한다:
  - **헤더**: `🎯 {드릴명} 훈련 완료!`
  - **요약 지표**:
    - 총 스윙 수 (예: `15회`) 및 훈련 시간 (MM:SS)
    - 라켓 페이스 정타율 (전체 중 `SQUARE` 비율 %)
    - 평균 운동 체인 에너지 전달 효율 (%)
  - **액션 버튼**:
    1. **[ 🎬 방금 친 스윙 리플레이 보기 ]**: `TASK-039`의 `LabReplayScreen`으로 즉시 진입.
    2. **[ 닫기 / 새 훈련 시작 ]**: 다이얼로그를 닫고 새로운 드릴 선택 상태로 복귀.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.feature.lab.ui

enum class CameraFacingMode {
    FRONT, // 전면 셀프 트레이닝 모드
    BACK   // 후면 코칭/관찰 모드
}

data class FarFieldHudState(
    val faceText: String,
    val faceColorHex: Long,
    val energyEfficiency: Float,
    val isSquare: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

data class SessionCompletionSummary(
    val sessionId: String,
    val drillName: String,
    val totalSwingCount: Int,
    val durationSeconds: Long,
    val squareRatePercent: Int,
    val averageEnergyEfficiency: Float
)
```

```kotlin
package io.github.loje0611.tennisdoc.feature.lab.ui

data class LabUiState(
    val selectedDrill: DrillType = DrillType.FOREHAND,
    val isSessionActive: Boolean = false,
    val sessionDurationSeconds: Long = 0L,
    val swingCount: Int = 0,
    val isSensorConnected: Boolean = false,
    val isSensorScanning: Boolean = false,
    val isDebugModeEnabled: Boolean = false,
    val cameraFacingMode: CameraFacingMode = CameraFacingMode.FRONT,
    val countdownSeconds: Int? = null,
    val farFieldHud: FarFieldHudState? = null,
    val completionSummary: SessionCompletionSummary? = null,
    val latestFusedSwing: FusedSwing? = null,
    val latestAnomalyReport: BaselineComparisonReport? = null
)
```

---

## 5. UI/UX 요구사항
- **고대비 시인성**: 3m 거리에서도 식별 가능하도록 HUD 글자 크기(최소 36sp)와 두꺼운 폰트(ExtraBold), 네온 테두리 적용.
- **부드러운 애니메이션**: 카운트다운 숫자 전환 시 Scale In/Out, HUD 등장/퇴장 시 300ms 페이드 효과.
- **안전한 모드 전환**: 카메라 전환 시 프레임 드랍 없이 1초 이내에 CameraX 렌즈 재바인딩 완료.

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 성능 최적화
- 테두리 컬러 펄스는 `Canvas` 또는 `Modifier.drawWithContent`를 사용하여 불필요한 전체 리컴포지션 방지.
- 5초 카운트다운 및 3초 HUD 타이머는 `viewModelScope` 코루틴을 통해 안전하게 취소 및 라이프사이클 관리.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **카운트다운 중 화면 이탈 또는 취소**: 타이머 코루틴 즉시 캔슬 및 세션 시작 취소.
- **전면 카메라가 지원되지 않는 특수 기기**: 카메라 Provider 바인딩 에러 시 후면 카메라로 안전하게 Fallback.
- **센서 미연결 상태에서 카운트다운 시도**: 카운트다운 진입 전 센서 연결 여부를 사전 차단하고 Toast("센서를 먼저 연결해 주세요") 출력.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: 상단 헤더에 카메라 전환 버튼(`🔄`)이 구현되고, 클릭 시 `FRONT` ↔ `BACK` 모드가 전환된다.
- [ ] **AC-2**: 전면 카메라(`FRONT`) 선택 시 카메라 프리뷰 및 스켈레톤 오버레이가 수평 미러링(좌우 반전)된다.
- [ ] **AC-3**: 전면 카메라에서 [측정 시작] 클릭 시 **5초 카운트다운(`5 ➔ 4 ➔ 3 ➔ 2 ➔ 1 ➔ 시작!`)** 오버레이 및 초 단위 사운드가 재생된 후 세션이 시작된다.
- [ ] **AC-4**: 후면 카메라에서 [측정 시작] 클릭 시 카운트다운 없이 즉시 세션이 시작된다.
- [ ] **AC-5**: 전면 카메라에서 스윙 감지 시 3초간 화면 중앙에 대형 HUD 팝업과 화면 외곽 컬러 테두리 펄스(정타 초록 / 결함 빨강)가 렌더링된다.
- [ ] **AC-6**: 전면 카메라에서는 스윙 직후 TTS 음성이 재생되고, 후면 카메라에서는 TTS가 음소거된다.
- [ ] **AC-7**: [측정 종료] 클릭 시 성적 요약 모달(`SessionCompletionDialog`)이 팝업되며, [리플레이 보기] 클릭 시 `LabReplayScreen`으로 이동한다.
- [ ] **AC-8**: `./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
```
