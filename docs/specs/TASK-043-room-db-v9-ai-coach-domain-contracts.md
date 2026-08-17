# TASK-043 명세서: Room DB v9 마이그레이션 및 AI 코치 도메인 계약 정의

## 1. 개요 및 배경

### 1.1 배경
Phase 3(`TASK-029~042`)을 통해 센서-비전 융합 엔진(`:core:fusion`)에서 5단계 운동 체인 딜레이(ms), 라켓 페이스 각도(°), 결함 인과 태그(`CausalCoachingEngine`) 및 Baseline 이상치 데이터가 정밀하게 산출됩니다.
Phase 4는 이 정량 지표들을 LLM에 전달하여 1:1 맞춤형 **"AI 코치 정밀 처방 리포트"** 를 생성하는 단계이며, 본 태스크(`TASK-043`)는 그 기반이 되는 **공용 도메인 모델 정의, Room DB v9 스키마 마이그레이션, DAO 영속화 인터페이스**를 구축합니다.

### 1.2 설계 가드레일 (D-7.5 준수)
- **도메인 모델의 결정론적 구조화**: `AiCoachReport`는 총평, 핵심 인과 결함 진단(`CausalFlawDiagnosis`), 집중 과제(`actionItems`), 맞춤 추천 드릴(`DrillRecommendation`), Fallback 여부(`isFallbackReport`)로 명확히 필드화하여 LLM이 수치를 왜곡하지 않고 구조화된 형식으로 직렬화/역직렬화할 수 있도록 합니다.
- **오프라인 영속화**: 생성된 리포트 JSON 및 타임스탬프를 세션 테이블(`swing_sessions`)에 저장하여 오프라인에서도 즉시 재조회할 수 있도록 합니다.

---

## 2. 요구사항 및 도메인 계약

### 2.1 `:core:model` 도메인 모델 정의

새 파일 `core/model/src/main/java/io/github/loje0611/tennisdoc/core/model/AiCoachReport.kt`:
```kotlin
package io.github.loje0611.tennisdoc.core.model

/**
 * AI 코치가 분석한 세션 종합 정밀 처방 리포트 도메인 모델.
 */
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

/**
 * 센서-비전 융합 기반 핵심 결함 원인 분석.
 */
data class CausalFlawDiagnosis(
    val flawTitle: String,
    val observedEffect: String, // e.g. "임팩트 시 라켓 페이스 +14° 열림"
    val rootCause: String,       // e.g. "골반 회전 대비 어깨 조기 회전으로 체인 가속 전달 누수"
    val coachingCue: String,     // e.g. "포워드 스윙 시 라켓 헤드를 뒤에 두고 하체 리드로 회전하세요."
)

/**
 * 다음 세션 추천 드릴 및 연습 목표.
 */
data class DrillRecommendation(
    val drillType: DrillType,
    val title: String,
    val focusPoint: String,
    val targetRepetitions: Int = 10,
)

/**
 * AI 코칭 스타일 / 톤 설정.
 */
enum class CoachTone {
    ENCOURAGING, // 격려 및 긍정형
    ANALYTICAL,  // 정밀 데이터 분석형
    STRICT,      // 엄격한 프로 코치형
}

/**
 * AI 코치 LLM 프로바이더.
 */
enum class LlmProvider {
    MOCK,
    GEMINI,
    OPENAI,
}
```

### 2.2 `:core:data` Room DB v9 스키마 변경

1. `SwingSessionEntity.kt` 필드 추가:
   - `val aiCoachReportJson: String? = null` (리포트 JSON 문자열)
   - `val aiReportGeneratedAt: Long? = null` (리포트 생성 타임스탬프)

2. `TennisDocDatabase.kt` 버전 업데이트 및 마이그레이션:
   - `@Database(..., version = 9, exportSchema = true)`
   - `MIGRATION_8_9`:
     ```kotlin
     val MIGRATION_8_9 = object : Migration(8, 9) {
         override fun migrate(db: SupportSQLiteDatabase) {
             db.execSQL("ALTER TABLE swing_sessions ADD COLUMN aiCoachReportJson TEXT DEFAULT NULL")
             db.execSQL("ALTER TABLE swing_sessions ADD COLUMN aiReportGeneratedAt INTEGER DEFAULT NULL")
         }
     }
     ```
   - `getInstance()`의 `addMigrations(...)`에 `MIGRATION_8_9` 등록.

3. `SwingSessionDao.kt` 메서드 추가:
   ```kotlin
   @Query("UPDATE swing_sessions SET aiCoachReportJson = :reportJson, aiReportGeneratedAt = :generatedAt WHERE sessionId = :sessionId")
   suspend fun updateAiCoachReport(sessionId: String, reportJson: String, generatedAt: Long)
   ```

4. `SwingHistoryRepository` 인터페이스 및 구현체(`SwingHistoryRepositoryImpl.kt`):
   ```kotlin
   suspend fun saveAiCoachReport(sessionId: String, reportJson: String, generatedAt: Long)
   ```

---

## 3. 대상 파일 및 모듈 경계

| 대상 모듈 | 파일 경로 | 변경 내용 |
|---|---|---|
| `:core:model` | `core/model/src/main/java/.../AiCoachReport.kt` | 신규 도메인 모델 (`AiCoachReport`, `CausalFlawDiagnosis`, `DrillRecommendation`, `CoachTone`, `LlmProvider`) |
| `:core:model` | `core/model/src/test/java/.../AiCoachReportTest.kt` | 도메인 모델 기본 생성 및 불변성 단위 테스트 |
| `:core:data` | `core/data/src/main/java/.../entity/SwingSessionEntity.kt` | `aiCoachReportJson`, `aiReportGeneratedAt` 컬럼 추가 |
| `:core:data` | `core/data/src/main/java/.../db/TennisDocDatabase.kt` | `version = 9`, `MIGRATION_8_9` 추가 및 빌더 등록 |
| `:core:data` | `core/data/src/main/java/.../dao/SwingSessionDao.kt` | `updateAiCoachReport` 쿼리 메서드 추가 |
| `:core:data` | `core/data/src/main/java/.../repository/SwingHistoryRepository.kt` | `saveAiCoachReport` 메서드 선언 |
| `:core:data` | `core/data/src/main/java/.../repository/SwingHistoryRepositoryImpl.kt` | `saveAiCoachReport` 구현 및 DAO 호출 |
| `:core:data` | `core/data/src/androidTest/java/.../Migration8To9Test.kt` | Room DB v8 ➔ v9 마이그레이션 계측 테스트 |
| `:core:data` | `core/data/src/test/java/.../repository/SwingHistoryRepositoryAiReportTest.kt` | 리포트 저장 및 세션 조회 연동 단위 테스트 (Fake DAO/Repo) |

---

## 4. 수용 기준 (Acceptance Criteria)

- **AC-1 (도메인 모델)**: `:core:model`에 `AiCoachReport`, `CausalFlawDiagnosis`, `DrillRecommendation`, `CoachTone`, `LlmProvider`가 올바르게 정의되어 단위 테스트로 인스턴스 생성이 검증되어야 한다.
- **AC-2 (DB v9 스키마 & 마이그레이션)**: `TennisDocDatabase` 버전이 9로 업데이트되고, `MIGRATION_8_9`를 통해 `swing_sessions` 테이블에 `aiCoachReportJson` 및 `aiReportGeneratedAt` 컬럼이 추가되어야 하며, 기존 데이터가 유실되지 않아야 한다.
- **AC-3 (DAO & Repository)**: `SwingSessionDao.updateAiCoachReport` 및 `SwingHistoryRepository.saveAiCoachReport`를 호출했을 때 해당 세션의 리포트 정보가 올바르게 갱신되어야 한다.
- **AC-4 (아키텍처 규칙)**: 순수 JVM 모듈 `:core:model`은 외부 라이브러리(Android 프레임워크) 의존이 없어야 하며, `verifyModuleDependencies` 검증을 통과해야 한다.
- **AC-5 (빌드 및 테스트 통과)**: 선언된 테스트 명령이 0 failure로 통과해야 한다.

---

## 5. 검증 명령 (Testing Instructions)

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

# 모듈 의존성 및 단위 테스트
./gradlew :core:model:test :core:data:test verifyModuleDependencies --rerun-tasks
```
