# TASK-044 명세서: 세션 융합 지표 집계 및 LLM Context Builder 구현

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-17 | PM | 최초 작성 (Phase 4 A그룹: 세션 내 융합 지표 집계 및 프라이버시 보호형 LLM Context Builder 구축) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
Phase 4의 핵심 목표는 LLM을 활용한 개인화 AI 코치 리포트 생성입니다.
D-7.5 설계 제약에 따라 **"수치는 LLM이 임의로 생성하지 않으며, 결정론적 분석 엔진이 산출한 정밀 수치만을 구조화 JSON으로 전달"** 해야 하며, **"카메라 영상이나 33개 관절 랜드마크 원시 시계열은 프라이버시 보호를 위해 전송하지 않아야"** 합니다.
본 태스크(`TASK-044`)는 `:core:fusion` 모듈에서 세션 내 모든 융합 스윙 데이터(`List<FusedSwing>`)와 개인 베이스라인(`PersonalBaseline?`)을 입력받아, LLM 프롬프트에 주입할 **결정론적 집계 컨텍스트(`SessionPrescriptionContext`)** 를 추출·직렬화하는 `SessionPrescriptionContextBuilder`를 구현합니다.

### 1.2 범위
- `:core:model` 또는 `:core:fusion` 내 `SessionPrescriptionContext` 및 관련 하위 데이터 구조 정의
- `:core:fusion` 내 `SessionPrescriptionContextBuilder` 구현 (통계 집계, 5단계 체인 딜레이 평균, 페이스 분포, 주요 결함 태그 집계, 대표 결함 스윙 선정, 피로도/Baseline 분석 통합)
- 프라이버시 보호를 위한 JSON 직렬화/역직렬화 함수 또는 헬퍼 지원
- 골든 세션 픽스처 기반 단위 테스트 작성 및 정량 지표 일치 검증

---

## 2. Definitions & References (정의 및 참조)

- **D-7.5 LLM 리포트 설계 제약** (`docs/PRODUCT_DIRECTION.md`): 수치는 프롬프트 컨텍스트에 주입된 정량 지표만을 사용하며, 원시 영상/포즈 시계열은 전송하지 않음.
- **`SessionPrescriptionContext`**: 세션 전체의 스윙 역학 통계, 결함 빈도, 5단계 체인 구간별 지연, Baseline 이상치 및 피로도 상태를 담은 정밀 집계 데이터 구조.
- **`RepresentativeFlawSwing`**: 세션 중 에너지 효율 누수 또는 페이스 편차가 가장 큰 대표 결함 스윙의 요약 스냅샷 (LLM이 구체적 인과 관계를 설명하는 데 활용).

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: `SessionPrescriptionContext` 데이터 구조 정의
다음 정보를 포함하는 불변 데이터 클래스를 정의한다:
1. **세션 기본 정보**:
   - `sessionId: String`
   - `drillType: DrillType`
   - `totalSwingCount: Int`
   - `durationSeconds: Long`
2. **운동 체인 및 효율 통계**:
   - `sequentialChainRatePercent: Int` (순차적 가속 체인 성공 비율, 0~100)
   - `averageEnergyEfficiency: Float` (평균 에너지 전달 효율 %)
   - `maxEnergyEfficiency: Float` (최고 에너지 전달 효율 %)
   - `averageChainDurationMs: Long` (체인 총 소요 시간 평균 ms)
   - `stageDelaysMs`: 각 단계 간 평균 지연 시간 (`hipToShoulderMs`, `shoulderToWristMs`, `wristToRacketMs`, `racketToImpactMs`)
3. **임팩트 및 페이스 상태 분포**:
   - `squareFaceRatePercent: Int` (스퀘어 임팩트 비율 %)
   - `openFaceRatePercent: Int` (열린 페이스 비율 %)
   - `closedFaceRatePercent: Int` (닫힌 페이스 비율 %)
   - `averageFaceDeviationDeg: Float` (평균 페이스 편차각 °)
   - `averageRacketSpeed: Float` (평균 라켓 최고 각속도/속도 dps)
4. **결함 태그 및 패턴 집계**:
   - `flawTagCounts: Map<String, Int>` (세션 내 발생한 진단 태그별 빈도수, e.g. `EARLY_BODY_OPEN`: 4, `FACE_OPEN`: 5)
   - `primaryFlawTag: String?` (가장 빈도가 높은 주요 결함 태그)
5. **대표 결함 스윙 (최대 2건)**:
   - `representativeFlaws: List<RepresentativeFlawSwing>` (효율 최저 또는 편차 최대 스윙의 `swingId`, `faceState`, `deviationDeg`, `energyEfficiency`, `stageDelaysMs`, `diagnosisTags`)
6. **피로도 및 Baseline 비교 (Optional)**:
   - `isFatigued: Boolean` (세션 후반 폼 붕괴/피로 감지 여부)
   - `fatigueScore: Float` (피로도 점수 0.0~1.0)
   - `baselineAnomalyCount: Int` (Baseline 대비 이상치로 탐지된 지표 수)

### FR-2: `SessionPrescriptionContextBuilder` 구현
- `buildContext(sessionId: String, drillType: DrillType, swings: List<FusedSwing>, baseline: PersonalBaseline? = null, durationSeconds: Long = 0L): SessionPrescriptionContext`
- 스윙 목록(`swings`)이 비어있는 경우 안전한 기본값(`totalSwingCount = 0`, 비율 0, 빈 리스트 등)을 반환한다.
- 5단계 체인 딜레이 및 에너지 효율, 페이스 상태를 단일 패스로 정확하게 계산한다.
- `CausalCoachingEngine` 진단 태그 중 정상 태그(`CLEAN_STRIKE`, `OPTIMAL_CHAIN`, `SQUARE_FACE`)를 제외한 결함 태그들의 빈도를 집계하여 `primaryFlawTag`를 산출한다.
- `StatisticalAnomalyDetector`를 활용하여 `baseline`이 주어졌을 때 세션의 피로도(`FatigueAnalysis`) 및 이상치 개수를 컨텍스트에 포함한다.

### FR-3: JSON 직렬화 지원
- LLM 프롬프트에 즉시 주입할 수 있도록 `SessionPrescriptionContext`를 간결하고 가독성 높은 JSON 문자열로 직렬화하는 헬퍼 메서드(`toJsonString()`)를 제공한다.
- 원시 포즈 랜드마크(x,y,z,visibility)나 IMU 50Hz 시계열 배열이 직렬화 결과에 절대 포함되지 않도록 보장한다.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

```kotlin
package io.github.loje0611.tennisdoc.core.fusion.context

import io.github.loje0611.tennisdoc.core.fusion.model.DrillType
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.anomaly.PersonalBaseline

data class StageDelaysSummary(
    val hipToShoulderMs: Long,
    val shoulderToWristMs: Long,
    val wristToRacketMs: Long,
    val racketToImpactMs: Long
)

data class RepresentativeFlawSwing(
    val swingId: String,
    val faceState: RacketFaceState,
    val deviationDeg: Float,
    val energyEfficiency: Float,
    val stageDelaysMs: StageDelaysSummary,
    val diagnosisTags: List<String>
)

data class SessionPrescriptionContext(
    val sessionId: String,
    val drillType: DrillType,
    val totalSwingCount: Int,
    val durationSeconds: Long,
    val sequentialChainRatePercent: Int,
    val averageEnergyEfficiency: Float,
    val maxEnergyEfficiency: Float,
    val averageChainDurationMs: Long,
    val stageDelaysMs: StageDelaysSummary,
    val squareFaceRatePercent: Int,
    val openFaceRatePercent: Int,
    val closedFaceRatePercent: Int,
    val averageFaceDeviationDeg: Float,
    val averageRacketSpeed: Float,
    val flawTagCounts: Map<String, Int>,
    val primaryFlawTag: String?,
    val representativeFlaws: List<RepresentativeFlawSwing>,
    val isFatigued: Boolean = false,
    val fatigueScore: Float = 0f,
    val baselineAnomalyCount: Int = 0
) {
    fun toJsonString(): String
}

class SessionPrescriptionContextBuilder {
    fun buildContext(
        sessionId: String,
        drillType: DrillType,
        swings: List<FusedSwing>,
        baseline: PersonalBaseline? = null,
        durationSeconds: Long = 0L
    ): SessionPrescriptionContext
}
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- N/A (데이터 집계 및 프롬프트 컨텍스트 엔지니어링 백엔드 모듈 태스크)

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **순수 JVM 독립성**: `:core:fusion` 모듈 내에서 Android 프레임워크(`android.*`) 의존 없이 순수 Kotlin 표준 라이브러리로 구동되어야 한다.
- **수치 무결성 및 결정론성**: 동일한 `List<FusedSwing>` 입력에 대해 언제나 동일한 집계 수치를 산출해야 한다 (Float 부동소수점 오차는 소수점 둘째 자리 반올림 등 표준 규칙 적용).
- **프라이버시 가드레일**: `toJsonString()` 출력 페이로드 크기는 2KB 이하로 컴팩트해야 하며, 관절 좌표나 IMU 샘플 원시 데이터가 누출되지 않아야 한다.

---

## 7. Error Handling & Edge Cases (오류 처리 및 예외 상황)

- **빈 스윙 세션 (`swings.isEmpty()`)**: 크래시 없이 `totalSwingCount = 0`, 모든 비율 `0`, 평균치 `0.0f`, 빈 맵과 빈 리스트를 가진 정상 컨텍스트 반환.
- **단일 스윙 세션 (`swings.size == 1`)**: 평균과 최대값이 동일하게 산출되며 정상 빌드.
- **체인 결함 스윙만 존재하는 경우**: `sequentialChainRatePercent = 0`, `primaryFlawTag`가 가장 빈번한 결함 태그로 정확히 지정됨.
- **Baseline 미제공 (`baseline == null`)**: `isFatigued = false`, `baselineAnomalyCount = 0`으로 기본 처리.

---

## 8. Acceptance Criteria (수용 기준)

- **AC-1 (컨텍스트 모델 정의)**: `SessionPrescriptionContext`, `StageDelaysSummary`, `RepresentativeFlawSwing`이 명세대로 정의되고 불변성이 보장되어야 한다.
- **AC-2 (정확한 수치 집계)**: 복수 개의 `FusedSwing`이 주어졌을 때 순차 체인 비율, 페이스 상태 분포 비율, 평균 딜레이(ms) 및 결함 태그 빈도가 정확히 집계되어야 한다.
- **AC-3 (대표 결함 스윙 선정)**: 클린 스윙과 결함 스윙이 섞여 있을 때, 정상 스윙이 아닌 결함 스윙(에너지 효율 최저 또는 페이스 이상)이 `representativeFlaws`에 우선 선정되어야 한다.
- **AC-4 (프라이버시 JSON 직렬화)**: `toJsonString()` 출력에 관절 랜드마크(`landmarks`, `x`, `y`, `z`) 및 IMU 시계열(`accelX`, `gyroZ`) 필드가 일체 포함되지 않아야 한다.
- **AC-5 (빈 세션 예외 처리)**: `swings`가 비어있을 때 `ArithmeticException`(0으로 나누기) 없이 안전한 기본 객체를 반환해야 한다.
- **AC-6 (빌드 및 테스트 통과)**: 선언된 테스트 명령이 0 failure로 통과해야 한다.

---

## 9. Testing Instructions (테스트 명령)

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

# 모듈 의존성 및 단위 테스트
./gradlew :core:fusion:test verifyModuleDependencies --rerun-tasks
```
