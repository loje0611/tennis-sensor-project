# TASK-036 — 개인 Baseline 통계적 이상 탐지 및 피로도 모듈 구현 (D-7.4)

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (Phase 3 C그룹: D-7.4 개인 Baseline z-score 통계적 이상치 감지 및 세션 피로도 분석 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 제품 방향 결정 원칙 **D-7.4 (절대 기준 코칭 ➔ 개인 Baseline 대비 통계적 이상치 감지 & 피로도 트래킹)** 에 따라, 획일적인 절대 수치 강요를 탈피하고 사용자의 고유한 스윙 Baseline(평균 $\mu$ 및 표준편차 $\sigma$)을 축적하여 현재 스윙의 통계적 이상치(z-score 기반 $z = \frac{x - \mu}{\sigma}$) 및 세션 후반 폼 붕괴/피로도 누적을 감지하는 **통계적 이상 탐지 엔진(`StatisticalAnomalyDetector`)** 을 `:core:fusion` 모듈에 구현하는 작업을 규정합니다.

개인 맞춤형 Baseline 비교를 통해 "선수 대비 몇 점인가"가 아니라 "평소 나의 가장 좋은 폼 대비 어떤 지표가 유의미하게 무너졌는가"를 정밀 진단합니다.

### 1.2 범위
- `:core:fusion` 모듈 내 이상 탐지 패키지(`io.github.loje0611.tennisdoc.core.fusion.anomaly`) 신설:
  - `BaselineDistribution`: 단일 지표의 표본 수($N$), 평균($\mu$), 표준편차($\sigma$) 모델.
  - `PersonalBaseline`: 드릴별(`DrillType`) 누적 스윙 Baseline 모델.
  - `AnomalyResult`: 개별 지표의 z-score, 이상 여부, 심각도(`AnomalySeverity`: `NORMAL`, `WARNING`, `CRITICAL`), 진단 설명.
  - `FatigueAnalysis`: 세션 내 최근 스윙 추세 기반 피로도 점수($0.0 \sim 1.0$) 및 폼 붕괴 판정.
  - `BaselineComparisonReport`: 단일 스윙 또는 세션 단위 종합 비교 진단 리포트.
  - `StatisticalAnomalyDetector`: Baseline 점진적 업데이트(Welford's algorithm 또는 롤링 갱신), 이상치 계산, 피로도 트렌드 분석기.
- 핵심 분석 지표:
  1. `racketSpeed`: 라켓 피크 각속도 (피로 누적 시 감소)
  2. `energyTransferEfficiency`: 운동 체인 에너지 전달 효율
  3. `faceDeviationDeg`: 임팩트 라켓 페이스 각도 편차
  4. `wristRacketDelayMs`: 손목 ➔ 라켓 가속 지연 시간
  5. `elbowAngleDeg`: 임팩트 시 팔꿈치 폄 각도
- 골든 픽스처(JSON) 기반 JVM 단위 테스트:
  - Baseline 축적 후 정상 스윙(z-score $< 1.5$) 케이스.
  - 피로 누적 폼 붕괴 스윙(라켓 스피드 급락 $z < -2.5$, 체인 지연 급증 $z > 2.5$) 케이스.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **개인 Baseline (Personal Baseline, D-7.4)**: 사용자가 수행한 정상 스윙들의 주요 바이오마커 분포($\mu, \sigma$)로 정의되는 개인 맞춤형 기준선.
- **z-score (표준점수)**: $z = \frac{x - \mu}{\sigma}$로 계산되며, $x$가 평균에서 표준편차의 몇 배만큼 떨어져 있는지를 나타내는 척도.

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 제품 방향 결정: [`docs/PRODUCT_DIRECTION.md`](../PRODUCT_DIRECTION.md) (D-7.4)
- 융합 데이터 계약: [`docs/specs/TASK-031-core-fusion-module-data-contracts.md`](TASK-031-core-fusion-module-data-contracts.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 점진적 Baseline 갱신 (`updateBaseline`)
- 새로운 정상 `FusedSwing`이 수집될 때마다 Welford 알고리즘 또는 누적 모멘트를 사용하여 $O(1)$ 공간 복잡도로 각 지표의 평균과 분산/표준편차를 갱신한다:
  $$M_k = M_{k-1} + \frac{x_k - M_{k-1}}{k}$$
  $$S_k = S_{k-1} + (x_k - M_{k-1})(x_k - M_k)$$
  $$\sigma = \sqrt{\frac{S_k}{k - 1}} \quad (k \ge 2)$$
- 최소 $N \ge 3$회 이상의 표본이 축적되기 전에는 Baseline 신뢰도가 불충분하므로 `isReliable = false`로 플래그를 유지한다.

### FR-2: z-score 기반 이상치 탐지 (`detectAnomalies`)
- Baseline 지표 분포($\mu, \sigma$)와 현재 스윙의 측정값 $x$에 대해 $z = \frac{x - \mu}{\max(\sigma, \epsilon)}$을 계산한다 ($\epsilon = 1e-4$).
- 심각도(`AnomalySeverity`) 분류:
  - $|z| \ge 2.5$: `AnomalySeverity.CRITICAL` (`isAnomaly = true`)
  - $1.5 \le |z| < 2.5$: `AnomalySeverity.WARNING` (`isAnomaly = true`)
  - $|z| < 1.5$: `AnomalySeverity.NORMAL` (`isAnomaly = false`)
- 각 지표별 의미 있는 한국어 진단 설명 자동 생성 (예: "평소보다 라켓 헤드 스피드가 $2.8\sigma$ 유의미하게 감소했습니다.").

### FR-3: 세션 피로도 및 폼 붕괴 분석 (`analyzeFatigueTrend`)
- 세션 내 최근 $M$회(예: 최근 5~10회) 스윙의 연속적인 지표 변화 추세를 분석한다:
  - 라켓 스피드의 단조 감소 추세
  - 에너지 전달 효율의 지속적 저하 ($z < -1.5$)
  - 손목-라켓 릴리즈 타이밍 지연 증가
- 피로도 점수 `fatigueScore` ($0.0 \sim 1.0$) 계산 및 $0.7$ 이상 시 `isFatigued = true`, 폼 붕괴 경고(`formBreakdownSummary`) 도출.

### FR-4: 종합 비교 리포트 생성 (`compareWithBaseline`)
- `BaselineComparisonReport`:
  - `anomalies: List<AnomalyResult>`
  - `fatigue: FatigueAnalysis`
  - `coachingRecommendation: String` (이상치 및 피로도 상태에 따른 맞춤형 코칭 권고)

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.core.fusion.anomaly

import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.model.DrillType

data class BaselineDistribution(
    val count: Int,
    val mean: Float,
    val variance: Float,
    val stdDev: Float
)

data class PersonalBaseline(
    val drillType: DrillType,
    val totalSwings: Int,
    val distributions: Map<String, BaselineDistribution>,
    val isReliable: Boolean = totalSwings >= 5
)

enum class AnomalySeverity {
    NORMAL, WARNING, CRITICAL
}

data class AnomalyResult(
    val metricKey: String,
    val currentValue: Float,
    val baselineMean: Float,
    val zScore: Float,
    val isAnomaly: Boolean,
    val severity: AnomalySeverity,
    val description: String
)

data class FatigueAnalysis(
    val fatigueScore: Float,
    val isFatigued: Boolean,
    val formBreakdownSummary: String?
)

data class BaselineComparisonReport(
    val drillType: DrillType,
    val anomalies: List<AnomalyResult>,
    val fatigue: FatigueAnalysis,
    val coachingRecommendation: String
)

class StatisticalAnomalyDetector(
    private val warningZThreshold: Float = 1.5f,
    private val criticalZThreshold: Float = 2.5f
) {
    fun updateBaseline(existing: PersonalBaseline?, swing: FusedSwing): PersonalBaseline
    fun detectAnomalies(baseline: PersonalBaseline, swing: FusedSwing): BaselineComparisonReport
    fun analyzeFatigueTrend(recentSwings: List<FusedSwing>, baseline: PersonalBaseline): FatigueAnalysis
}
```

---

## 5. UI/UX 요구사항
- **N/A (순수 JVM 통계적 이상 탐지 모듈)**

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 수치적 안정성 (Numerical Stability)
- 표준편차가 0에 가까운 경우(모든 스윙이 동일값) 0으로 나누기 예외(`ArithmeticException` / `NaN`)를 방지하는 엡실론($\epsilon$) 가드 적용.

### 6.2 모듈 격리
- 순수 Kotlin JVM 클래스로 구현되어 Android 플랫폼 종속성 없이 동작.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **표본 부족 ($N < 5$)**: `isReliable = false`로 처리하며 z-score 경고를 과도하게 발화하지 않고 "Baseline 축적 중 (N/5)" 안내 반환.
- **이상치 극단값 ($|z| > 10$)**: z-score 클램핑 및 센서 튐(Glitch) 필터링.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `StatisticalAnomalyDetector`, `PersonalBaseline`, `BaselineComparisonReport` 클래스가 `:core:fusion`에 구현되고 컴파일에 성공한다.
- [ ] **AC-2**: 10회 정상 스윙 입력 후 Welford 알고리즘에 의해 평균과 표준편차가 수학적으로 정확하게 갱신된다.
- [ ] **AC-3**: Baseline 평균 대비 $+2.8\sigma$ 벗어난 스윙 입력 시 `severity == CRITICAL`, `isAnomaly == true`가 산출된다.
- [ ] **AC-4**: Baseline 평균 $\pm 1.0\sigma$ 이내의 스윙에 대해 `severity == NORMAL`, `isAnomaly == false`가 산출된다.
- [ ] **AC-5**: 세션 후반 라켓 스피드가 급락하고 체인 지연이 증가하는 연속 스윙 데이터에 대해 `isFatigued == true` 및 폼 붕괴 경고가 발생한다.
- [ ] **AC-6**: 표본 수 $N < 5$인 경우 `isReliable == false`로 처리되어 과도한 이상치 경고를 억제한다.
- [ ] **AC-7**: 골든 픽스처 기반 단위 테스트 및 `./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug
```
