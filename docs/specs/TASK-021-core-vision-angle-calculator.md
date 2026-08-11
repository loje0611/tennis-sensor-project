# TASK-021 — `:core:vision` 모듈 + `PoseFrame` 데이터 계약 + 관절 각도 계산 (`src/angle_calculator.py` 포팅)

| 항목 | 값 |
|---|---|
| Task ID | TASK-021 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-009, TASK-020 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-11 | PM | 최초 작성 (Phase 2 B그룹 비전 알고리즘 Kotlin 포팅 착수) |

---

## 1. 개요 및 범위

### 1.1 개요
Phase 1에서 Python 프로토타입(`tennis-vision-analyzer/src/angle_calculator.py`)으로 검증된 3D 관절 각도 계산 및 포즈 데이터 구조를 순수 Kotlin/JVM 모듈인 `:core:vision`으로 포팅한다. 

D-9.2 원칙에 따라 `:core:vision` 모듈은 Android SDK 및 CameraX/MediaPipe Android 의존성이 없는 **순수 `kotlin("jvm")` 모듈**로 구현되며, 입출력 골든 픽스처(JSON)를 추출하여 Python 구현체와 수치적으로 완전히 동일한 결과를 내는지 정밀하게 검증한다.

### 1.2 범위
- **포함**:
  - `:core:vision` 모듈 빌드 설정 (`build.gradle.kts`에 `tennisdoc.jvm.library` 플러그인 확인 및 kotlinx.serialization 또는 jackson 등 테스트 픽스처 로딩용 의존성 구성).
  - 3D 포즈 랜드마크 및 프레임 데이터 계약 타입 선언 (`PoseLandmark`, `PoseFrame`, `JointAngleResult`).
  - 공간상 세 점 기반 3D 내적 각도 계산 알고리즘 (`AngleCalculator.calculate3dAngle`) 및 결측치(`NaN`), 벡터 길이 0, 부동소수점 오차 범위 클리핑 처리.
  - MediaPipe 33개 랜드마크 프레임에서 오른팔(12-14-16) 및 오른무릎(24-26-28) 각도 추출 (`AngleCalculator.getJointAnglesFromPose`).
  - Python 쪽 골든 픽스처 추출 스크립트 작성 및 `golden_angles_fixture.json` 생성.
  - `:core:vision/src/test/`에 골든 픽스처 기반 수치 검증 테스트 (`AngleCalculatorTest.kt`) 구축.
- **제외**:
  - CameraX 및 MediaPipe Android SDK 바인딩 (Phase 2 C그룹 TASK-026에서 `:feature:lab`에 배치).
  - 속도 계산, 다중 스윙 임팩트 감지, 스윙 궤적 분류 (TASK-022, TASK-023 범위).

---

## 2. 정의 및 참조

- **참조 소스**: [`tennis-vision-analyzer/src/angle_calculator.py`](file:///home/keunu/personal-project/tennis-sensor-project/tennis-vision-analyzer/src/angle_calculator.py), [`tennis-vision-analyzer/src/pose_extractor.py`](file:///home/keunu/personal-project/tennis-sensor-project/tennis-vision-analyzer/src/pose_extractor.py)
- **관련 계획**: [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 (B그룹 골든 픽스처 원칙)

---

## 3. 기능 요구사항

### FR-1. `PoseLandmark` 및 `PoseFrame` 데이터 구조체 선언
`:core:vision` 모듈의 `io.github.loje0611.tennisdoc.core.vision.model` 패키지에 다음 데이터 클래스들을 정의한다:
1. `data class PoseLandmark(val x: Float, val y: Float, val z: Float, val visibility: Float = 1.0f)`
   - 헬퍼 프로퍼티: `val isNan: Boolean get() = x.isNaN() || y.isNaN() || z.isNaN()`
2. `data class PoseFrame(val landmarks: List<PoseLandmark>)`
   - `landmarks` 크기가 33개 이상인지 확인하는 프로퍼티 및 결측 프레임 헬퍼 구성.
3. `data class JointAngleResult(val rightArmAngle: Double, val rightKneeAngle: Double)`

### FR-2. 3D 관절 각도 계산 알고리즘 (`AngleCalculator.calculate3dAngle`)
`io.github.loje0611.tennisdoc.core.vision.analyzer.AngleCalculator`에 세 점 $a, b, c$ ($b$가 중심점)를 받아 3D 내적 각도(0 ~ 180도)를 반환하는 함수를 구현한다:
- 입력: `a: PoseLandmark, b: PoseLandmark, c: PoseLandmark`
- 처리 로직:
  1. $a, b, c$ 중 어느 하나라도 `isNan`이 `true`이면 `Double.NaN` 반환.
  2. 벡터 $ba = (a.x - b.x, a.y - b.y, a.z - b.z)$ 및 $bc = (c.x - b.x, c.y - b.y, c.z - b.z)$ 계산.
  3. 벡터의 크기 $\|ba\| = \sqrt{ba_x^2 + ba_y^2 + ba_z^2}$ 및 $\|bc\| = \sqrt{bc_x^2 + bc_y^2 + bc_z^2}$ 계산.
  4. $\|ba\| == 0.0$ 이거나 $\|bc\| == 0.0$ 이면 `Double.NaN` 반환.
  5. 내적 $dot = ba_x \cdot bc_x + ba_y \cdot bc_y + ba_z \cdot bc_z$ 계산 후 코사인 값 $cos = \frac{dot}{\|ba\| \cdot \|bc\|}$ 구함.
  6. 부동소수점 오차 방지를 위해 $cos$ 값을 $[-1.0, 1.0]$ 범위로 클리핑 (`coerceIn(-1.0, 1.0)`).
  7. $\arccos(cos)$ 라디안 값을 계산한 후 디그리(Degree)로 변환 (`Math.toDegrees(angleRad)`).

### FR-3. 포즈 프레임 주요 관절 각도 추출 (`AngleCalculator.getJointAnglesFromPose`)
- 입력: `poseFrame: PoseFrame`
- 조건 및 예외 처리:
  - `poseFrame.landmarks.size < 33` 이면 `JointAngleResult(Double.NaN, Double.NaN)` 반환.
- 인덱스 정의:
  - 어깨(`R_SHOULDER = 12`), 팔꿈치(`R_ELBOW = 14`), 손목(`R_WRIST = 16`)
  - 골반(`R_HIP = 24`), 무릎(`R_KNEE = 26`), 발목(`R_ANKLE = 28`)
- 결과:
  - `rightArmAngle = calculate3dAngle(landmarks[12], landmarks[14], landmarks[16])`
  - `rightKneeAngle = calculate3dAngle(landmarks[24], landmarks[26], landmarks[28])`
  - `JointAngleResult(rightArmAngle, rightKneeAngle)` 반환.

### FR-4. Python 입출력 골든 픽스처 추출 및 테스트 리소스 고정
- Python 프로젝트(`tennis-vision-analyzer/`)에 픽스처 추출 도구 또는 스크립트를 구성하여 일직선(180도), 직각(90도), 임의의 3D 관절 좌표, `NaN` 결측치, 0 길이 벡터 입력에 대한 기대 결과가 담긴 JSON 파일(`golden_angles_fixture.json`)을 생성한다.
- 생성된 JSON 픽스처를 `:core:vision/src/test/resources/golden_angles_fixture.json`에 배치한다.
- `:core:vision/src/test/`에 `AngleCalculatorTest.kt`를 작성하여 골든 픽스처의 모든 케이스를 로딩하고, Kotlin 구현 결과와 Python 결과의 차이가 허용 오차 `1e-5` 이내임을 검증한다.

---

## 4. 인터페이스 및 데이터 구조

```kotlin
package io.github.loje0611.tennisdoc.core.vision.model

data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float = 1.0f
) {
    val isNan: Boolean get() = x.isNaN() || y.isNaN() || z.isNaN()
}

data class PoseFrame(
    val landmarks: List<PoseLandmark>
)

data class JointAngleResult(
    val rightArmAngle: Double,
    val rightKneeAngle: Double
)
```

---

## 5. 인수 조건 (Acceptance Criteria)

| # | 조건 |
|---|---|
| **AC-1** | `./gradlew :core:vision:assembleDebug` 및 `./gradlew :core:vision:test` 통과. |
| **AC-2** | `./gradlew test` 통과 (기존 테스트 회귀 없음). |
| **AC-3** | `./gradlew verifyModuleDependencies verifyJniBindings` 통과. |
| **AC-4** | `:core:vision` 모듈 소스 코드 및 `build.gradle.kts` 내에 Android SDK (`android.*`, `androidx.*` 등) 및 NDK 의존성이 **0건**임 (pure JVM 모듈 유지). |
| **AC-5** | `golden_angles_fixture.json` 파일이 `:core:vision/src/test/resources/`에 배치되어 있음. |
| **AC-6** | **(골든 픽스처 동일성 검증)** `AngleCalculatorTest`에서 골든 픽스처의 모든 입력에 대해 계산한 결과가 Python 결과값과 **허용 오차 `1e-5` 이내로 일치**함. |
| **AC-7** | 결측치(`NaN`), 벡터 길이 0, 부동소수점 오차 범위 초과 입력 시 `Double.NaN` 또는 0.0/180.0도의 경계값이 예외 없이 올바르게 계산됨. |
| **AC-8** | 변경 및 신규 소스 경로가 `TennisDocAI/core/vision/`, `tennis-vision-analyzer/` (골든 픽스처 추출 스크립트), 명세/보드 문서에 한정됨. |

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
