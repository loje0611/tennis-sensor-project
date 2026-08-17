# TASK-046 명세서: 로컬 룰 기반 Fallback 엔진 & LLM API 클라이언트 구현

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-17 | PM | 최초 작성 (Phase 4 B그룹: 오프라인 무중단 Fallback 룰 엔진, Gemini API 클라이언트 및 복합 코칭 서비스 구현) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
Phase 4의 핵심 가치 중 하나는 **"어떠한 네트워크 장애나 API Key 미설정 상황에서도 사용자의 훈련 흐름이 끊기지 않는 100% 무중단 코칭 경험"** 을 제공하는 것입니다.
본 태스크(`TASK-046`)는 `:core:coach` 모듈에서 원격 LLM 호출 실패 또는 오프라인 환경에 대응하는 **결정론적 로컬 룰 기반 Fallback 엔진(`LocalRuleBasedCoachEngine`)** 과 **Gemini REST API 클라이언트(`GeminiCoachClient`)**, 그리고 이들을 지능적으로 조율하여 자동 Fallback을 수행하는 **`CompositeAiCoachService`** 를 구현합니다.

### 1.2 범위
- `:core:coach` 내 `LocalRuleBasedCoachEngine` 구현 (세션 융합 지표 및 결함 태그 기반 오프라인 규칙형 `AiCoachReport` 생성, `isFallbackReport = true`).
- `:core:coach` 내 `GeminiCoachClient` 구현 (Google Gemini API REST 페이로드 조립, 타임아웃 처리, 프롬프트 빌더 및 파서 연동).
- `HttpTransport` 인터페이스 추상화 (순수 JVM 단위 테스트에서 Fake HTTP 응답 주입 가능).
- `CompositeAiCoachService` (또는 `AiCoachManager`) 구현: `LlmProvider` 설정에 따른 분기 및 네트워크/파싱 오류 시 로컬 Fallback 자동 전환.
- 단위 테스트를 통한 Fallback 룰 분기, Fake HTTP 성공/실패 시나리오, 무중단 리포트 생성 보장 검증.

---

## 2. Definitions & References (정의 및 참조)

- **D-7.5 LLM 리포트 설계 제약** (`docs/PRODUCT_DIRECTION.md`): 클라우드 API 호출 실패 또는 오프라인 환경에서도 로컬 결정론적 룰 기반 처방을 생성하여 100% 가용성을 보장함.
- **`LocalRuleBasedCoachEngine`**: 원격 통신 없이 세션 융합 지표(`SessionPrescriptionContext`)만을 기반으로 신뢰성 높은 한국어 처방 리포트를 즉시 생성하는 오프라인 룰 엔진.
- **`GeminiCoachClient`**: Google AI Gemini REST API(`https://generativelanguage.googleapis.com/...`)와 통신하여 리포트를 생성하는 클라이언트.
- **`CompositeAiCoachService`**: Provider 설정(Mock, Gemini, OpenAI) 및 네트워크 상태에 따라 최적의 클라이언트를 호출하고, 실패 시 `LocalRuleBasedCoachEngine`으로 투명하게 Fallback하는 종합 서비스.

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: `LocalRuleBasedCoachEngine` 구현
- `generateFallbackReport(context: SessionPrescriptionContext, tone: CoachTone): AiCoachReport`
- **총평 및 장점 요약**:
  - `averageEnergyEfficiency`, `squareFaceRatePercent`, `sequentialChainRatePercent` 수치를 기반으로 톤별 맞춤 총평 생성.
  - 정타율 70% 이상 또는 효율 80% 이상인 경우 해당 강점을 `keyStrengths`에 반영.
- **인과 결함 진단 (`primaryFlawDiagnosis`) 매핑**:
  - `primaryFlawTag`에 따라 구체적 진단 구성:
    - `EARLY_BODY_OPEN`: 골반 대비 상체 조기 회전으로 인한 타점 밀림 및 페이스 열림 ➔ 하체 리드 코칭 큐
    - `FACE_OPEN`: 라켓 페이스 열림 ➔ 임팩트 면 스퀘어 유지 코칭 큐
    - `FACE_CLOSED` / `LATE_CONTACT`: 타점 후방 형성 ➔ 전방 타점 확보 코칭 큐
    - `POWER_LEAK` / `CHAIN_TIMING_DELAY`: 운동 체인 분절 간 가속 전달 누수 ➔ 손목-라켓 릴리즈 일치 코칭 큐
    - `CLEAN_STRIKE`: 결함 없음, 이상적인 체인 타이밍 칭찬
    - 기타/결함 없음: 전반적인 밸런스 유지 가이드
- **추천 드릴 (`recommendedDrills`)**:
  - `drillType`과 결함 태그에 맞는 맞춤 드릴 2~3종 및 목표 횟수(10~20회) 자동 선정.
- **플래그 설정**: `isFallbackReport = true`, `rawModelName = "local-rule-engine"`.

### FR-2: `HttpTransport` 인터페이스 및 `GeminiCoachClient` 구현
- `interface HttpTransport { suspend fun postJson(url: String, headers: Map<String, String>, bodyJson: String, timeoutMs: Long): HttpResponse }`
- `data class HttpResponse(val statusCode: Int, val body: String)`
- `DefaultHttpTransport`: JDK 표준 `java.net.HttpURLConnection` 또는 `java.net.http.HttpClient`를 활용한 순수 JVM HTTP 전송 구현체 (타임아웃 기본 10초).
- `GeminiCoachClient(apiKey: String, modelName: String = "gemini-1.5-flash", transport: HttpTransport = DefaultHttpTransport())`:
  - `generateReport(context: SessionPrescriptionContext, tone: CoachTone): Result<AiCoachReport>`
  - `CoachPromptBuilder`로 프롬프트 조립 ➔ Gemini `generateContent` REST JSON 페이로드 생성 ➔ API 호출 ➔ `StructuredReportParser`로 역직렬화.
  - HTTP 상태 코드가 200이 아니거나 타임아웃 발생 시 `Result.failure` 반환.

### FR-3: `CompositeAiCoachService` 자동 Fallback 조율기 구현
- `class CompositeAiCoachService(geminiClientFactory: (apiKey: String) -> LlmCoachClient = { GeminiCoachClient(it) }, mockClient: LlmCoachClient = MockLlmCoachClient(), fallbackEngine: LocalRuleBasedCoachEngine = LocalRuleBasedCoachEngine())`
- `suspend fun createReport(context: SessionPrescriptionContext, provider: LlmProvider, apiKey: String?, tone: CoachTone): AiCoachReport`
- **조율 로직**:
  1. `provider == LlmProvider.MOCK` ➔ `mockClient.generateReport(context, tone)` 호출.
  2. `provider == LlmProvider.GEMINI`:
     - `apiKey`가 `null`이거나 공백(`isBlank()`)인 경우 ➔ 원격 호출을 건너뛰고 즉시 `fallbackEngine.generateFallbackReport(context, tone)` 반환.
     - `apiKey`가 유효한 경우 ➔ `geminiClient.generateReport(...)` 호출.
     - 호출 결과가 `Result.success`이면 해당 리포트 반환 (`isFallbackReport = false`).
     - 호출 결과가 `Result.failure`(네트워크 단절, 타임아웃, 파싱 오류 등)인 경우 ➔ 예외를 삼키고 즉시 `fallbackEngine.generateFallbackReport(context, tone)` 반환 (`isFallbackReport = true`).
  3. 모든 경로에서 항상 유효한 `AiCoachReport` 인스턴스를 반환하여 UI 계층에 절대 `null`이나 크래시가 전파되지 않도록 보장.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

```kotlin
package io.github.loje0611.tennisdoc.core.coach.engine

import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CoachTone

class LocalRuleBasedCoachEngine {
    fun generateFallbackReport(
        context: SessionPrescriptionContext,
        tone: CoachTone = CoachTone.ENCOURAGING
    ): AiCoachReport
}
```

```kotlin
package io.github.loje0611.tennisdoc.core.coach.network

interface HttpTransport {
    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
        timeoutMs: Long = 10_000L
    ): HttpResponse
}

data class HttpResponse(
    val statusCode: Int,
    val body: String
)
```

```kotlin
package io.github.loje0611.tennisdoc.core.coach.client

class GeminiCoachClient(
    private val apiKey: String,
    private val modelName: String = "gemini-1.5-flash",
    private val transport: HttpTransport = DefaultHttpTransport()
) : LlmCoachClient
```

```kotlin
package io.github.loje0611.tennisdoc.core.coach.service

import io.github.loje0611.tennisdoc.core.fusion.context.SessionPrescriptionContext
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.LlmProvider

class CompositeAiCoachService(
    private val geminiClientFactory: (apiKey: String) -> LlmCoachClient = { GeminiCoachClient(it) },
    private val mockClient: LlmCoachClient = MockLlmCoachClient(),
    private val fallbackEngine: LocalRuleBasedCoachEngine = LocalRuleBasedCoachEngine()
) {
    suspend fun createReport(
        context: SessionPrescriptionContext,
        provider: LlmProvider = LlmProvider.GEMINI,
        apiKey: String? = null,
        tone: CoachTone = CoachTone.ENCOURAGING
    ): AiCoachReport
}
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- N/A (네트워크 클라이언트 및 Fallback 룰 엔진 백엔드 모듈 태스크)

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **순수 JVM 실행 가능성**: `:core:coach` 모듈은 Android SDK(`android.*`)에 의존하지 않고 JDK 표준 라이브러리 기반으로 완전하게 빌드 및 테스트되어야 한다.
- **무중단 신뢰성(High Availability)**: 네트워크 단절(비행기 모드), 타임아웃, 서버 500 에러, 잘못된 JSON 응답 등 어떤 실패 상황에서도 `CompositeAiCoachService.createReport`는 유효한 `AiCoachReport`를 1초 이내에 반환해야 한다.
- **테스트 용이성(Testability)**: `HttpTransport`를 Mocking/Faking하여 네트워크 I/O 없이도 모든 성공/실패 경로를 100% 단위 테스트로 검증할 수 있어야 한다.

---

## 7. Error Handling & Edge Cases (오류 처리 및 예외 상황)

- **빈 API Key 전달 (`apiKey = null` 또는 `""`)**: 원격 네트워크 호출을 시도하지 않고 즉시 `LocalRuleBasedCoachEngine`으로 안전하게 전환한다.
- **네트워크 타임아웃 (TimeoutException)**: `GeminiCoachClient`가 타임아웃을 감지하여 `Result.failure`를 반환하고, `CompositeAiCoachService`가 이를 Fallback 리포트로 변환한다.
- **HTTP 401 Unauthorized (잘못된 API Key)**: 오류 로그/메시지를 안전하게 캡슐화하고 Fallback 리포트를 반환한다.
- **LLM이 엉뚱한 비-JSON 텍스트 응답**: 파서가 실패를 반환하며, 서비스가 Fallback 리포트로 대체한다.

---

## 8. Acceptance Criteria (수용 기준)

- **AC-1 (로컬 룰 기반 Fallback 생성)**: `LocalRuleBasedCoachEngine.generateFallbackReport` 호출 시 `isFallbackReport == true` 및 `rawModelName == "local-rule-engine"` 플래그가 설정되고, 컨텍스트의 `primaryFlawTag`와 효율 수치가 반영된 정밀한 한국어 리포트가 생성되어야 한다.
- **AC-2 (Fake HTTP 기반 Gemini 성공 파싱)**: `GeminiCoachClient`에 유효한 JSON을 반환하는 `FakeHttpTransport`를 주입했을 때, 정상적으로 `AiCoachReport`(`isFallbackReport == false`)를 반환해야 한다.
- **AC-3 (네트워크 에러 시 GeminiClient 실패 반환)**: HTTP 500 또는 네트워크 예외 발생 시 `GeminiCoachClient`가 크래시 없이 `Result.failure`를 반환해야 한다.
- **AC-4 (API Key 부재 시 무중단 Fallback)**: `CompositeAiCoachService.createReport`에 `apiKey = null`을 전달했을 때 네트워크 호출 없이 즉시 Fallback 리포트(`isFallbackReport == true`)를 반환해야 한다.
- **AC-5 (원격 호출 실패 시 자동 Fallback 전환)**: `CompositeAiCoachService`에서 Gemini 호출이 실패하더라도 최종적으로 유효한 Fallback `AiCoachReport`가 반환되어야 한다.
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
