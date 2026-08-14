# TASK-034 — 센서-비전 융합 인과 코칭(Causal Coaching) 룰 엔진 구현

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (Phase 3 B그룹: 센서 라켓 페이스 상태와 비전 운동 체인/자세 결합 인과 코칭 룰 엔진 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 센서가 측정한 **"라켓 페이스 각도 및 임팩트 충격(결과)"** 과 비전이 측정한 **"골반·어깨·손목 운동 체인 타이밍 및 관절 자세(원인)"** 간의 상관관계를 분석하여, 스윙 결함의 근본 원인을 도출하고 구체적인 교정 가이드를 제시하는 **인과 코칭 룰 엔진(`CausalCoachingEngine`)** 및 통합 융합 파이프라인(`FusionEngineImpl`)을 `:core:fusion` 모듈에 구현하는 작업을 규정합니다.

단순히 "라켓이 열렸다"나 "팔꿈치가 굽었다"는 현상 나열을 넘어, "상체가 골반보다 30ms 일찍 열렸기 때문에 임팩트 시 라켓 페이스가 12도 열렸다"와 같이 **원인 ➔ 결과의 인과적 피드백(Causal Explanation)** 을 제공합니다.

### 1.2 범위
- `:core:fusion` 모듈 내 인과 코칭 엔진 구현:
  - `CausalCoachingEngine`: `DrillType`, `SyncAnchor`, `KineticChain5Stage`, `RacketImpactOrientation`, `poses`, `imuSamples`를 종합 분석하여 `FusionDiagnosis` 생성.
  - 라켓 임팩트 페이스 각도 계산기(`RacketImpactCalculator`): IMU 자세/가속도 벡터로부터 임팩트 순간의 `RacketImpactOrientation`(`rollDeg`, `pitchDeg`, `yawDeg`, `faceState`, `deviationDeg`) 산출.
- 핵심 인과 진단 룰셋 구축:
  1. **상체 조기 개방으로 인한 페이스 열림 (`EARLY_BODY_OPEN_FACE_OPEN`)**
  2. **타점 후방 형성으로 인한 페이스 닫힘 (`LATE_CONTACT_FACE_CLOSED`)**
  3. **운동 체인 에너지 유실 (`KINETIC_POWER_LEAK`)**
  4. **최적 순차 가속 및 스퀘어 타격 (`OPTIMAL_CLEAN_STRIKE`)**
  5. **드릴별 타겟 궤적 불일치 (`DRILL_TRAJECTORY_MISMATCH`)**
- 종합 융합 엔진 구현체 (`FusionEngineImpl`):
  - `ImpactAnchorSynchronizer` ➔ `RacketImpactCalculator` ➔ `KineticChain5StageAnalyzer` ➔ `CausalCoachingEngine`을 단일 `fuse()` 파이프라인으로 조립.
- 골든 픽스처(JSON) 기반 JVM 단위 테스트:
  - 4개 주요 인과 시나리오별 태그, 원인, 피드백 문장 일치성 검증.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **인과 코칭(Causal Coaching)**: 물리적 타격 결과(라켓 페이스 각도/헤드스피드)의 원인을 신체 분절의 선행 동작(골반·어깨 회전 타이밍, 관절 각도)에서 찾아 설명하는 진단 체계.
- **`FusionDiagnosis`**: TASK-031에서 정의된 진단 결과 모델 (`diagnosisTags: List<String>`, `primaryCause: String`, `coachingFeedback: String`, `causalExplanation: String`).

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 융합 데이터 계약: [`docs/specs/TASK-031-core-fusion-module-data-contracts.md`](TASK-031-core-fusion-module-data-contracts.md)
- 5단계 운동 체인: [`docs/specs/TASK-033-kinetic-chain-5stage-analysis.md`](TASK-033-kinetic-chain-5stage-analysis.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 라켓 페이스 상태 및 각도 계산 (`RacketImpactCalculator`)
- IMU 가속도/자이로 시계열에서 임팩트 순간($t_{\text{impact}}$)의 라켓 오리엔테이션을 계산한다.
- 임팩트 시점의 롤/피치/요 각도($\theta_{\text{roll}}, \theta_{\text{pitch}}, \theta_{\text{yaw}}$)로부터 이상적인 스퀘어 기준면(0도)과의 편차(`deviationDeg`)를 구한다.
- 판정 기준:
  - `deviationDeg > 8.0f`: `RacketFaceState.OPEN` (열림)
  - `deviationDeg < -8.0f`: `RacketFaceState.CLOSED` (닫힘)
  - 기타 ($-8.0\text{f} \le \text{deviationDeg} \le 8.0\text{f}$): `RacketFaceState.SQUARE` (직각/스퀘어)

### FR-2: 인과 진단 룰셋 및 추론 로직 (`CausalCoachingEngine`)
다음 룰을 우선순위 순서대로 평가하여 주 원인(`primaryCause`)과 인과 설명(`causalExplanation`)을 도출한다:

1. **상체 조기 개방 ➔ 페이스 열림 (Rule 1)**
   - 조건: `racketImpact.faceState == OPEN` && `!kineticChain.isSequential` (Shoulder 피크가 Hip 피크보다 앞서거나 지연 < 10ms)
   - `diagnosisTags`: `["FACE_OPEN", "EARLY_BODY_OPEN", "KINETIC_FAULT"]`
   - `primaryCause`: "상체 조기 회전으로 인한 타점 밀림 및 페이스 열림"
   - `coachingFeedback`: "골반 회전이 먼저 시작된 후 상체가 따라오도록 코어 타이밍을 교정하세요."
   - `causalExplanation`: "상체가 골반보다 일찍 열려 임팩트 타점이 뒤로 밀리면서 라켓 페이스가 열린 상태로 맞았습니다."

2. **후방 타점 ➔ 페이스 닫힘 (Rule 2)**
   - 조건: `racketImpact.faceState == CLOSED` && (비전 임팩트 시 손목 타점이 어깨 중심선보다 뒤에 위치)
   - `diagnosisTags`: `["FACE_CLOSED", "LATE_CONTACT"]`
   - `primaryCause`: "타점 후방 형성으로 인한 라켓 페이스 닫힘"
   - `coachingFeedback`: "몸 앞쪽에서 공을 맞추도록 전방 타점을 확보하고 팔로우 스루를 길게 가져가세요."
   - `causalExplanation`: "임팩트 타점이 몸 뒤에서 형성되어 라켓 헤드가 급격히 감기며 페이스가 닫혔습니다."

3. **운동 체인 에너지 누수 (Rule 3)**
   - 조건: `kineticChain.energyTransferEfficiency < 65.0f` && (손목 ➔ 라켓 지연 > 70ms 또는 골반 ➔ 어깨 지연 > 90ms)
   - `diagnosisTags`: `["POWER_LEAK", "CHAIN_TIMING_DELAY"]`
   - `primaryCause`: "신체 분절 간 가속 전달 지연으로 인한 스윙 파워 손실"
   - `coachingFeedback`: "손목과 라켓의 릴리즈 타이밍을 일치시켜 채찍처럼 에너지를 전달하세요."
   - `causalExplanation`: "상체 회전 에너지가 라켓 헤드로 전달되는 과정에서 지연이 발생해 스윙 파워가 감소했습니다."

4. **클린 스트라이크 (Rule 4)**
   - 조건: `kineticChain.isSequential` && `racketImpact.faceState == SQUARE` && `kineticChain.energyTransferEfficiency >= 80.0f`
   - `diagnosisTags`: `["CLEAN_STRIKE", "OPTIMAL_CHAIN", "SQUARE_FACE"]`
   - `primaryCause`: "완벽한 5단계 운동 체인 및 스퀘어 임팩트"
   - `coachingFeedback`: "이상적인 체인 타이밍과 정확한 페이스 정렬입니다. 현재 폼을 유지하세요."
   - `causalExplanation`: "골반부터 라켓까지 순차적으로 가속되어 최대의 에너지가 스퀘어 페이스로 공에 전달되었습니다."

### FR-3: 종합 융합 엔진 (`FusionEngineImpl`) 파이프라인
- `FusionEngine` 인터페이스를 구현하는 `FusionEngineImpl` 작성:
  ```kotlin
  override fun fuse(
      drillType: DrillType,
      poses: List<PoseFrame>,
      imuSamples: List<ImuDataPoint>
  ): FusedSwing
  ```
- 단계:
  1. `ImpactAnchorSynchronizer.synchronize(poses, imuSamples)` ➔ `anchor`
  2. `RacketImpactCalculator.calculate(imuSamples, anchor)` ➔ `racketImpact`
  3. `KineticChain5StageAnalyzer.analyze(poses, imuSamples, anchor)` ➔ `kineticChain`
  4. `CausalCoachingEngine.diagnose(drillType, anchor, kineticChain, racketImpact, poses, imuSamples)` ➔ `diagnosis`
  5. `FusedSwing` 조립 및 반환.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.core.fusion.coaching

import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

class CausalCoachingEngine {
    fun diagnose(
        drillType: DrillType,
        anchor: SyncAnchor,
        kineticChain: KineticChain5Stage,
        racketImpact: RacketImpactOrientation,
        poses: List<PoseFrame>,
        imuSamples: List<ImuDataPoint>
    ): FusionDiagnosis
}
```

---

## 5. UI/UX 요구사항
- **N/A (순수 JVM 인과 코칭 룰 엔진 모듈)**

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 결정론적 실행 (Deterministic Output)
- 동일한 입력에 대해 100% 동일한 `diagnosisTags`와 `primaryCause`를 산출해야 한다.

### 6.2 모듈 격리
- 순수 Kotlin JVM 클래스로 작성되며 UI/프레임워크 종속성을 배제한다.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **동기화 실패 (`anchor.isSynchronized == false`)**:
  - `diagnosisTags`: `["SYNC_FAILED"]`
  - `primaryCause`: "센서-비전 임팩트 시간 동기화 실패"
  - `coachingFeedback`: "센서 연결 및 카메라 앵글을 확인하고 다시 스윙해 주세요."
- **빈 입력 데이터**: 크래시 없이 기본 안내 진단 객체 반환.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `CausalCoachingEngine` 및 `FusionEngineImpl` 클래스가 `:core:fusion` 모듈에 구현되고 컴파일에 성공한다.
- [ ] **AC-2**: 페이스 열림 + 상체 조기 회전 픽스처 입력 시 `FACE_OPEN` 및 `EARLY_BODY_OPEN` 태그와 원인 분석 문장이 도출된다.
- [ ] **AC-3**: 페이스 닫힘 + 후방 타점 픽스처 입력 시 `FACE_CLOSED` 태그와 전방 타점 교정 피드백이 도출된다.
- [ ] **AC-4**: 정상 순차 가속 + 스퀘어 임팩트 픽스처 입력 시 `CLEAN_STRIKE` 및 `OPTIMAL_CHAIN` 태그가 도출된다.
- [ ] **AC-5**: `FusionEngineImpl.fuse()`가 앵커 동기화 ➔ 5단계 운동 체인 ➔ 인과 진단을 단일 파이프라인으로 수행하여 완전한 `FusedSwing`을 반환한다.
- [ ] **AC-6**: 골든 픽스처(JSON) 기반 인과 코칭 단위 테스트가 100% 통과한다.
- [ ] **AC-7**: `./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug
```
