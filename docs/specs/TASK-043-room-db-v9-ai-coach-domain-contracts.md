# TASK-043 명세서: Room DB v9 마이그레이션 및 AI 코치 도메인 계약 정의

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-17 | PM | 최초 작성 (Phase 4 착수: AI 코치 도메인 모델 정의 및 Room DB v9 마이그레이션 스키마 구축) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
Phase 3(`TASK-029~042`)을 통해 센서-비전 융합 엔진(`:core:fusion`)에서 5단계 운동 체인 딜레이(ms), 라켓 페이스 각도(°), 결함 인과 태그(`CausalCoachingEngine`) 및 Baseline 이상치 데이터가 정밀하게 산출됩니다.
Phase 4는 이 정량 지표들을 LLM에 전달하여 1:1 맞춤형 **"AI 코치 정밀 처방 리포트"** 를 생성하는 단계입니다. 본 태스크(`TASK-043`)는 그 기반이 되는 **공용 도메인 모델 정의, Room DB v9 스키마 마이그레이션, DAO 영속화 인터페이스**를 구축합니다.

### 1.2 범위
- `:core:model` 내 `AiCoachReport`, `CausalFlawDiagnosis`, `DrillRecommendation`, `CoachTone`, `LlmProvider` 공용 도메인 모델 신설.
- `:core:data` 내 `SwingSessionEntity`에 `aiCoachReportJson`, `aiReportGeneratedAt` 컬럼 추가 및 Room DB v9 스키마 마이그레이션(`MIGRATION_8_9`).
- `SwingSessionDao` 및 `SwingHistoryRepository`에 AI 코치 리포트 저장/갱신 메서드 추가.

---

## 2. Definitions & References (정의 및 참조)

- **D-7.5 LLM 리포트 설계 제약** (`docs/PRODUCT_DIRECTION.md`): 수치는 LLM이 임의로 생성하지 않고 결정론적 지표 JSON을 바탕으로 인과 설명/처방만 생성하며, 로컬 DB에 영속화함.
- **`AiCoachReport`**: AI 코치가 세션 전체 융합 분석 결과를 바탕으로 산출한 종합 진단 처방 데이터 객체.
- **`MIGRATION_8_9`**: Room DB 버전 8에서 9로의 무중단 스키마 마이그레이션 (기존 데이터 보존).

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: `:core:model` AI 코치 도메인 계약 정의
- `AiCoachReport` 데이터 클래스를 정의한다:
  - `reportId: String` (고유 ID)
  - `sessionId: String` (연관 세션 ID)
  - `generatedAtMillis: Long` (생성 타임스탬프)
  - `overallSummary: String` (세션 총평)
  - `keyStrengths: List<String>` (잘된 점 목록)
  - `primaryFlawDiagnosis: CausalFlawDiagnosis?` (핵심 결함 인과 분석)
  - `actionItems: List<String>` (다음 연습 집중 과제)
  - `recommendedDrills: List<DrillRecommendation>` (추천 드릴 목록)
  - `isFallbackReport: Boolean` (오프라인/Fallback 생성 여부)
  - `rawModelName: String?` (사용한 LLM 모델 식별자)
- `CausalFlawDiagnosis` 데이터 클래스를 정의한다:
  - `flawTitle: String` (결함 요약명)
  - `observedEffect: String` (관측된 현상)
  - `rootCause: String` (근본 원인)
  - `coachingCue: String` (교정 코칭 큐)
- `DrillRecommendation` 데이터 클래스를 정의한다:
  - `drillType: DrillType` (추천 드릴 종류)
  - `title: String` (드릴 명칭)
  - `focusPoint: String` (연습 시 집중 포인트)
  - `targetRepetitions: Int = 10` (권장 반복 횟수)
- `CoachTone` 열거형(`ENCOURAGING`, `ANALYTICAL`, `STRICT`) 및 `LlmProvider` 열거형(`MOCK`, `GEMINI`, `OPENAI`)을 정의한다.

### FR-2: `SwingSessionEntity` 엔티티 확장
- `SwingSessionEntity`에 다음 두 필드를 추가한다 (기본값 `null`):
  - `val aiCoachReportJson: String? = null`
  - `val aiReportGeneratedAt: Long? = null`

### FR-3: Room DB v9 마이그레이션 (`MIGRATION_8_9`)
- `TennisDocDatabase`의 버전을 `8`에서 `9`로 올린다 (`exportSchema = true`).
- `MIGRATION_8_9`를 작성하여 `swing_sessions` 테이블에 `aiCoachReportJson TEXT DEFAULT NULL`, `aiReportGeneratedAt INTEGER DEFAULT NULL` 컬럼을 `ALTER TABLE`로 추가한다.
- `TennisDocDatabase.getInstance()`의 마이그레이션 목록에 `MIGRATION_8_9`를 등록한다.

### FR-4: DAO 및 저장소 메서드 추가
- `SwingSessionDao`에 `updateAiCoachReport(sessionId: String, reportJson: String, generatedAt: Long)` 쿼리 메서드를 정의한다 (`@Query("UPDATE swing_sessions SET aiCoachReportJson = :reportJson, aiReportGeneratedAt = :generatedAt WHERE sessionId = :sessionId")`).
- `SwingHistoryRepository` 및 `SwingHistoryRepositoryImpl`에 `saveAiCoachReport(sessionId: String, reportJson: String, generatedAt: Long = System.currentTimeMillis())` 메서드를 정의 및 구현한다.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

```kotlin
package io.github.loje0611.tennisdoc.core.model

data class AiCoachReport(
    val reportId: String,
    val sessionId: String,
    val generatedAtMillis: Long,
    val overallSummary: String,
    val keyStrengths: List<String> = emptyList(),
    val primaryFlawDiagnosis: CausalFlawDiagnosis? = null,
    val actionItems: List<String> = emptyList(),
    val recommendedDrills: List<DrillRecommendation> = emptyList(),
    val isFallbackReport: Boolean = false,
    val rawModelName: String? = null,
)

data class CausalFlawDiagnosis(
    val flawTitle: String,
    val observedEffect: String,
    val rootCause: String,
    val coachingCue: String,
)

data class DrillRecommendation(
    val drillType: DrillType,
    val title: String,
    val focusPoint: String,
    val targetRepetitions: Int = 10,
)

enum class CoachTone {
    ENCOURAGING,
    ANALYTICAL,
    STRICT,
}

enum class LlmProvider {
    MOCK,
    GEMINI,
    OPENAI,
}
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- N/A (데이터 모델 및 Room DB 계층 백엔드 모듈 태스크)

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **순수 JVM 독립성**: `:core:model`은 Android 프레임워크(`android.*`)에 대한 의존성이 없는 순수 Kotlin JVM 모듈이어야 한다.
- **무결성 및 하위 호환성**: Room DB v8에서 v9로 마이그레이션 시 기존 세션 및 스윙 레코드 데이터가 유실되지 않아야 한다.
- **아키텍처 규칙 준수**: `verifyModuleDependencies` 검사에서 순환 또는 허용되지 않은 모듈 간 의존성 위반이 없어야 한다.

---

## 7. Error Handling & Edge Cases (오류 처리 및 예외 상황)

- **존재하지 않는 `sessionId`에 대한 리포트 저장**: `updateAiCoachReport` 호출 시 대상 행이 없더라도 예외가 발생하지 않고 조용히 0개 행이 갱신된다.
- **리포트가 아직 생성되지 않은 세션**: `aiCoachReportJson` 및 `aiReportGeneratedAt`은 `null`을 반환하며, 기존 세션 상세 조회 로직에 영향을 주지 않는다.

---

## 8. Acceptance Criteria (수용 기준)

- **AC-1 (도메인 모델)**: `:core:model`에 `AiCoachReport`, `CausalFlawDiagnosis`, `DrillRecommendation`, `CoachTone`, `LlmProvider`가 올바르게 정의되어 단위 테스트로 인스턴스 생성이 검증되어야 한다.
- **AC-2 (DB v9 스키마 & 마이그레이션)**: `TennisDocDatabase` 버전이 9로 업데이트되고, `MIGRATION_8_9`를 통해 `swing_sessions` 테이블에 `aiCoachReportJson` 및 `aiReportGeneratedAt` 컬럼이 추가되어야 하며, 기존 데이터가 유실되지 않아야 한다.
- **AC-3 (DAO & Repository)**: `SwingSessionDao.updateAiCoachReport` 및 `SwingHistoryRepository.saveAiCoachReport`를 호출했을 때 해당 세션의 리포트 정보가 올바르게 갱신되어야 한다.
- **AC-4 (아키텍처 규칙)**: 순수 JVM 모듈 `:core:model`은 외부 라이브러리(Android 프레임워크) 의존이 없어야 하며, `verifyModuleDependencies` 검증을 통과해야 한다.
- **AC-5 (빌드 및 테스트 통과)**: 선언된 테스트 명령이 0 failure로 통과해야 한다.

---

## 9. Testing Instructions (테스트 명령)

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

# 모듈 의존성 및 단위 테스트
./gradlew :core:model:test :core:data:test verifyModuleDependencies --rerun-tasks
```
