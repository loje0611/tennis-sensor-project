# TASK-033 — 5단계 통합 운동 체인(Kinetic Chain) 분석 엔진 구현

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (Phase 3 B그룹: 비전 3단계+센서 2단계 결합 5단계 운동 체인 분석 엔진 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 비전 3단계 관절 가속(골반 ➔ 어깨 ➔ 손목)과 센서 2단계 라켓 가속(라켓 회전 ➔ 임팩트 충격)을 시간 동기화(`SyncAnchor`)를 통해 결합하여, 테니스 스윙의 전신 에너지 전달 순서와 타이밍을 정밀 분석하는 **5단계 통합 운동 체인 분석 엔진(`KineticChain5StageAnalyzer`)** 을 `:core:fusion` 모듈에 구현하는 작업을 규정합니다.

비전 단독으로는 관측할 수 없는 "라켓의 최종 릴리즈 및 임팩트 충격 순간"을 IMU 센서와 결합함으로써, 스윙 에너지 손실의 원인이 되는 결함(어깨 조기 회전, 라켓 가속 지연 등)을 정량화하고 에너지 전달 효율 점수(`energyTransferEfficiency`)를 산출합니다.

### 1.2 범위
- `:core:fusion` 모듈 내 5단계 운동 체인 분석 클래스 구현:
  - `KineticChain5StageAnalyzer`: `poses: List<PoseFrame>`, `imuSamples: List<ImuDataPoint>`, `anchor: SyncAnchor`를 입력받아 `KineticChain5Stage`를 계산.
- 각 단계별 피크 타임스탬프 및 크기 산출:
  - **Stage 1 (HIP)**: 비전 골반 벡터(Left Hip 23, Right Hip 24) 각속도 피크 ($t_{\text{hip}}$, $\omega_{\text{hip}}$)
  - **Stage 2 (SHOULDER)**: 비전 어깨 벡터(Left Shoulder 11, Right Shoulder 12) 각속도 피크 ($t_{\text{shoulder}}$, $\omega_{\text{shoulder}}$)
  - **Stage 3 (WRIST)**: 비전 손목 관절(Wrist 16/15) 3D 선속도 피크 ($t_{\text{wrist}}$, $v_{\text{wrist}}$)
  - **Stage 4 (RACKET)**: 센서 IMU 자이로 합성 각속도 피크 ($t_{\text{racket}}$, $\omega_{\text{racket}}$) — 동기화 오프셋 보정 적용
  - **Stage 5 (IMPACT)**: 센서 IMU 가속도 충격 피크 ($t_{\text{impact}}$, $a_{\text{impact}}$) — 동기화 오프셋 보정 적용
- 분석 지표 계산:
  - 순차적 가속 여부 판별 (`isSequential = (t_hip <= t_shoulder <= t_wrist <= t_racket <= t_impact)`)
  - 인접 단계별 지연 시간 (`delayFromPreviousMs`)
  - 에너지 전달 효율 점수 (`energyTransferEfficiency: Float`, 0.0 ~ 100.0점)
- 골든 픽스처(JSON) 기반 JVM 단위 테스트:
  - 정상 프로형 순차 체인 데이터셋 (100점/고효율).
  - 결함형 체인 데이터셋 (어깨 조기 회전, 손목 선가속 등).

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **5단계 운동 체인(5-Stage Kinetic Chain)**: 하지 회전력 ➔ 골반 ➔ 어깨 ➔ 손목 ➔ 라켓 ➔ 임팩트로 이어지는 채찍(Whip) 형태의 순차적 가속/감속 역학 모델.
- **`KineticChain5Stage`**: TASK-031에서 정의된 5단계 운동 체인 모델 (`stages: List<KineticStage>`, `isSequential: Boolean`, `totalDurationMs: Long`, `energyTransferEfficiency: Float`).

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 융합 데이터 계약: [`docs/specs/TASK-031-core-fusion-module-data-contracts.md`](TASK-031-core-fusion-module-data-contracts.md)
- 임팩트 앵커 동기화: [`docs/specs/TASK-032-impact-anchor-synchronization.md`](TASK-032-impact-anchor-synchronization.md)
- 비전 운동 체인: [`docs/specs/TASK-024-core-vision-kinetic-chain.md`](TASK-024-core-vision-kinetic-chain.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 5단계 피크 검출 및 시간축 통합
- 비전 포즈 시계열에서 골반 각속도, 어깨 각속도, 손목 선속도의 극대값 시점($t_{\text{hip}}$, $t_{\text{shoulder}}$, $t_{\text{wrist}}$)을 추출한다.
- 센서 IMU 시계열에서 자이로 각속도 피크($t_{\text{racket, sensor}}$) 및 가속도 임팩트 피크($t_{\text{impact, sensor}}$)를 추출한다.
- `anchor.timeOffsetMs`를 적용하여 센서 타임스탬프를 비전 타임스탬프 기준 공통 시간축으로 변환한다:
  $$t_{\text{racket}} = t_{\text{racket, sensor}} - \text{anchor.timeOffsetMs}$$
  $$t_{\text{impact}} = t_{\text{impact, sensor}} - \text{anchor.timeOffsetMs}$$

### FR-2: `KineticStage` 리스트 구성
- 정확히 5개의 `KineticStage` 객체를 생성하여 순서대로 배치한다:
  1. `KineticStage(KineticStageType.HIP, t_hip, v_hip, 0L)`
  2. `KineticStage(KineticStageType.SHOULDER, t_shoulder, v_shoulder, t_shoulder - t_hip)`
  3. `KineticStage(KineticStageType.WRIST, t_wrist, v_wrist, t_wrist - t_shoulder)`
  4. `KineticStage(KineticStageType.RACKET, t_racket, v_racket, t_racket - t_wrist)`
  5. `KineticStage(KineticStageType.IMPACT, t_impact, v_impact, t_impact - t_racket)`

### FR-3: 순차성 및 총 지속 시간 판별
- `isSequential = (t_hip <= t_shoulder && t_shoulder <= t_wrist && t_wrist <= t_racket && t_racket <= t_impact)`
- `totalDurationMs = max(0L, t_impact - t_hip)`

### FR-4: 에너지 전달 효율 점수 (`energyTransferEfficiency`) 계산
- 기본 점수 산출 로직:
  - 순차성 점수: `isSequential == true`인 경우 기본 40점 부여 (비순차 시 0점).
  - 단계별 이상적 타이밍 윈도우 점수 (각 단계별 최대 15점씩, 총 60점):
    - 골반 ➔ 어깨 지연: $15\text{ms} \sim 80\text{ms}$ 범위 시 15점 (벗어날수록 선형 감점)
    - 어깨 ➔ 손목 지연: $15\text{ms} \sim 80\text{ms}$ 범위 시 15점
    - 손목 ➔ 라켓 지연: $10\text{ms} \sim 60\text{ms}$ 범위 시 15점
    - 라켓 ➔ 임팩트 지연: $5\text{ms} \sim 40\text{ms}$ 범위 시 15점
  - 최종 점수: 0.0f ~ 100.0f 범위로 클램핑.

### FR-5: 데이터 부재 및 비동기화 처리
- `anchor.isSynchronized == false`이거나 데이터가 부족하여 특정 스테이지 피크 검출이 불가한 경우:
  - `isSequential = false`, `energyTransferEfficiency = 0.0f`, 기본 5개 스테이지를 안전하게 반환하며 예외를 던지지 않는다.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.core.fusion.analysis

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

class KineticChain5StageAnalyzer {
    /**
     * 비전 포즈 프레임과 IMU 샘플, 동기화 앵커를 결합하여 5단계 운동 체인을 분석한다.
     */
    fun analyze(
        poses: List<PoseFrame>,
        imuSamples: List<ImuDataPoint>,
        anchor: SyncAnchor
    ): KineticChain5Stage
}
```

---

## 5. UI/UX 요구사항
- **N/A (순수 JVM 역학 분석 알고리즘 모듈)**

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 결정론적 실행 (Deterministic Execution)
- 동일한 입력 픽스처(포즈 + IMU + 앵커)에 대해 항상 100% 동일한 스테이지 피크 타임스탬프와 효율 점수를 산출해야 한다.

### 6.2 실행 속도
- 단일 스윙 분석 소요 시간 $10\text{ms}$ 미만.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **역순 가속 발생 (어깨가 골반보다 먼저 회전)**: `isSequential = false`, 해당 지연 시간 음수 기록 및 효율 점수 감점.
- **라켓 가속 피크 미검출**: 센서 최대값으로 대체 또는 0점 처리.
- **임팩트 후 라켓 가속 ($t_{\text{racket}} > t_{\text{impact}}$)**: 결함 상태로 판별 (`isSequential = false`).

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `KineticChain5StageAnalyzer` 클래스가 `:core:fusion` 모듈에 구현되고 컴파일에 성공한다.
- [ ] **AC-2**: 완벽한 순차 가속 골든 픽스처(골반 $t=100$, 어깨 $t=140$, 손목 $t=180$, 라켓 $t=210$, 임팩트 $t=230$)에서 `isSequential == true`, `energyTransferEfficiency >= 90.0f`를 산출한다.
- [ ] **AC-3**: 역순 가속 골든 픽스처(어깨 $t=100$, 골반 $t=140$)에서 `isSequential == false`, `energyTransferEfficiency < 50.0f`를 산출한다.
- [ ] **AC-4**: `anchor.timeOffsetMs`에 따른 센서 타임스탬프의 시간축 보정이 정확히 적용된다.
- [ ] **AC-5**: 산출된 `KineticChain5Stage.stages`는 항상 정확히 5개 요소(`HIP`, `SHOULDER`, `WRIST`, `RACKET`, `IMPACT`)를 순서대로 포함한다.
- [ ] **AC-6**: 골든 픽스처(JSON) 기반 5단계 운동 체인 단위 테스트가 100% 통과한다.
- [ ] **AC-7**: `./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug
```
