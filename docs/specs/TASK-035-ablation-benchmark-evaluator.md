# TASK-035 — Ablation 자동 채점 검증 도구 구현 (D-7.1)

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (Phase 3 C그룹: D-7.1 센서-비전 융합 vs 비전 단독 Ablation 자동 채점 평가 도구 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 제품 방향 결정 원칙 **D-7.1 (Ablation Test: 비전 단독 vs 센서+비전 융합 차별화 정량화)** 에 따라, 센서(IMU) 데이터의 추가가 스윙 분석과 코칭 품질에 미치는 부가가치를 수학적·정량적으로 측정하고 검증하는 **자동 채점 평가 도구(`AblationEvaluator`)** 를 `:core:fusion` 모듈에 구현하는 작업을 규정합니다.

센서가 결합됨으로써 도출되는 진단 태그와 코칭 피드백이 비전 단독 대비 유의미한 차별화(진단 태그 Jaccard 거리 $D_J \ge 0.3$, 인과 피드백 제공, 5단계 운동 체인 관측성 확장)를 달성하는지 자동으로 채점하여 CI/CD 및 단위 테스트 파이프라인에서 융합 엔진의 성능을 보증합니다.

### 1.2 범위
- `:core:fusion` 모듈 내 평가 패키지(`io.github.loje0611.tennisdoc.core.fusion.evaluation`) 신설:
  - `AblationScore`: 단일 스윙에 대한 채점 결과 모델 (`tagJaccardDistance`, `isJaccardCriteriaMet`, `hasCausalExplanation`, `kineticChainGain`, `overallPass`).
  - `AblationTestCase`: 비교 평가용 단일 입력 케이스 모델 (융합 입력 + 비전 단독 기준 데이터).
  - `AblationDatasetReport`: 다중 데이터셋 일괄 채점 종합 통계 리포트.
  - `AblationEvaluator`: 융합 결과(`FusedSwing`)와 비전 단독 결과를 비교 채점하는 핵심 평가기.
- 정량 채점 알고리즘 구현:
  - **진단 태그 Jaccard 거리 계산**:
    $$J(\text{Fusion}, \text{Vision}) = \frac{|\text{Tags}_{\text{Fusion}} \cap \text{Tags}_{\text{Vision}}|}{|\text{Tags}_{\text{Fusion}} \cup \text{Tags}_{\text{Vision}}|}, \quad D_J = 1.0 - J$$
    - 기준: $D_J \ge 0.3$ (30% 이상의 신규/심층 진단 정보 획득 시 합격).
  - **인과 코칭 유효성 채점**:
    - 피드백 문장에 인과 관계 키워드("때문에", "원인", "밀려", "닫혀", "열려", "지연", "타이밍") 및 라켓 페이스 상태 언급 여부 검증.
  - **운동 체인 관측성 이득 채점**:
    - 비전 3단계(골반·어깨·손목) 대비 센서 2단계(라켓 가속·임팩트 충격) 추가로 인한 관측 분절 수 확장(+2 Stages) 검증.
- 골든 픽스처(JSON) 데이터셋 및 JVM 단위 테스트 구축:
  - 최소 5개 이상의 표준 스윙 케이스(페이스 열림, 후방 타점 닫힘, 파워 유실 지연, 클린 스트라이크, 비동기 폴백)를 포함한 `golden_ablation_dataset.json`.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **Ablation Test (소거 테스트, D-7.1)**: 특정 모달리티(센서)를 제거했을 때와 포함했을 때의 분석 결과 차이를 정량화하여 센서의 기여도를 입증하는 기법.
- **Jaccard 거리 ($D_J$)**: 두 집합 간의 비유사도를 나타내는 척도 ($0.0 \sim 1.0$). 0이면 완전 일치, 1이면 공통 원소 없음.

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 제품 방향 결정: [`docs/PRODUCT_DIRECTION.md`](../PRODUCT_DIRECTION.md) (D-7.1)
- 융합 코칭 엔진: [`docs/specs/TASK-034-causal-coaching-engine.md`](TASK-034-causal-coaching-engine.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: Jaccard 유사도 및 거리 계산
- `fusionTags: List<String>`와 `visionOnlyTags: List<String>`를 입력받아 Jaccard 거리 $D_J$를 계산한다:
  - 두 집합이 모두 비어있는 경우: $D_J = 0.0\text{f}$
  - 합집합이 0이 아닌 경우: $D_J = 1.0\text{f} - \frac{|\text{fusionTags} \cap \text{visionOnlyTags}|}{|\text{fusionTags} \cup \text{visionOnlyTags}|}$
- `isJaccardCriteriaMet = (D_J >= 0.3f)`

### FR-2: 인과 설명 품질 검증
- `fusedSwing.diagnosis`의 `causalExplanation` 및 `coachingFeedback`이 다음 조건을 만족하는지 채점한다:
  - 필수 인과 키워드 목록(예: `"원인"`, `"때문에"`, `"밀려"`, `"열려"`, `"닫혀"`, `"지연"`, `"페이스"`, `"타이밍"`, `"에너지"`) 중 1개 이상 포함 여부.
  - 비전 단독 피드백 대비 라켓 상태에 대한 구체적 설명 포함 여부.

### FR-3: 운동 체인 관측성 확장 검증
- `fusedSwing.kineticChain.stages.size == 5`이고 `KineticStageType.RACKET`과 `KineticStageType.IMPACT`가 유효한 센서 피크 데이터를 포함하고 있는지 확인하여 `kineticChainStageGain == 2`를 부여한다.

### FR-4: 단일 스윙 채점 (`evaluate`)
- 입력: `fusedSwing: FusedSwing`, `visionOnlyTags: List<String>`, `visionOnlyFeedback: String`
- 출력: `AblationScore`
  - `overallPass = isJaccardCriteriaMet && hasCausalExplanation && (kineticChainStageGain >= 2 || !fusedSwing.anchor.isSynchronized)`

### FR-5: 데이터셋 일괄 채점 리포트 생성 (`evaluateDataset`)
- `List<AblationTestCase>` 전체에 대해 `FusionEngine` 실행 및 채점을 수행하고 종합 통계 리포트 `AblationDatasetReport`를 반환한다:
  - `totalCases: Int`
  - `passedCases: Int`
  - `passRate: Float` ($= \text{passedCases} / \text{totalCases}$)
  - `averageJaccardDistance: Float`
  - `summary: String` (결과 요약 문자열)

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.core.fusion.evaluation

import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngine
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

data class AblationScore(
    val tagJaccardDistance: Float,
    val isJaccardCriteriaMet: Boolean,
    val hasCausalExplanation: Boolean,
    val kineticChainStageGain: Int,
    val overallPass: Boolean
)

data class AblationTestCase(
    val testCaseId: String,
    val drillType: DrillType,
    val poses: List<PoseFrame>,
    val imuSamples: List<ImuDataPoint>,
    val visionOnlyTags: List<String>,
    val visionOnlyFeedback: String
)

data class AblationDatasetReport(
    val totalCases: Int,
    val passedCases: Int,
    val passRate: Float,
    val averageJaccardDistance: Float,
    val summary: String
)

class AblationEvaluator(
    private val jaccardThreshold: Float = 0.3f
) {
    fun evaluate(
        fusedSwing: FusedSwing,
        visionOnlyTags: List<String>,
        visionOnlyFeedback: String
    ): AblationScore

    fun evaluateDataset(
        dataset: List<AblationTestCase>,
        engine: FusionEngine
    ): AblationDatasetReport
}
```

---

## 5. UI/UX 요구사항
- **N/A (순수 JVM Ablation 정량 평가 및 벤치마크 도구)**

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 결정론적 채점 (Deterministic Scoring)
- 동일한 입력에 대해 일관되고 재현 가능한 점수 및 리포트를 생성해야 한다.

### 6.2 모듈 격리
- `:core:fusion`의 순수 Kotlin/JVM 클래스로 구현되어 CI 단위 테스트에서 즉각 실행 가능해야 한다.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **동일한 태그셋 전달 (비차별화 케이스)**: $D_J = 0.0\text{f}$, `isJaccardCriteriaMet = false`로 정확히 불합격 판정.
- **빈 태그셋 또는 빈 피드백**: 크래시 없이 $D_J = 0.0\text{f}$ 및 안전한 채점 결과 반환.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `AblationEvaluator`, `AblationScore`, `AblationDatasetReport` 클래스가 `:core:fusion`에 구현되고 컴파일에 성공한다.
- [ ] **AC-2**: 융합 태그 `["FACE_OPEN", "EARLY_BODY_OPEN"]`와 비전 태그 `["EARLY_BODY_OPEN"]` 비교 시 $D_J = 0.5\text{f}$, `isJaccardCriteriaMet == true`가 산출된다.
- [ ] **AC-3**: 동일 태그셋 비교 시 $D_J = 0.0\text{f}$, `isJaccardCriteriaMet == false`로 불합격 처리된다.
- [ ] **AC-4**: 인과 키워드가 포함된 융합 진단에 대해 `hasCausalExplanation == true`가 정확히 판정된다.
- [ ] **AC-5**: 5단계 운동 체인을 포함하는 정상 융합 스윙에 대해 `kineticChainStageGain == 2`가 부여된다.
- [ ] **AC-6**: `golden_ablation_dataset.json` (5개 이상 케이스)을 이용한 데이터셋 평가 단위 테스트에서 전체 통과율 $100\%$, 평균 Jaccard 거리 $\ge 0.3\text{f}$를 기록한다.
- [ ] **AC-7**: `./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug
```
