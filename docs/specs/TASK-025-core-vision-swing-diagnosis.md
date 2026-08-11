# TASK-025 — `:core:vision` 스윙 종합 진단 및 피드백 태그 생성 (`src/swing_diagnosis.py` 포팅)

| 항목 | 값 |
|---|---|
| Task ID | TASK-025 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-009, TASK-021, TASK-023, TASK-024 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-11 | PM | 최초 작성 (Phase 2 B그룹 비전 스윙 종합 진단 및 피드백 생성 알고리즘 Kotlin 포팅) |

---

## 1. 개요 및 범위

### 1.1 개요
Phase 1 Python 프로토타입(`tennis-vision-analyzer/src/swing_diagnosis.py`)으로 검증된 임팩트 프레임별 관절 각도, 구종, 운동 체인 윈도우 속도 피크 분석 기반 스윙 진단 및 시각화용 피드백 태그 생성 알고리즘을 순수 Kotlin/JVM 모듈인 `:core:vision`으로 포팅한다.

D-9.2 원칙에 따라 순수 `kotlin("jvm")` 모듈에 구현하며, B그룹의 이전 포팅 결과들(TASK-021 관절 각도, TASK-023 구종, TASK-024 운동 체인 속도)의 통합 진단 로직을 구현하고 입출력 골든 픽스처(JSON) 기반으로 수치적/텍스트적 정확도를 일치시킨다.

### 1.2 범위
- **포함**:
  - 피드백 아이템 및 종합 진단 결과 데이터 모델 선언 (`FeedbackItem`, `SwingDiagnosisResult`).
  - 임팩트 윈도우(`-1.0s` ~ `+0.5s`) 내 골반/어깨/손목 피크 탐색 및 가속 순서 결함 감지 (`Use Hip First`, `Late Wrist`).
  - 팔 펴짐 각도(< 120도)에 따른 타점 지표 결함 감지 (`Arm Bent`).
  - 구종 분류(`Flat`, `Slice`)에 따른 상향 궤적 결함 감지 (`Low Path`).
  - 문제 없는 스윙에 대한 칭찬 태그 생성 (`Good Swing!`).
  - Python 쪽 골든 픽스처 추출 스크립트 작성 및 `golden_swing_diagnosis_fixture.json` 생성 및 `:core:vision/src/test/resources/` 등록.
  - `:core:vision/src/test/`에 골든 픽스처 기반 수치 검증 테스트 (`SwingDiagnosisBuilderTest.kt`) 구축.
- **제외**:
  - Android Compose 오버레이 렌더링 및 UI 툴팁 배치 (Phase 3 범위).
  - LLM 기반 자연어 리포트 생성 (Phase 4 범위).

---

## 2. 정의 및 참조

- **참조 소스**: [`tennis-vision-analyzer/src/swing_diagnosis.py`](file:///home/keunu/personal-project/tennis-sensor-project/tennis-vision-analyzer/src/swing_diagnosis.py)
- **선행 명세**: [`docs/specs/TASK-021-core-vision-angle-calculator.md`](TASK-021-core-vision-angle-calculator.md), [`docs/specs/TASK-023-core-vision-swing-path-classifier.md`](TASK-023-core-vision-swing-path-classifier.md), [`docs/specs/TASK-024-core-vision-kinetic-chain.md`](TASK-024-core-vision-kinetic-chain.md)
- **관련 계획**: [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 (B그룹 골든 픽스처 원칙)

---

## 3. 기능 요구사항

### FR-1. 스윙 진단 및 피드백 데이터 모델 정의
`io.github.loje0611.tennisdoc.core.vision.model` 패키지에 다음 데이터 클래스를 작성한다:
```kotlin
data class FeedbackItem(
    val text: String,
    val targetJoint: Int
)

data class SwingDiagnosisResult(
    val swingFeedbacks: Map<Int, List<FeedbackItem>>,
    val allProblems: List<String>
)
```

### FR-2. 스윙 종합 진단 및 피드백 생성 (`SwingDiagnosisBuilder.buildSwingFeedbacks`)
`io.github.loje0611.tennisdoc.core.vision.analyzer.SwingDiagnosisBuilder` 클래스/오브젝트에 구현한다:
- 입력: `impactFrames: List<Int>`, `swingTypes: List<String>`, `armAngles: List<Double>`, `chainVelocities: JointVelocities?`, `fps: Float = 30f`
- 처리 로직:
  1. 각 임팩트 프레임 `frame` (인덱스 `i`)에 대해 순회 수행:
     - `stype = swingTypes.getOrElse(i) { "Unknown" }`
     - `armAngle = if (frame in armAngles.indices) armAngles[frame] else 0.0`
     - `feedbacks = mutableListOf<FeedbackItem>()`
  2. **운동 체인 윈도우 분석**:
     - `chainVelocities != null` 인 경우:
       - `startF = maxOf(0, (frame - fps * 1.0f).toInt())`
       - `endF = minOf(chainVelocities.hip.size, (frame + fps * 0.5f).toInt())`
       - `startF < endF` 인 경우 해당 구간 `[startF, endF)` 내에서 골반(`hip`), 어깨(`shoulder`), 손목(`wrist`) 속도의 최대값 인덱스(`peakHip`, `peakShoulder`, `peakWrist`) 계산.
       - `peakHip >= peakShoulder` 이면 `feedbacks.add(FeedbackItem("Use Hip First", 24))` 추가 및 `allProblems.add("운동 체인(하체->상체 순서)")` 기록.
       - 그렇지 않고 `peakShoulder >= peakWrist` 이면 `feedbacks.add(FeedbackItem("Late Wrist", 16))` 추가 및 `allProblems.add("팔/손목 가속")` 기록.
  3. **팔 펴짐 각도 분석**:
     - `armAngle < 120.0` 인 경우:
       - `val angleStr = String.format(Locale.US, "%.0f", armAngle)`
       - `feedbacks.add(FeedbackItem("Arm Bent($angleStr)", 14))` 추가 및 `allProblems.add("타점(팔 각도)")` 기록.
  4. **스윙 궤적 구종 분석**:
     - `stype in listOf("Flat", "Slice")` 인 경우:
       - `feedbacks.add(FeedbackItem("Low Path", 16))` 추가 및 `allProblems.add("상향 스윙 궤적")` 기록.
  5. **Good Swing 처리**:
     - `feedbacks.isEmpty()` 인 경우:
       - `feedbacks.add(FeedbackItem("Good Swing!", 12))` 추가.
  6. `swingFeedbacks[frame] = feedbacks` 매핑 등록.
  7. `SwingDiagnosisResult(swingFeedbacks, allProblems)` 반환.

### FR-3. Python 입출력 골든 픽스처 추출 및 테스트
- `tennis-vision-analyzer/` 스크립트를 확장하여 정상 스윙, 팔이 굽은 스윙, 운동 체인 순서가 꼬인 스윙, Slice/Flat 스윙이 믹스된 데이터에 대해 Python `build_swing_feedbacks`를 실행한 결과가 포함된 `golden_swing_diagnosis_fixture.json` 생성 및 `:core:vision/src/test/resources/` 등록.
- `:core:vision/src/test/`에 `SwingDiagnosisBuilderTest.kt`를 작성하여 Kotlin 분석 결과의 `swingFeedbacks` 및 `allProblems` 텍스트와 관절 타겟 인덱스가 Python 결과와 **100% 동일**함을 단위 테스트로 검증.

---

## 4. 인터페이스 및 데이터 구조

```kotlin
package io.github.loje0611.tennisdoc.core.vision.analyzer

object SwingDiagnosisBuilder {
    fun buildSwingFeedbacks(
        impactFrames: List<Int>,
        swingTypes: List<String>,
        armAngles: List<Double>,
        chainVelocities: JointVelocities?,
        fps: Float = 30f
    ): SwingDiagnosisResult
}
```

---

## 5. 인수 조건 (Acceptance Criteria)

| # | 조건 |
|---|---|
| **AC-1** | `./gradlew :core:vision:assembleDebug` 및 `./gradlew :core:vision:test` 통과. |
| **AC-2** | `./gradlew test` 통과 (B그룹 전체 포팅 알고리즘 단위 테스트 포함 100% 통과). |
| **AC-3** | `./gradlew verifyModuleDependencies verifyJniBindings` 통과. |
| **AC-4** | `:core:vision` 모듈이 순수 Kotlin/JVM 모듈로 유지되며 Android SDK 의존성이 0건임. |
| **AC-5** | `golden_swing_diagnosis_fixture.json` 파일이 `:core:vision/src/test/resources/`에 배치되어 있음. |
| **AC-6** | **(골든 픽스처 동일성 검증)** `SwingDiagnosisBuilderTest`에서 피드백 문구(`text`), 관절 인덱스(`targetJoint`), 그리고 `allProblems` 리스트 결과가 Python 결과와 **100% 완벽히 일치**함. |
| **AC-7** | `impactFrames`가 비어있거나 `chainVelocities`가 `null`인 경우에도 예외 없이 빈 맵 또는 기본 피드백이 안전하게 생성됨. |
| **AC-8** | 변경 및 신규 소스 경로가 `TennisDocAI/core/vision/`, `tennis-vision-analyzer/` (픽스처 스크립트), 명세/보드 문서에 한정됨. |

---

## 6. 테스트 지침

명령어 실행 위치: `TennisDocAI/`

1. `:core:vision` 모듈 단위 테스트 실행:
   ```bash
   ./gradlew :core:vision:test
   ```
2. 전체 빌드 및 검증 태스크 실행:
   ```bash
   ./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
   ```
