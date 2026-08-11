# TASK-022 — `:core:vision` 3D 관절 속도 계산 및 다중 스윙 임팩트 프레임 감지 (`src/impact_detector.py` 포팅)

| 항목 | 값 |
|---|---|
| Task ID | TASK-022 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-009, TASK-021 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-11 | PM | 최초 작성 (Phase 2 B그룹 비전 속도 및 임팩트 감지 알고리즘 Kotlin 포팅) |

---

## 1. 개요 및 범위

### 1.1 개요
Phase 1 Python 프로토타입(`tennis-vision-analyzer/src/impact_detector.py`)으로 검증된 관절 3D 이동 속도 계산, 1D 가우시안 노이즈 필터링, 그리고 피크 감지(Peak Detection) 기반 다중 스윙 임팩트 프레임 추정 알고리즘을 순수 Kotlin/JVM 모듈인 `:core:vision`으로 포팅한다.

D-9.2 원칙에 따라 순수 `kotlin("jvm")` 모듈에 구현하며, Scipy의 `gaussian_filter1d` 및 `find_peaks` 알고리즘을 Kotlin 알고리즘으로 동등 포팅하여 입출력 골든 픽스처(JSON) 기반으로 수치적 정확도를 완벽히 검증한다.

### 1.2 범위
- **포함**:
  - 연속된 `PoseFrame` 리스트에서 특정 관절(기본: 손목)의 3D 공간 유클리디안 이동 속도(초당 거리) 계산 (`ImpactDetector.calculateVelocity`).
  - 1D 가우시안 스무딩 노이즈 필터 함수 구현 (`ImpactDetector.gaussianFilter1d`, `sigma = 2.0f`).
  - Scipy `find_peaks` 매개변수(`height`, `distance`, `prominence`) 동등 피크 감지 알고리즘 및 다중 스윙 임팩트 프레임 추정 (`ImpactDetector.detectImpactFrames`).
  - Python 쪽 골든 픽스처 추출 스크립트 작성 및 `golden_impact_fixture.json` 생성 및 `:core:vision/src/test/resources/` 등록.
  - `:core:vision/src/test/`에 골든 픽스처 기반 테스트 (`ImpactDetectorTest.kt`) 구축.
- **제외**:
  - Android UI 렌더링, 오버레이 및 동영상 인코딩 (Phase 3 범위).
  - 스윙 궤적 분류(Topspin/Flat/Slice) 및 운동 체인 분석 (TASK-023, TASK-024 범위).

---

## 2. 정의 및 참조

- **참조 소스**: [`tennis-vision-analyzer/src/impact_detector.py`](file:///home/keunu/personal-project/tennis-sensor-project/tennis-vision-analyzer/src/impact_detector.py)
- **선행 명세**: [`docs/specs/TASK-021-core-vision-angle-calculator.md`](TASK-021-core-vision-angle-calculator.md)
- **관련 계획**: [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 (B그룹 골든 픽스처 원칙)

---

## 3. 기능 요구사항

### FR-1. 관절 3D 이동 속도 계산 (`ImpactDetector.calculateVelocity`)
`io.github.loje0611.tennisdoc.core.vision.analyzer.ImpactDetector` 클래스/오브젝트에 구현한다:
- 입력: `poseFrames: List<PoseFrame>`, `jointIndex: Int`, `fps: Float = 30f`
- 처리 로직:
  1. `poseFrames.size < 2` 인 경우 빈 배열/리스트 반환.
  2. 프레임 $i$ ($i \ge 1$)와 프레임 $i-1$ 간의 관절 $jointIndex$ 좌표 $p_i(x,y,z)$ 및 $p_{i-1}(x,y,z)$의 유클리디안 거리 $d_i = \sqrt{(x_i - x_{i-1})^2 + (y_i - y_{i-1})^2 + (z_i - z_{i-1})^2}$ 계산.
  3. $p_i$ 또는 $p_{i-1}$ 중 하나라도 `isNan`이 `true`이면 $d_i = 0.0f$ 로 치환.
  4. 프레임간 이동 거리에 `fps`를 곱하여 초당 속도 $v_i = d_i \times fps$ 산출.
  5. 전체 프레임 수와 배열 크기를 1:1로 맞추기 위해 인덱스 0 위치에 `0.0f`를 선두 삽입 (`velocities.size == poseFrames.size`).

### FR-2. 1차원 가우시안 필터링 (`ImpactDetector.gaussianFilter1d`)
- 입력: `velocities: FloatArray`, `sigma: Float = 2.0f`
- 처리 로직:
  1. $\sigma = 2.0$ 에 대한 가우시안 커널 윈도우 생성 (반지름 $radius = \lceil 4.0 \times \sigma \rceil$, 즉 $radius = 8$, 커널 크기 $17$).
  2. 커널 가중치 $w_k = \frac{1}{\sqrt{2\pi}\sigma} \exp\left(-\frac{k^2}{2\sigma^2}\right)$ 계산 및 합이 1이 되도록 정규화.
  3. 경계면 처리는 Reflect 방식(`scipy.ndimage.gaussian_filter1d` 기본값)을 적용하여 컨볼루션 연산 수행.

### FR-3. 다중 스윙 임팩트 프레임 감지 (`ImpactDetector.detectImpactFrames`)
- 입력: `poseFrames: List<PoseFrame>`, `fps: Float = 30f`, `isRightHand: Boolean = true`
- 결과 데이터 클래스: `data class ImpactDetectionResult(val impactFrames: List<Int>, val velocities: List<Float>)`
- 관절 인덱스 선택: `wristIndex = if (isRightHand) 16 else 15` (오른손목 16, 왼손목 15)
- 처리 로직:
  1. `velocities = calculateVelocity(poseFrames, wristIndex, fps)` 수행.
  2. `velocitiesClean = velocities.map { if (it.isNaN()) 0f else it }` 처리.
  3. `velocitiesSmooth = gaussianFilter1d(velocitiesClean, sigma = 2.0f)` 적용.
  4. `maxVel = max(velocitiesSmooth)`. `maxVel == 0f` 이면 `ImpactDetectionResult(listOf(0), velocities)` 반환.
  5. 피크 감지 파라미터 계산:
     - `minPeakHeight = maxVel * 0.5f` (최대 속도의 50% 이상)
     - `minProminence = maxVel * 0.3f` (주변 대비 30% 이상 돌출)
     - `distanceFrames = (fps * 2.0f).toInt()` (최소 2초 간격)
  6. `findPeaks` 알고리즘 수행:
     - 조건 1: `velocitiesSmooth[i] >= minPeakHeight`
     - 조건 2: `velocitiesSmooth[i] > velocitiesSmooth[i-1]` 이고 `velocitiesSmooth[i] > velocitiesSmooth[i+1]` (Local maxima)
     - 조건 3: Prominence (피크의 좌우 골짜기 깊이 차이) $\ge minProminence$
     - 조건 4: 피크 간 거리가 `distanceFrames` 이상 (중복 피크 중 Prominence/Height가 높은 피크 우선 선택)
  7. 감지된 피크 프레임 인덱스 목록 `List<Int>` 반환. 감지된 피크가 0개면 `listOf(maxVelocityIndex)`를 반환.

### FR-4. Python 골든 픽스처 추출 및 수치 동일성 단위 테스트
- `tennis-vision-analyzer/` 스크립트를 확장하여 가속/감속 스윙 데이터, 다중 스윙 데이터, 결측 데이터에 대해 Python `impact_detector.py`를 실행한 결과가 포함된 `golden_impact_fixture.json` 파일 생성.
- 생성된 JSON 픽스처를 `:core:vision/src/test/resources/golden_impact_fixture.json`에 배치.
- `:core:vision/src/test/`에 `ImpactDetectorTest.kt`를 작성하여 Kotlin 구현 결과가 Python 결과와 프레임 인덱스는 100% 동일하고, 속도값은 허용 오차 `1e-4` 이내로 일치함을 검증.

---

## 4. 인터페이스 및 데이터 구조

```kotlin
package io.github.loje0611.tennisdoc.core.vision.analyzer

data class ImpactDetectionResult(
    val impactFrames: List<Int>,
    val velocities: List<Float>
)

object ImpactDetector {
    fun calculateVelocity(
        poseFrames: List<PoseFrame>,
        jointIndex: Int,
        fps: Float = 30f
    ): List<Float>

    fun gaussianFilter1d(
        data: FloatArray,
        sigma: Float = 2.0f
    ): FloatArray

    fun detectImpactFrames(
        poseFrames: List<PoseFrame>,
        fps: Float = 30f,
        isRightHand: Boolean = true
    ): ImpactDetectionResult
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
| **AC-5** | `golden_impact_fixture.json` 파일이 `:core:vision/src/test/resources/`에 배치되어 있음. |
| **AC-6** | **(골든 픽스처 동일성 검증)** `ImpactDetectorTest`에서 감지된 임팩트 프레임 인덱스 목록이 Python 결과와 **100% 완벽히 일치**하며, 계산된 속도값 오차가 **`1e-4` 이내**임. |
| **AC-7** | 프레임 수가 2 미만이거나 모든 좌표가 결측치(`NaN`)인 경우에도 예외(Exception) 없이 빈 결과 또는 인덱스 0이 안전하게 반환됨. |
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
