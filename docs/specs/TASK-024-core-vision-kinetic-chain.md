# TASK-024 — `:core:vision` 골반-어깨-손목 운동 체인(Kinetic Chain) 역학 분석 (`src/kinetic_chain.py` 포팅)

| 항목 | 값 |
|---|---|
| Task ID | TASK-024 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-009, TASK-022 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-11 | PM | 최초 작성 (Phase 2 B그룹 비전 운동 체인 분석 알고리즘 Kotlin 포팅) |

---

## 1. 개요 및 범위

### 1.1 개요
Phase 1 Python 프로토타입(`tennis-vision-analyzer/src/kinetic_chain.py`)으로 검증된 3D 관절(골반 $\rightarrow$ 어깨 $\rightarrow$ 손목) 가속 순서 및 타이밍 지연(ms) 기반의 운동 체인(Kinetic Chain) 역학 분석 알고리즘을 순수 Kotlin/JVM 모듈인 `:core:vision`으로 포팅한다.

D-9.2 원칙에 따라 순수 `kotlin("jvm")` 모듈에 구현하며, TASK-022에서 완성된 `ImpactDetector.calculateVelocity`를 재활용하여 입출력 골든 픽스처(JSON) 기반으로 수치적 정확도와 피크 순서 판정 동일성을 검증한다.

### 1.2 범위
- **포함**:
  - 운동 체인 분석 데이터 구조체 선언 (`PeakFrames`, `TimingMs`, `JointVelocities`, `KineticChainResult`).
  - 골반(하체), 어깨(몸통), 손목(팔) 관절 속도 산출 및 피크 프레임(`argmax`) 추출 알고리즘 (`KineticChainAnalyzer.analyzeKineticChain`).
  - 프레임 간 지연 시간(ms) 계산 및 올바른 순서(골반 $\le$ 어깨 $\le$ 손목) 가속 체인 검증.
  - Python 쪽 골든 픽스처 추출 스크립트 작성 및 `golden_kinetic_chain_fixture.json` 생성 및 `:core:vision/src/test/resources/` 등록.
  - `:core:vision/src/test/`에 골든 픽스처 기반 수치 검증 테스트 (`KineticChainAnalyzerTest.kt`) 구축.
- **제외**:
  - 스윙 종합 진단 및 피드백 문구 생성 (TASK-025 범위).
  - Android UI 및 시각화 차트 렌더링.

---

## 2. 정의 및 참조

- **참조 소스**: [`tennis-vision-analyzer/src/kinetic_chain.py`](file:///home/keunu/personal-project/tennis-sensor-project/tennis-vision-analyzer/src/kinetic_chain.py)
- **선행 명세**: [`docs/specs/TASK-022-core-vision-impact-detector.md`](TASK-022-core-vision-impact-detector.md)
- **관련 계획**: [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §5 (B그룹 골든 픽스처 원칙)

---

## 3. 기능 요구사항

### FR-1. 운동 체인 데이터 모델 정의
`io.github.loje0611.tennisdoc.core.vision.model` 패키지에 다음 데이터 클래스를 구성한다:
```kotlin
data class PeakFrames(
    val hip: Int,
    val shoulder: Int,
    val wrist: Int
)

data class TimingMs(
    val hipToShoulder: Double,
    val shoulderToWrist: Double
)

data class JointVelocities(
    val hip: List<Float>,
    val shoulder: List<Float>,
    val wrist: List<Float>
)

data class KineticChainResult(
    val peakFrames: PeakFrames,
    val timingMs: TimingMs,
    val isCorrectChain: Boolean,
    val velocities: JointVelocities
)
```

### FR-2. 운동 체인 가속 순서 및 타이밍 분석 (`KineticChainAnalyzer.analyzeKineticChain`)
`io.github.loje0611.tennisdoc.core.vision.analyzer.KineticChainAnalyzer` 클래스/오브젝트에 구현한다:
- 입력: `poseFrames: List<PoseFrame>`, `fps: Float = 30f`, `isRightHand: Boolean = true`
- 처리 로직:
  1. `poseFrames.size < 2` 인 경우 `null` 반환.
  2. 관절 인덱스 설정:
     - 골반: `hipIdx = if (isRightHand) 24 else 23`
     - 어깨: `shoulderIdx = if (isRightHand) 12 else 11`
     - 손목: `wristIdx = if (isRightHand) 16 else 15`
  3. `ImpactDetector.calculateVelocity`를 이용하여 `velHip`, `velShoulder`, `velWrist` (각 `List<Float>`) 속도 산출.
  4. 각 속도 리스트의 `NaN` 값을 `0.0f`로 치환 처리.
  5. 속도 리스트에서 최대 속도 발생 프레임 인덱스(`argmax`) 산출: `peakHip`, `peakShoulder`, `peakWrist`.
  6. 프레임당 시간(ms) `msPerFrame = 1000.0 / fps` 계산.
  7. 관절간 전이 지연 시간(ms) 계산:
     - `hipToShoulderMs = (peakShoulder - peakHip) * msPerFrame`
     - `shoulderToWristMs = (peakWrist - peakShoulder) * msPerFrame`
  8. 가속 순서 유효성 검증:
     - `isCorrectChain = (peakHip <= peakShoulder) && (peakShoulder <= peakWrist)`
  9. `KineticChainResult` 객체 생성 및 반환.

### FR-3. Python 입출력 골든 픽스처 추출 및 수치 동일성 단위 테스트
- `tennis-vision-analyzer/` 스크립트를 확장하여 정상 운동 체인, 순서 역전 체인, 동시 피크 발생 체인, 결측 데이터에 대해 Python `analyze_kinetic_chain`을 실행한 결과가 포함된 `golden_kinetic_chain_fixture.json` 파일 생성.
- 생성된 JSON 픽스처를 `:core:vision/src/test/resources/golden_kinetic_chain_fixture.json`에 배치.
- `:core:vision/src/test/`에 `KineticChainAnalyzerTest.kt`를 작성하여 Kotlin 분석 결과의 `peakFrames` 및 `isCorrectChain` 이 Python 결과와 100% 동일하고, `timingMs` 차이가 `1e-4` 이내임을 검증.

---

## 4. 인터페이스 및 데이터 구조

```kotlin
package io.github.loje0611.tennisdoc.core.vision.analyzer

object KineticChainAnalyzer {
    fun analyzeKineticChain(
        poseFrames: List<PoseFrame>,
        fps: Float = 30f,
        isRightHand: Boolean = true
    ): KineticChainResult?
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
| **AC-5** | `golden_kinetic_chain_fixture.json` 파일이 `:core:vision/src/test/resources/`에 배치되어 있음. |
| **AC-6** | **(골든 픽스처 동일성 검증)** `KineticChainAnalyzerTest`에서 `peakFrames` 및 `isCorrectChain` 결과가 Python 결과와 **100% 완벽히 일치**하며, `timingMs` 오차가 **`1e-4` 이내**임. |
| **AC-7** | 프레임 수가 2 미만이거나 입력이 비어있을 때 예외 없이 `null`이 안전하게 반환됨. |
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
