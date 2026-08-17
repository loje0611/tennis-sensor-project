# TASK-045 명세서: :core:coach 모듈 신설 및 프롬프트 템플릿 / 구조화 파서 구현

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-17 | PM | 최초 작성 (Phase 4 B그룹: :core:coach 순수 JVM 모듈 신설, 프롬프트 엔지니어링 및 JSON 구조화 파서 구현) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
`TASK-044`에서 구현된 `SessionPrescriptionContextBuilder`를 통해 세션의 정량 지표와 결함 분석 데이터가 프라이버시 보호형 JSON으로 집계됩니다.
본 태스크(`TASK-045`)는 이 집계 데이터를 LLM(Gemini / OpenAI 등)에 주입하여 구조화된 AI 코칭 리포트를 생성할 수 있도록 **순수 Kotlin JVM 모듈 `:core:coach`를 신설**하고, **시스템 프롬프트 템플릿 조립기(`CoachPromptBuilder`)** 및 **LLM 응답 JSON 역직렬화 파서(`StructuredReportParser`)**, 그리고 테스트를 위한 **`MockLlmCoachClient`** 를 구현합니다.

### 1.2 범위
- `TennisDocAI`에 순수 Kotlin JVM 모듈 `:core:coach` 신설 (`settings.gradle.kts` 등록 및 `tennisdoc.jvm.library` 컨벤션 적용).
- `verifyModuleDependencies` 허용 규칙에 `:core:coach -> {:core:model, :core:fusion}` 추가.
- `CoachPromptBuilder`: 테니스 전문 코치 System Instruction, 톤(`CoachTone`) 반영, `SessionPrescriptionContext` 주입, JSON 출력 스키마 가이드라인 프롬프트 생성.
- `StructuredReportParser`: 마크다운 코드블록(```json ... ```) 포함 텍스트를 `AiCoachReport` 도메인 객체로 안전하게 파싱 및 필드 유효성 검증.
- `MockLlmCoachClient`: 네트워크 없이 즉시 테스트 및 로컬 시뮬레이션이 가능한 Fake LLM 클라이언트.
- 단위 테스트를 통한 프롬프트 구성, JSON 파싱 성공/실패 복구, Mock 클라이언트 동작 검증.

---

## 2. Definitions & References (정의 및 참조)

- **D-7.5 LLM 리포트 설계 제약** (`docs/PRODUCT_DIRECTION.md`): 수치는 LLM이 임의로 생성하지 않고 Context JSON에 명시된 수치를 Ground Truth로 사용하며, 표현·인과설명·처방·톤 조절만을 담당함.
- **`:core:coach`**: Android 프레임워크 의존 없이 프롬프트 빌드, LLM JSON 파싱, 코칭 룰을 처리하는 순수 Kotlin JVM 모듈.
- **`CoachPromptBuilder`**: `SessionPrescriptionContext`와 `CoachTone`을 바탕으로 LLM용 프롬프트 문자열을 생성하는 빌더.
- **`StructuredReportParser`**: LLM의 응답 텍스트에서 JSON 객체를 추출하여 `AiCoachReport`로 변환하는 파서.

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: `:core:coach` 모듈 스캐폴딩 및 의존성 규칙 갱신
- `TennisDocAI/settings.gradle.kts`에 `include(":core:coach")`를 추가한다.
- `core/coach/build.gradle.kts`를 생성하고 `tennisdoc.jvm.library` 플러그인, `:core:model`, `:core:fusion` 의존성을 구성한다.
- `TennisDocAI/build.gradle.kts`의 `verifyModuleDependencies` 태스크에 `":core:coach" to setOf(":core:model", ":core:fusion")`를 추가한다.

### FR-2: `CoachPromptBuilder` 구현
- `buildPrompt(context: SessionPrescriptionContext, tone: CoachTone = CoachTone.ENCOURAGING): String`
- 프롬프트 구성 요소:
  1. **역할 정의(System Instruction)**: 프로 테니스 생체역학 전문 코치 페르소나.
  2. **가드레일 지침**: 수치(각도, 효율 %, 딜레이 ms 등)를 임의로 조작하거나 환각하지 말고 컨텍스트에 주어진 값을 그대로 인용할 것.
  3. **코칭 톤 지시**:
     - `ENCOURAGING`: 칭찬과 긍정적인 피드백 중심, 격려하는 어조.
     - `ANALYTICAL`: 데이터 수치와 운동 체인 역학 위주의 객관적 분석 어조.
     - `STRICT`: 원인과 결함을 명확히 짚고 엄격한 반복 훈련을 요구하는 프로 코치 어조.
  4. **주입 데이터**: `context.toJsonString()`을 [SESSION_DATA] 블록에 포함.
  5. **출력 형식 가이드**: `AiCoachReport`의 필드 구조(`overallSummary`, `keyStrengths`, `primaryFlawDiagnosis`, `actionItems`, `recommendedDrills`)에 부합하는 순수 JSON 형식 강제.

### FR-3: `StructuredReportParser` 구현
- `parseReport(rawResponse: String, sessionId: String, fallbackModelName: String = "gemini-1.5-pro"): Result<AiCoachReport>`
- 마크다운 코드블록(` ```json ... ``` ` 또는 ` ``` ... ``` `)으로 감싸진 응답에서도 순수 JSON을 안전하게 추출한다.
- 필수 필드(`overallSummary`)가 누락되었거나 JSON 구문 오류 시 안전하게 `Result.failure`를 반환한다.
- `CausalFlawDiagnosis` 및 `DrillRecommendation` 객체를 매핑하며, `reportId`는 고유 UUID로 자동 생성한다.
- `isFallbackReport`는 `false`로 설정한다.

### FR-4: `MockLlmCoachClient` 및 LLM 클라이언트 인터페이스 정의
- `interface LlmCoachClient { suspend fun generateReport(context: SessionPrescriptionContext, tone: CoachTone): Result<AiCoachReport> }`
- `MockLlmCoachClient` 구현:
  - 네트워크 통신 없이 `context`의 `primaryFlawTag`, `averageEnergyEfficiency`, `squareFaceRatePercent`를 활용하여 유의미한 결정론적 목업 `AiCoachReport`를 즉시 반환.
  - 테스트 및 오프라인 UI 개발에서 100% 예측 가능한 결과 제공.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

```kotlin
package io.github.loje0611.tennisdoc.core.coach.prompt

import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.model.CoachTone

class CoachPromptBuilder {
    fun buildPrompt(
        context: SessionPrescriptionContext,
        tone: CoachTone = CoachTone.ENCOURAGING
    ): String
}
```

```kotlin
package io.github.loje0611.tennisdoc.core.coach.parser

import io.github.loje0611.tennisdoc.core.model.AiCoachReport

class StructuredReportParser {
    fun parseReport(
        rawResponse: String,
        sessionId: String,
        rawModelName: String? = null
    ): Result<AiCoachReport>
}
```

```kotlin
package io.github.loje0611.tennisdoc.core.coach.client

import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CoachTone

interface LlmCoachClient {
    suspend fun generateReport(
        context: SessionPrescriptionContext,
        tone: CoachTone = CoachTone.ENCOURAGING
    ): Result<AiCoachReport>
}

class MockLlmCoachClient : LlmCoachClient {
    override suspend fun generateReport(
        context: SessionPrescriptionContext,
        tone: CoachTone
    ): Result<AiCoachReport>
}
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- N/A (프롬프트 빌더 및 JSON 파서 백엔드 모듈 태스크)

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **순수 JVM 독립성**: `:core:coach` 모듈은 Android SDK 의존 없이 JVM에서 100% 실행 및 테스트되어야 한다.
- **파서 견고성(Robustness)**: 앞뒤 설명 텍스트, 코드블록 기호(` ``` `), 개행 문자 등이 혼합된 LLM 응답에서도 유효한 JSON을 안정적으로 추출해야 한다.
- **모듈 의존성 단방향 보장**: `:core:coach`는 `:core:model`과 `:core:fusion`만을 참조하며, 역참조나 UI 의존성을 갖지 않는다.

---

## 7. Error Handling & Edge Cases (오류 처리 및 예외 상황)

- **완전히 깨진 응답 (Non-JSON)**: `StructuredReportParser`가 크래시 없이 `Result.failure`를 반환한다.
- **부분적 필드 누락**: `keyStrengths`, `actionItems`, `recommendedDrills`가 JSON에 없더라도 `emptyList()`로 안전하게 기본값 처리한다.
- **빈 세션 컨텍스트 주입**: `CoachPromptBuilder`가 스윙 수 0인 세션 컨텍스트에 대해서도 올바른 프롬프트 문자열을 생성한다.

---

## 8. Acceptance Criteria (수용 기준)

- **AC-1 (모듈 스캐폴딩 및 의존성 규칙)**: `:core:coach`가 Gradle 멀티모듈 프로젝트에 등록되고, `verifyModuleDependencies` 검증을 통과해야 한다.
- **AC-2 (프롬프트 구성 및 톤 반영)**: `CoachPromptBuilder.buildPrompt` 호출 시 `context.toJsonString()` 내용이 포함되고, `CoachTone`별 지침(격려/분석/엄격)이 프롬프트에 명확히 반영되어야 한다.
- **AC-3 (마크다운 코드블록 JSON 파싱)**: `StructuredReportParser`가 순수 JSON뿐만 아니라 ` ```json ... ``` ` 마크다운으로 감싸진 응답 텍스트를 `AiCoachReport`로 정확히 역직렬화해야 한다.
- **AC-4 (파서 오류 복원력)**: 빈 문자열, 비-JSON 텍스트에 대해 예외로 크래시되지 않고 `Result.failure`를 반환해야 한다.
- **AC-5 (Mock 클라이언트 결정론적 동작)**: `MockLlmCoachClient`가 주어진 컨텍스트(드릴, 결함 태그)에 맞는 유효한 `AiCoachReport` 인스턴스를 성공적으로 반환해야 한다.
- **AC-6 (빌드 및 테스트 통과)**: 선언된 테스트 명령이 0 failure로 통과해야 한다.

---

## 9. Testing Instructions (테스트 명령)

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

# 모듈 의존성 및 단위 테스트
./gradlew :core:coach:test verifyModuleDependencies --rerun-tasks
```
