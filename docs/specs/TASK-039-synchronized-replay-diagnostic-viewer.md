# TASK-039 — 동기 리플레이(Synchronized Replay) 및 정밀 진단 뷰어 구현

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-15 | PM | 최초 작성 (Phase 3 D그룹 완결: 비전 포즈 시계열과 IMU 파형 타임라인 동기 락킹 시크바, 슬로우/프레임 스텝 제어, 툴팁 배치 불변식 및 5단계 운동체인 정밀 진단 뷰어 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Phase 3 Lab 모드의 최종 완결 컴포넌트인 **동기 리플레이(Synchronized Replay) 및 정밀 진단 뷰어**를 `:feature:lab` 모듈에 구현하는 작업을 규정합니다.

단일 스윙(`FusedSwing`)에서 캡처된 30fps 비전 포즈 시계열(`PoseFrame`)과 50Hz 센서 파형 시계열(`ImuDataPoint`)을 임팩트 앵커(`SyncAnchor`) 기반으로 **1:1 시간축 동기 락킹(Synchronized Timeline Locking)**하여, 사용자가 스윙 전 과정을 밀리초(ms)/프레임 단위로 탐색(Seek)하고 분석할 수 있는 인터랙티브 UI를 제공합니다. 또한 임팩트 순간 진단 툴팁은 [`TASK-007`](TASK-007-overlay-rendering.md)에 정의된 **툴팁 배치 불변식(상호 비겹침 INV-1, 프레임 경계 내부 INV-2 등)**을 엄격히 준수합니다.

### 1.2 범위
- `:feature:lab` 모듈 내 리플레이 UI 컴포넌트 구현 (`io.github.loje0611.tennisdoc.feature.lab.replay`):
  - `LabReplayScreen`: 전체 동기 리플레이 화면 컨테이너 및 Scaffold 구성.
  - `PoseReplayCanvas`: 타임라인 시점의 33개 관절 랜드마크 스켈레톤 및 툴팁 오버레이 캔버스.
  - `SynchronizedTimelineController`: 재생/일시정지, 0.5x 슬로우모션, 1프레임 전/후(±33ms) 스텝, 임팩트 앵커 점프 버튼 및 슬라이더 시크바.
  - `ImuWaveformChart`: 50Hz 가속도 합성 크기($|a| = \sqrt{a_x^2 + a_y^2 + a_z^2}$) 및 자이로 각속도 파형 그래프와 현재 시간 수직 커서(Cursor Line).
  - `KineticChainSummaryCard`: 5단계 운동 체인 순서/에너지 전달 효율(%) 및 센서-비전 융합 인과 코칭 문구 요약 카드.
  - `DiagnosticTooltipLayout`: [`TASK-007`](TASK-007-overlay-rendering.md)의 툴팁 배치 불변식(INV-1~INV-5)을 만족하는 배치 알고리즘 컴포넌트.
- `LabReplayViewModel` 구현:
  - `FusedSwing` 인입 시 타임라인 총 길이(`durationMs`), 비전-센서 동기화 오프셋(`SyncAnchor.timeOffsetMs`) 계산 및 상태 관리.
  - 현재 탐색 시점(`currentTimestampMs`)에 따른 인접 `PoseFrame` 및 `ImuDataPoint` 인덱스 계산.
  - 재생 루프(Tick) 및 속도(0.5x / 1.0x) 제어 Coroutine Flow.
- 단위 테스트 및 Compose UI 렌더링 검증:
  - 시간축 동기화 매핑 계산 단위 테스트 (`LabReplayViewModelTest`).
  - 시크바 조작 및 재생/정지 상태 전이 테스트.
  - 툴팁 겹침 방지 불변식 검증 단위 테스트.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **임팩트 앵커 동기화 (`SyncAnchor`)**: 비전 손목 속도 피크($t_{\text{vision}}$)와 센서 IMU 피크($t_{\text{sensor}}$) 간의 시간 오차 `timeOffsetMs = sensorImpactTimestampMs - visionImpactTimestampMs`를 보정하여 단일 기준 타임라인으로 정렬하는 계약.
- **타임라인 동기 락킹**: 시크바 시간 $t$를 이동했을 때, 비전 프레임($t$)과 IMU 파형($t + \text{timeOffsetMs}$)의 커서가 물리적으로 동일한 스윙 순간을 가리키도록 결합하는 UI 메커니즘.
- **툴팁 배치 불변식 ([TASK-007](TASK-007-overlay-rendering.md))**:
  - **INV-1 (상호 비겹침)**: 서로 다른 툴팁 사각형 간의 교집합 면적은 0이며, 최소 여백(`MIN_GAP = 10dp`) 이상을 유지한다.
  - **INV-2 (프레임 내부)**: 모든 툴팁 박스는 캔버스 화면 경계 내에 완전히 포함된다.
  - **INV-3 (결정성)**: 동일한 입력 데이터에 대해 툴팁 배치 결과는 항상 동일해야 한다.

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 융합 도메인 모델: [`docs/specs/TASK-031-core-fusion-module-setup.md`](TASK-031-core-fusion-module-setup.md)
- 임팩트 앵커 동기화: [`docs/specs/TASK-032-impact-anchor-synchronization.md`](TASK-032-impact-anchor-synchronization.md)
- 툴팁 렌더링 불변식: [`docs/specs/TASK-007-overlay-rendering.md`](TASK-007-overlay-rendering.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 동기화 타임라인 제어 (`SynchronizedTimelineController`)
- **타임라인 범위**: 스윙 시작 시점($t = 0\text{ms}$)부터 스윙 종료 시점($t = \text{durationMs}$)까지 연속 슬라이더를 제공한다.
- **현재 탐색 시점 ($t_{\text{current}}$)**:
  - 슬라이더 드래그 또는 탭 시 $t_{\text{current}}$를 갱신한다.
  - 비전 캔버스는 $t_{\text{current}}$와 가장 타임스탬프 차이가 적은 `PoseFrame`을 렌더링한다.
  - IMU 파형 차트는 $t_{\text{current}} + \text{timeOffsetMs}$에 해당하는 위치에 수직 강조선(Cursor)을 즉각 이동시킨다.
- **임팩트 앵커 마커 및 퀵 점프**:
  - 타임라인 상에 임팩트 순간($t_{\text{impact}}$)을 주황/적색 마커 핀으로 표시한다.
  - "🎯 임팩트" 퀵 점프 버튼을 누르면 $t_{\text{current}} = t_{\text{impact}}$로 즉시 이동한다.
- **재생 컨트롤 바**:
  - `Play / Pause`: 자동 재생(기본 30fps 주기 타이머) 및 일시정지 토글.
  - `Speed Toggle`: 1.0x(일반 재생) ↔ 0.5x(슬로우 모션) 토글.
  - `Step Back (-1 Frame)`: $t_{\text{current}} = \max(0, t_{\text{current}} - 33\text{ms})$.
  - `Step Forward (+1 Frame)`: $t_{\text{current}} = \min(\text{durationMs}, t_{\text{current}} + 33\text{ms})$.

### FR-2: 포즈 스켈레톤 리플레이 캔버스 (`PoseReplayCanvas`)
- 선택된 프레임의 33개 관절 좌표를 정규화 좌표(0.0~1.0)에서 캔버스 크기에 맞추어 2D 스켈레톤으로 렌더링한다:
  - 관절 키포인트: 반경 4dp 원형 마커 (Primary Accent).
  - 뼈대 연결선: 두께 2dp 선 (어깨-팔꿈치-손목, 골반-무릎-발목, 척추/상체 라인).
- 임팩트 시점($|t_{\text{current}} - t_{\text{impact}}| \le 33\text{ms}$)인 경우 캔버스 상단에 `IMPACT!` 뱃지를 강조 표시한다.

### FR-3: 툴팁 배치 불변식 준수 진단 오버레이 (`DiagnosticTooltipLayout`)
- 스윙 중 결함이 발견된 관절(예: 골반 조기 회전, 손목 지연 등)에 인과 툴팁을 표시할 때 아래 **불변식**을 만족해야 한다:
  - **INV-1 (상호 비겹침)**: 2개 이상의 툴팁이 렌더링될 때 툴팁 박스 간 겹침 면적은 **0**이다.
  - **INV-2 (프레임 내부)**: 모든 툴팁 박스는 캔버스 가로/세로 경계 `[0, width] x [0, height]` 내에 완전히 포함된다.
  - **INV-3 (근접성 및 지시선)**: 툴팁 박스는 대상 관절 좌표와 지시선(Line)으로 연결되며, 충돌이 없는 한 대상 관절에 가장 가깝게 배치된다.

### FR-4: IMU 다축/합성 파형 차트 (`ImuWaveformChart`)
- 50Hz IMU 데이터의 합성 가속도($|a| = \sqrt{a_x^2 + a_y^2 + a_z^2}$) 및 합성 각속도($|\omega| = \sqrt{\omega_x^2 + \omega_y^2 + \omega_z^2}$) 시계열 곡선을 Canvas로 렌더링한다.
- 차트 상에 5단계 운동 체인의 피크 시점(골반, 어깨, 손목, 라켓, 임팩트)을 색상별 점 마커로 시각화한다.
- 현재 재생 시간 $t_{\text{current}} + \text{timeOffsetMs}$에 해당하는 위치에 수직 커서 라인을 표시하여 실시간 락킹을 시각적으로 확인하도록 한다.

### FR-5: 5단계 운동 체인 및 인과 진단 요약 카드 (`KineticChainSummaryCard`)
- 5단계 가속 순서 인디케이터: 골반 ➔ 어깨 ➔ 손목 ➔ 라켓 ➔ 임팩트.
- 순차 가속 여부(`isSequential == true`: 정상/녹색, `false`: 비순차/적색) 및 에너지 전달 효율(예: `88%`).
- 라켓 페이스 상태 뱃지 (`SQUARE`, `OPEN`, `CLOSED`) 및 각도 편차.
- 인과 진단 설명(`causalExplanation`) 및 교정 가이드(`coachingFeedback`).

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.feature.lab.replay

import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

data class ReplayTooltip(
    val targetJointIndex: Int,
    val jointX: Float,
    val jointY: Float,
    val text: String
)

data class TooltipBoxRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val tooltip: ReplayTooltip
)

data class LabReplayUiState(
    val fusedSwing: FusedSwing? = null,
    val durationMs: Long = 0L,
    val currentTimestampMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val currentPoseFrame: PoseFrame? = null,
    val currentImuPoint: ImuDataPoint? = null,
    val isImpactFrame: Boolean = false,
    val tooltips: List<ReplayTooltip> = emptyList()
)
```

```kotlin
package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

object TooltipPlacementCalculator {
    const val MIN_GAP: Float = 10f

    /**
     * TASK-007 INV-1~INV-3을 만족하도록 툴팁 박스 위치를 계산한다.
     */
    fun computePlacement(
        tooltips: List<ReplayTooltip>,
        boxSizes: List<Size>,
        canvasSize: Size
    ): List<Rect>
}
```

---

## 5. UI/UX 요구사항
- **시인성 및 대비**: 상단 스켈레톤 영역과 하단 IMU 파형 영역은 명확한 시각적 구분을 제공하고, 타임 커서는 밝은 형광/흰색으로 뚜렷하게 표시.
- **부드러운 조작감**: 시크바 드래그 시 60fps로 프레임 전환이 즉각 반응해야 하며 버벅임이 없어야 함.
- **터치 타겟**: 재생/일시정지, 프레임 스텝 버튼은 최소 48dp 터치 영역 보장.

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 성능 및 렌더링 최적화
- IMU 파형 및 스켈레톤 캔버스는 불필요한 Recomposition을 방지하기 위해 `drawWithCache` 또는 독립 Composable 분리 적용.
- $t_{\text{current}}$ 탐색 시 Binary Search(이진 탐색)를 활용하여 $O(\log N)$으로 포즈 프레임 및 IMU 포인트를 색인.

### 6.2 모듈 의존성 규칙 준수
- `:feature:lab`은 `:core:ui`, `:core:model`, `:core:fusion`, `:core:data`, `:core:vision`을 참조하며 단방향 아키텍처를 준수함.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **`fusedSwing` 데이터가 null이거나 프레임이 빈 경우**: "리플레이 데이터가 없습니다" 안내 문구 표시 및 시크바 비활성화.
- **비전 또는 센서 타임스탬프 불일치/결측**: 타임라인 범위를 벗어난 경우 가장 가까운 첫/마지막 프레임으로 클램핑.
- **툴팁이 화면 경계를 벗어나는 경우**: INV-2에 따라 화면 안쪽으로 자동 클램핑.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `LabReplayScreen`, `PoseReplayCanvas`, `SynchronizedTimelineController`, `ImuWaveformChart`, `KineticChainSummaryCard` 컴포저블이 `:feature:lab`에 구현된다.
- [ ] **AC-2**: 시크바 위치 변경 시 비전 포즈 프레임과 IMU 파형 커서가 `SyncAnchor.timeOffsetMs`에 따라 정확히 동기화되어 갱신된다.
- [ ] **AC-3**: "🎯 임팩트" 퀵 점프 클릭 시 $t_{\text{current}} = t_{\text{impact}}$로 즉시 이동하고 `IMPACT!` 강조 표시가 활성화된다.
- [ ] **AC-4**: 재생(Play), 일시정지(Pause), 0.5x 슬로우모션, ±33ms(1프레임) 스텝 버튼이 정상 동작한다.
- [ ] **AC-5**: `TooltipPlacementCalculator`가 계산한 툴팁 박스들은 상호 교집합 면적이 0(INV-1)이고 캔버스 경계 내(INV-2)에 위치한다.
- [ ] **AC-6**: 5단계 운동 체인 순서/효율 및 융합 인과 코칭 문구가 정상적으로 렌더링된다.
- [ ] **AC-7**: `LabReplayViewModelTest` 및 단위 테스트가 100% 통과한다.
- [ ] **AC-8**: `./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
```
