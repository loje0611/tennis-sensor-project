# TASK-023 — `:core:vision` 손목 Y 궤적 기울기 기반 스윙 구종 분류 (`src/swing_path.py` 포팅)

| 항목 | 값 |
|---|---|
| Task ID | TASK-023 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-009, TASK-022 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-11 | PM | 최초 작성 (Phase 2 B그룹 비전 스윙 궤적 분류 알고리즘 Kotlin 포팅) |

---

## 1. 개요 및 범위

### 1.1 개요
Phase 1 Python 프로토타입(`tennis-vision-analyzer/src/swing_path.py`)으로 검증된 임팩트 프레임 전후 손목 궤적의 1차 선형 회귀(Linear Regression Slope) 기반 구종 분류 알고리즘(Topspin, Flat, Slice)을 순수 Kotlin/JVM 모듈인 `:core:vision`으로 포팅한다.

D-9.2 원칙에 따라 순수 `kotlin("jvm")` 모듈에 구현하며, NumPy `polyfit(x, y, 1)` 기울기 연산을 Kotlin 수학 알고리즘으로 동등 구현하여 입출력 골든 픽스처(JSON) 기반으로 수치적 정확도를 검증한다.

### 1.2 범위
- **포함**:
  - Y 좌표 시퀀스에 대한 1차 선형 회귀 기울기 계산 유틸리티 (`SwingPathClassifier.calculateLinearSlope`).
  - 임팩트 프레임 전후 윈도우(`analysisWindow = 10`) 손목 Y 궤적 기울기 계산 및 구종 분류 알고리즘 (`SwingPathClassifier.classifySwingPath` — `Topspin`, `Flat`, `Slice`, `Unknown`).
  - 손목 3D 궤적 데이터 추출 함수 (`SwingPathClassifier.getWristTrajectory3d`).
  - Python 쪽 골든 픽스처 추출 스크립트 작성 및 `golden_swing_path_fixture.json` 생성 및 `:core:vision/src/test/resources/` 등록.
  - `:core:vision/src/test/`에 골든 픽스처 기반 수치 검증 테스트 (`SwingPathClassifierTest.kt`) 구축.
- **제외**:
  - 운동 체인 분석 및 스윙 진단 코칭 텍스트 생성 (TASK-024, TASK-025 범위).
  - Android UI 3D 그래프 렌더링.

---

## 2. 정의 및 참조

- **참조 소스**: [`tennis-vision-analyzer/src/swing_path.py`](file:///home/keunu/personal-project/tennis-sensor-project/tennis-vision-analyzer/src/swing_path.py)
- **선행 명세**: [`docs/specs/TASK-022-core-vision-impact-detector.md`](TASK-022-core-vision-impact-detector.md)
- **관련 계획**: [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 (B그룹 골든 픽스처 원칙)

---

## 3. 기능 요구사항

### FR-1. 1차 선형 회귀 기울기 계산 (`SwingPathClassifier.calculateLinearSlope`)
`io.github.loje0611.tennisdoc.core.vision.analyzer.SwingPathClassifier` 클래스/오브젝트에 구현한다:
- 입력: `yValues: DoubleArray` (또는 `FloatArray`)
- 처리 로직:
  1. `N = yValues.size`. $N < 2$ 이면 `Double.NaN` 반환.
  2. $x = 0, 1, \dots, N-1$ 에 대해:
     - $\sum x = \frac{(N-1)N}{2}$
     - $\sum x^2 = \frac{(N-1)N(2N-1)}{6}$
     - $\sum y = \sum_{i=0}^{N-1} y_i$
     - $\sum (x y) = \sum_{i=0}^{N-1} (i \cdot y_i)$
  3. 분모 $denom = N \sum x^2 - (\sum x)^2$ 계산. $denom == 0.0$ 이면 `Double.NaN` 반환.
  4. 기울기 $slope = \frac{N \sum(x y) - (\sum x)(\sum y)}{denom}$ 계산 및 반환.

### FR-2. 손목 궤적 기반 스윙 구종 분류 (`SwingPathClassifier.classifySwingPath`)
- 입력: `poseFrames: List<PoseFrame>`, `impactFrame: Int?`, `isRightHand: Boolean = true`, `analysisWindow: Int = 10`
- 반환 타입: `SwingPathType` (`TOPSPIN`, `FLAT`, `SLICE`, `UNKNOWN`) 및 해당 키 문자열(`"Topspin"`, `"Flat"`, `"Slice"`, `"Unknown"`)
- 처리 로직:
  1. `impactFrame == null` 이거나 `poseFrames.isEmpty()` 인 경우 `UNKNOWN` (`"Unknown"`) 반환.
  2. 손목 관절 인덱스: `wristIndex = if (isRightHand) 16 else 15`.
  3. 분석 구간 산출:
     - `startFrame = maxOf(0, impactFrame - analysisWindow)`
     - `endFrame = minOf(poseFrames.size, impactFrame + analysisWindow)`
  4. `startFrame` ~ `endFrame` (exclusive) 구간 내에서 $wristIndex$ 번 랜드마크의 Y 좌표 중 `!isNan` 인 값들만 `yTrajectory` 배열로 추출.
  5. `yTrajectory.size < 2` 인 경우 `UNKNOWN` (`"Unknown"`) 반환.
  6. `slope = calculateLinearSlope(yTrajectory)`. `slope.isNaN()` 인 경우 `UNKNOWN` 반환.
  7. 임계값 $THRESHOLD = 0.005$ 판단 (MediaPipe 좌표계에서 Y=0 상단, Y=1 하단):
     - `slope < -0.005` (Y 감소 = 라켓 상향 궤적) -> `TOPSPIN` (`"Topspin"`)
     - `slope > 0.005` (Y 증가 = 라켓 하향 궤적) -> `SLICE` (`"Slice"`)
     - 수평 및 그 외 -> `FLAT` (`"Flat"`)

### FR-3. 손목 3D 궤적 추출 (`SwingPathClassifier.getWristTrajectory3d`)
- 입력: `poseFrames: List<PoseFrame>`, `isRightHand: Boolean = true`
- 처리 로직:
  - 각 프레임의 손목 랜드마크 중 `!isNan` 인 랜드마크 리스트만 필터링하여 반환 (`List<PoseLandmark>`).

### FR-4. Python 입출력 골든 픽스처 추출 및 수치 동일성 단위 테스트
- `tennis-vision-analyzer/` 스크립트를 확장하여 상향 궤적(Topspin), 평행 궤적(Flat), 하향 궤적(Slice), 프레임 경계 및 결측치 입력에 대해 Python `swing_path.py`를 실행한 결과(slope값 및 분류 결과)가 포함된 `golden_swing_path_fixture.json` 파일 생성.
- 생성된 JSON 픽스처를 `:core:vision/src/test/resources/golden_swing_path_fixture.json`에 배치.
- `:core:vision/src/test/`에 `SwingPathClassifierTest.kt`를 작성하여 Kotlin 기울기 계산 결과가 Python 결과와 `1e-5` 이내이고 구종 분류 결과가 100% 동일함을 검증.

---

## 4. 인터페이스 및 데이터 구조

```kotlin
package io.github.loje0611.tennisdoc.core.vision.analyzer

enum class SwingPathType(val key: String) {
    TOPSPIN("Topspin"),
    FLAT("Flat"),
    SLICE("Slice"),
    UNKNOWN("Unknown")
}

object SwingPathClassifier {
    fun calculateLinearSlope(yValues: DoubleArray): Double

    fun classifySwingPath(
        poseFrames: List<PoseFrame>,
        impactFrame: Int?,
        isRightHand: Boolean = true,
        analysisWindow: Int = 10
    ): SwingPathType

    fun getWristTrajectory3d(
        poseFrames: List<PoseFrame>,
        isRightHand: Boolean = true
    ): List<PoseLandmark>
}
```

---

## 5. 인수 조건 (Acceptance Criteria)

| # | 조건 |
|---|---|
| **AC-1** | `./gradlew :core:vision:assembleDebug` 및 `./gradlew :core:vision:test` 통과. |
| **AC-2** | `./gradlew test` 통과 (기존 테스트 회귀 없음). |
| **AC-3** | `./gradlew verifyModuleDependencies verifyJniBindings` 통과. |
| **AC-4** | `:core:vision` 모듈이 순수 Kotlin/JVM 모듈로 유지되며 Android SDK 의존성이 0건임. |
| **AC-5** | `golden_swing_path_fixture.json` 파일이 `:core:vision/src/test/resources/`에 배치되어 있음. |
| **AC-6** | **(골든 픽스처 동일성 검증)** `SwingPathClassifierTest`에서 구종 분류 결과가 Python 결과와 **100% 완벽히 일치**하며, 기울기 계산 오차가 **`1e-5` 이내**임. |
| **AC-7** | `impactFrame`이 `null`이거나 프레임 데이터가 부족할 때 예외 없이 `SwingPathType.UNKNOWN` ("Unknown")이 반환됨. |
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
