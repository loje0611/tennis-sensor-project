# TASK-031 — `:core:fusion` 모듈 신설 및 융합 데이터 계약 정의

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (Phase 3 B그룹 착수: :core:fusion 순수 JVM 모듈 스캐폴딩 및 융합 도메인 계약 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Phase 3 센서-비전 융합 엔진의 기반이 되는 **`:core:fusion` [순수 Kotlin JVM] 모듈을 신설**하고, 센서(IMU)와 비전(Pose) 시계열을 결합하기 위한 핵심 데이터 계약(`SyncAnchor`, `KineticChain5Stage`, `RacketImpactOrientation`, `FusedSwing`, `FusionDiagnosis`) 및 `FusionEngine` 인터페이스를 정의하는 작업을 규정합니다.

D-9.2 원칙에 따라 Android 프레임워크 의존성이 없는 순수 JVM 모듈로 구성하여, 계측 기기 없이 빠르고 결정론적인 단위 테스트를 가능하게 합니다.

### 1.2 범위
- `settings.gradle.kts`에 `:core:fusion` 모듈 등록.
- `core/fusion/build.gradle.kts` 생성:
  - 컨벤션 플러그인: `id("tennisdoc.jvm.library")`
  - 의존성: `:core:model`, `:core:vision`, `:core:analysis`
- 루트 `build.gradle.kts`의 `verifyModuleDependencies` 아키텍처 규칙 갱신:
  - `:core:fusion` 허용 의존성: `{:core:model, :core:vision, :core:analysis}`
  - `:feature:lab` 허용 의존성에 `:core:fusion` 추가.
  - `:app` 허용 의존성에 `:core:fusion` 추가.
- 융합 데이터 모델 및 계약 정의 (`io.github.loje0611.tennisdoc.core.fusion.model`):
  - `SyncAnchor`: 임팩트 앵커 시간 오프셋 및 동기화 메타데이터
  - `KineticStage` & `KineticChain5Stage`: 5단계 통합 운동 체인 모델 (골반 ➔ 어깨 ➔ 손목 ➔ 라켓 ➔ 임팩트)
  - `RacketImpactOrientation` & `RacketFaceState`: 임팩트 순간 라켓 각도 및 상태 (`OPEN`, `CLOSED`, `SQUARE`)
  - `ImuDataPoint`: IMU 시계열 데이터 샘플 계약
  - `FusionDiagnosis`: 인과 코칭 진단 태그 및 설명 모델
  - `FusedSwing`: 동기화된 단일 스윙 종합 융합 컨테이너
- `FusionEngine` 공개 인터페이스 정의.
- 단위 테스트:
  - 융합 데이터 모델 불변식(Invariants: 5단계 스테이지 무결성, 앵커 시간 정렬) 및 팩토리 검증.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **`:core:fusion`**: 센서 IMU 시계열과 비전 포즈 시계열을 융합하는 순수 Kotlin/JVM 분석 모듈.
- **`SyncAnchor`**: 비전의 손목 속도 피크 시점과 센서의 고주파 충격 피크 시점 간의 시간 정렬 기준 앵커.
- **`KineticChain5Stage`**: 기존 비전 3단계(골반·어깨·손목)에 센서 기반 라켓 가속 및 임팩트 페이스를 통합한 5단계 운동 역학 체인.

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 제품 방향 결정: [`docs/PRODUCT_DIRECTION.md`](../PRODUCT_DIRECTION.md) (D-9.2)
- 비전 데이터 계약: [`docs/specs/TASK-021-core-vision-angle-calculator.md`](TASK-021-core-vision-angle-calculator.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: `:core:fusion` 모듈 스캐폴딩 및 빌드 설정
- `settings.gradle.kts`에 `include(":core:fusion")`을 선언한다.
- `TennisDocAI/core/fusion/build.gradle.kts`를 생성하고 `tennisdoc.jvm.library` 플러그인을 적용한다.
- 의존성 선언:
  - `implementation(project(":core:model"))`
  - `implementation(project(":core:vision"))`
  - `implementation(project(":core:analysis"))`
  - `testImplementation(libs.junit)`
  - `testImplementation("org.json:json:20240303")`

### FR-2: 아키텍처 의존성 검증 규칙 갱신 (`verifyModuleDependencies`)
- 루트 `build.gradle.kts`의 `verifyModuleDependencies` 태스크에 `:core:fusion`을 추가한다:
  - `":core:fusion" to setOf(":core:model", ":core:vision", ":core:analysis")`
  - `":feature:lab"` 허용 목록에 `":core:fusion"` 포함.
  - `":app"` 허용 목록에 `":core:fusion"` 포함.

### FR-3: 임팩트 동기화 앵커 계약 (`SyncAnchor`)
- `SyncAnchor` 데이터 클래스 정의:
  - `visionImpactTimestampMs: Long`: 비전 손목 가속 기반 추정 임팩트 시점
  - `sensorImpactTimestampMs: Long`: 센서 고주파 IMU 피크 시점
  - `timeOffsetMs: Long`: 센서와 비전 간의 시간차 (`sensorImpactTimestampMs - visionImpactTimestampMs`)
  - `confidence: Float`: 동기화 신뢰도 점수 (0.0f ~ 1.0f)
  - `isSynchronized: Boolean`: 시간차가 유효 허용 범위(예: $\le 100\text{ms}$) 내에 있는지 여부

### FR-4: 5단계 통합 운동 체인 모델 (`KineticChain5Stage`)
- `KineticStageType` 열거형 정의:
  - `HIP`: 골반 회전 (비전)
  - `SHOULDER`: 어깨 회전 (비전)
  - `WRIST`: 손목 스윙 가속 (비전)
  - `RACKET`: 라켓 회전 각속도 (센서)
  - `IMPACT`: 임팩트 페이스 충격 (센서)
- `KineticStage` 데이터 클래스 정의:
  - `stage: KineticStageType`
  - `peakTimestampMs: Long`
  - `peakValue: Float`
  - `delayFromPreviousMs: Long`
- `KineticChain5Stage` 데이터 클래스 정의:
  - `stages: List<KineticStage>` (정확히 5개 스테이지 순서 보장)
  - `isSequential: Boolean`: 피크 타임스탬프가 골반 ➔ 어깨 ➔ 손목 ➔ 라켓 ➔ 임팩트 순으로 단조 증가하는지 여부
  - `totalDurationMs: Long`: 골반 시작부터 임팩트까지의 총 시간 (ms)
  - `energyTransferEfficiency: Float`: 에너지 전달 효율 점수 (0.0f ~ 100.0f)

### FR-5: 라켓 페이스 각도 및 상태 모델 (`RacketImpactOrientation`)
- `RacketFaceState` 열거형 정의: `OPEN` (열림), `CLOSED` (닫힘), `SQUARE` (직각/스퀘어)
- `RacketImpactOrientation` 데이터 클래스 정의:
  - `rollDeg: Float`, `pitchDeg: Float`, `yawDeg: Float`
  - `faceState: RacketFaceState`
  - `deviationDeg: Float`: 이상적인 스퀘어 각도와의 편차 (도 단위)

### FR-6: 융합 종합 결과 컨테이너 (`FusedSwing`) 및 진단 모델 (`FusionDiagnosis`)
- `ImuDataPoint` 시계열 데이터 클래스:
  - `timestampMs: Long`, `accelX: Float`, `accelY: Float`, `accelZ: Float`, `gyroX: Float`, `gyroY: Float`, `gyroZ: Float`
- `FusionDiagnosis` 데이터 클래스:
  - `diagnosisTags: List<String>`
  - `primaryCause: String`
  - `coachingFeedback: String`
  - `causalExplanation: String`
- `FusedSwing` 데이터 클래스:
  - `swingId: String`, `sessionId: String`, `drillType: DrillType`
  - `anchor: SyncAnchor`
  - `kineticChain: KineticChain5Stage`
  - `racketImpact: RacketImpactOrientation`
  - `visionPoses: List<PoseFrame>`
  - `imuSamples: List<ImuDataPoint>`
  - `diagnosis: FusionDiagnosis?`

### FR-7: `FusionEngine` 인터페이스 정의
- `interface FusionEngine`:
  - `fun fuse(drillType: DrillType, poses: List<PoseFrame>, imuSamples: List<ImuDataPoint>): FusedSwing`

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.core.fusion.model

import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

enum class KineticStageType {
    HIP, SHOULDER, WRIST, RACKET, IMPACT
}

data class KineticStage(
    val stage: KineticStageType,
    val peakTimestampMs: Long,
    val peakValue: Float,
    val delayFromPreviousMs: Long = 0L
)

data class KineticChain5Stage(
    val stages: List<KineticStage>,
    val isSequential: Boolean,
    val totalDurationMs: Long,
    val energyTransferEfficiency: Float
) {
    init {
        require(stages.size == 5) { "KineticChain5Stage must contain exactly 5 stages" }
    }
}

enum class RacketFaceState {
    OPEN, CLOSED, SQUARE
}

data class RacketImpactOrientation(
    val rollDeg: Float,
    val pitchDeg: Float,
    val yawDeg: Float,
    val faceState: RacketFaceState,
    val deviationDeg: Float
)

data class SyncAnchor(
    val visionImpactTimestampMs: Long,
    val sensorImpactTimestampMs: Long,
    val timeOffsetMs: Long = sensorImpactTimestampMs - visionImpactTimestampMs,
    val confidence: Float,
    val isSynchronized: Boolean = Math.abs(timeOffsetMs) <= 100L
)

data class ImuDataPoint(
    val timestampMs: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float
)

data class FusionDiagnosis(
    val diagnosisTags: List<String>,
    val primaryCause: String,
    val coachingFeedback: String,
    val causalExplanation: String
)

data class FusedSwing(
    val swingId: String,
    val sessionId: String,
    val drillType: DrillType,
    val anchor: SyncAnchor,
    val kineticChain: KineticChain5Stage,
    val racketImpact: RacketImpactOrientation,
    val visionPoses: List<PoseFrame>,
    val imuSamples: List<ImuDataPoint>,
    val diagnosis: FusionDiagnosis? = null
)
```

---

## 5. UI/UX 요구사항
- **N/A (순수 JVM 융합 엔진 및 도메인 데이터 계약 모듈)**

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 플랫폼 독립성 (Zero Android Framework Dependency)
- `:core:fusion`은 `android.*` 패키지 참조를 일체 포함하지 않는 순수 Kotlin/JVM 라이브러리여야 한다.

### 6.2 모듈 의존성 단방향 규칙
- `:core:fusion`은 오직 하위 계층(`:core:model`, `:core:vision`, `:core:analysis`)만 참조할 수 있으며 UI나 Feature 모듈을 참조하지 않는다.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **불완전한 5단계 스테이지 리스트**: `KineticChain5Stage` 생성 시 스테이지 개수가 5개가 아니면 `IllegalArgumentException` 발생.
- **음수 또는 비정상 시간 오프셋**: `SyncAnchor` 생성 시 오프셋 자동 계산 및 `isSynchronized` 불리언 플래그 안전 반환.
- **빈 센서/비전 데이터 전달**: `FusionEngine`은 예외를 던지지 않고 낮은 신뢰도(`confidence = 0f`, `isSynchronized = false`)의 `FusedSwing` 반환.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `settings.gradle.kts`에 `:core:fusion`이 등록되고 `build.gradle.kts`가 생성되어 컴파일에 성공한다.
- [ ] **AC-2**: `verifyModuleDependencies` 검증 태스크가 `:core:fusion`, `:feature:lab`, `:app`의 신규 허용 규칙을 포함하여 통과한다.
- [ ] **AC-3**: `SyncAnchor`, `KineticStage`, `KineticChain5Stage`, `RacketImpactOrientation`, `FusedSwing` 도메인 데이터 모델이 정의된다.
- [ ] **AC-4**: `KineticChain5Stage` 불변식(정확히 5개 스테이지) 검증 단위 테스트가 통과한다.
- [ ] **AC-5**: `SyncAnchor`의 `timeOffsetMs` 및 `isSynchronized` 계산 검증 단위 테스트가 통과한다.
- [ ] **AC-6**: `FusionEngine` 인터페이스가 정의되고 더미/스텁 구현체를 통한 단위 테스트가 통과한다.
- [ ] **AC-7**: `./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug
```
