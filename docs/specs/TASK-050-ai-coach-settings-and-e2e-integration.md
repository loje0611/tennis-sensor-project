# TASK-050 명세서: AI 코치 환경설정 UX 구현 및 전 구간 통합 E2E 검증

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-17 | PM | 최초 작성 (Phase 4 D그룹: DataStore 기반 AI 코치 환경설정 UX, Gemini API Key/톤 설정, 전 구간 E2E 통합 및 Phase 4 완결 검증) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
Phase 4(`TASK-043`~`TASK-049`)를 통해 AI 코치 도메인 모델, 융합 지표 Context Builder, Gemini/Fallback 복합 서비스, 스포츠 카드 UI, Lab 및 History 화면 연동이 모두 완성되었습니다.
본 태스크(`TASK-050`)는 Phase 4의 최종 완결 태스크로서, **사용자가 본인의 Gemini API Key와 선호하는 코칭 톤(격려/분석/엄격) 및 AI 프로바이더를 직접 설정하고 테스트할 수 있는 `AiCoachPreferencesRepository` 및 설정 화면(`SettingsScreen`) UX**를 구현하고, **세션 측정 ➔ 융합 분석 ➔ Gemini AI 처방 ➔ Room DB v9 캐싱 ➔ UI 렌더링에 이르는 전 구간 E2E 통합 검증**을 완료합니다.

### 1.2 범위
- `:core:data`에 `AiCoachPreferencesRepository` 구현 (DataStore 기반 Gemini API Key, LLM Provider, 기본 코칭 톤 영속화 및 Flow 제공).
- `:app`의 `SettingsViewModel` 및 `SettingsScreen`에 **[🤖 AI 코치 설정]** 섹션 추가:
  - Gemini API Key 입력/수정/마스킹 토글 UI 및 API Key 연결 테스트(Test Connection) 버튼.
  - 기본 코칭 톤(`CoachTone`: 🌱 격려형 / 📊 분석형 / 🎯 엄격형) 선택 UI.
  - AI 프로바이더(`LlmProvider`: Google Gemini / Mock / Local Rule Only) 선택 UI.
- `LabViewModel` 및 `SessionDetailViewModel`이 `AiCoachPreferencesRepository`의 사용자 설정값(API Key, 선호 톤, Provider)을 기본값으로 사용하도록 연결.
- Phase 4 전 구간 통합 단위/UI 테스트(`AiCoachSettingsUiTest`, `Phase4EndToEndIntegrationTest`) 및 모듈 의존성 검증.

---

## 2. Definitions & References (정의 및 참조)

- **`AiCoachPreferencesRepository`** (`:core:data`): Jetpack DataStore(Preferences)를 통해 사용자의 Gemini API Key와 코칭 선호 설정을 암호화/영속화하는 저장소.
- **D-7.5 무중단 LLM 가드레일**: API Key가 설정되지 않았거나 잘못된 키가 입력되었더라도 앱이 크래시되지 않고 로컬 Fallback 엔진으로 안전하게 전환됨.
- **Phase 4 E2E 파이프라인**: Lab/History 세션 데이터 ➔ `SessionPrescriptionContextBuilder` ➔ `CompositeAiCoachService` ➔ Room DB v9 `aiCoachReportJson` ➔ `AiCoachReportCard` 렌더링.

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: `AiCoachPreferencesRepository` 구현 (`:core:data`)
- `context.preferencesDataStore(name = "ai_coach_settings")` 사용.
- **데이터 스트림**:
  - `val geminiApiKey: Flow<String?>` (저장된 API Key 또는 null)
  - `val llmProvider: Flow<LlmProvider>` (기본값: `LlmProvider.GEMINI`)
  - `val defaultCoachTone: Flow<CoachTone>` (기본값: `CoachTone.ENCOURAGING`)
- **수정 함수**:
  - `suspend fun setGeminiApiKey(apiKey: String?)` (공백/빈 문자열은 null로 정리)
  - `suspend fun setLlmProvider(provider: LlmProvider)`
  - `suspend fun setDefaultCoachTone(tone: CoachTone)`
- `CoreDataModule`에 Hilt `@Provides` 싱글톤 등록.

### FR-2: `SettingsViewModel` AI 코치 상태 및 액션 연동
- `SettingsViewModel`에 `AiCoachPreferencesRepository` 주입:
  - `val geminiApiKey: StateFlow<String?>`
  - `val llmProvider: StateFlow<LlmProvider>`
  - `val defaultCoachTone: StateFlow<CoachTone>`
  - `val apiKeyTestState: StateFlow<ApiKeyTestStatus>` (`IDLE`, `TESTING`, `SUCCESS`, `ERROR(message)`)
- **액션**:
  - `fun saveGeminiApiKey(apiKey: String)`
  - `fun saveLlmProvider(provider: LlmProvider)`
  - `fun saveDefaultCoachTone(tone: CoachTone)`
  - `fun testGeminiApiKey(apiKey: String)`: `GeminiCoachClient` 또는 Fake 핑 호출을 통해 API Key 유효성을 검증하고 결과 상태 발행.

### FR-3: `SettingsScreen` [🤖 AI 코치 설정] UI 구현
- **카드 컨테이너**: Clean Sunlit Court 스타일의 둥근 모서리(`16.dp`) 카드.
- **프로바이더 선택**:
  - `Google Gemini Flash (권장)` / `오프라인 룰 엔진 (로컬 전용)` / `가상 Mock 코치` 칩 또는 드롭다운.
- **Gemini API Key 입력 필드**:
  - 비밀번호 마스킹 및 표시/숨김 토글 아이콘.
  - 우측에 **[연결 테스트]** 버튼 ➔ 로딩 스피너 및 성공(초록 체크 "연결 성공") / 실패(빨간 경고 "인증 실패") 피드백.
  - 하단 도움말: "Google AI Studio에서 무료로 발급받은 API Key를 입력하세요."
- **기본 코칭 톤 선택기**:
  - `CoachToneSelector` 컴포넌트 임베드 (🌱 격려형, 📊 분석형, 🎯 엄격형).

### FR-4: 전 구간 E2E 연동 및 무중단 Fallback 보장
- `LabViewModel`과 `SessionDetailViewModel`에서 리포트 요청 시, `AiCoachPreferencesRepository`의 API Key와 기본 톤을 자동 반영.
- API Key가 비어있을 때 [AI 코치 처방받기] 요청 시 즉시 `LocalRuleBasedCoachEngine`이 작동하여 `⚡ 로컬 룰 엔진 분석` 배지가 달린 정상 리포트가 화면에 렌더링되어야 함.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

```kotlin
package io.github.loje0611.tennisdoc.core.data.repository

import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.LlmProvider
import kotlinx.coroutines.flow.Flow

class AiCoachPreferencesRepository(context: Context) {
    val geminiApiKey: Flow<String?>
    val llmProvider: Flow<LlmProvider>
    val defaultCoachTone: Flow<CoachTone>

    suspend fun setGeminiApiKey(apiKey: String?)
    suspend fun setLlmProvider(provider: LlmProvider)
    suspend fun setDefaultCoachTone(tone: CoachTone)
}
```

```kotlin
package io.github.loje0611.tennisdoc.ui.settings

sealed interface ApiKeyTestStatus {
    object Idle : ApiKeyTestStatus
    object Testing : ApiKeyTestStatus
    object Success : ApiKeyTestStatus
    data class Error(val message: String) : ApiKeyTestStatus
}
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- **Clean Sunlit Court 테마**:
  - 설정 카드 배경: `#FFFFFF`, 테두리 `#E2E8F0` (1dp).
  - API Key 입력창: 라운드 `12.dp`, 포커스 시 Royal Blue (`#2563EB`) 아웃라인.
  - 연결 테스트 성공 배지: 소프트 그린 (`#DCFCE7`), 텍스트 `#16A34A`.
  - 연결 테스트 실패 텍스트: 소프트 레드 (`#FEE2E2`), 텍스트 `#DC2626`.
- **보안 및 프라이버시**:
  - API Key 입력값은 화면에 평문으로 노출되지 않도록 기본적으로 마스킹(`••••••••`) 처리.

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **안전한 데이터 보관**: DataStore를 통해 기기 내부 프라이빗 스토리지에 격리 저장되며 외부 앱에 노출되지 않음.
- **모듈 의존성 단방향 준수**: `verifyModuleDependencies` 0 violations 유지.

---

## 7. Error Handling & Edge Cases (오류 처리 및 예외 상황)

- **유효하지 않은 API Key로 테스트 시**: 크래시 없이 `ApiKeyTestStatus.Error`가 발행되어 에러 메시지가 사용자에게 노출됨.
- **네트워크 오프라인 상태에서 테스트 시**: "네트워크 연결을 확인하세요" 안내 제공.
- **API Key 삭제(초기화)**: 빈 문자열 저장 시 안전하게 키가 제거되고 기본 로컬 Fallback 모드로 전환.

---

## 8. Acceptance Criteria (수용 기준)

- **AC-1 (설정 영속화 및 Flow 스트림)**: `AiCoachPreferencesRepository`에서 API Key, Provider, CoachTone을 저장하고 다시 읽어왔을 때 동일한 값이 반환되어야 한다.
- **AC-2 (설정 화면 AI 코치 섹션 렌더링)**: `SettingsScreen`에 AI 코치 프로바이더, API Key 입력창, 연결 테스트 버튼, 톤 셀렉터가 정상 렌더링되어야 한다.
- **AC-3 (API Key 연결 테스트 상태 전이)**: `SettingsViewModel.testGeminiApiKey` 호출 시 `Testing` ➔ `Success` 또는 `Error` 상태로 정상 전이되어야 한다.
- **AC-4 (기본 톤 및 키 변경 반영)**: 설정 화면에서 톤을 변경하면 `AiCoachPreferencesRepository`에 즉시 반영되어야 한다.
- **AC-5 (전 구간 통합 E2E 검증)**: 세션 데이터 집계 ➔ AI 코치 리포트 생성 ➔ DB 영속화 ➔ History/Lab UI 표시 전 과정이 통합 테스트로 검증되어야 한다.
- **AC-6 (빌드 및 테스트 100% 통과)**: 전체 프로젝트의 단위 테스트 및 `verifyModuleDependencies`가 0 failure로 통과해야 한다.

---

## 9. Testing Instructions (테스트 명령)

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

# 전체 단위 테스트 및 모듈 의존성 검증
./gradlew test verifyModuleDependencies --rerun-tasks
```
