# TASK-049 명세서: :feature:history 세션 상세 화면 AI 처방 리포트 탭 통합

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-17 | PM | 최초 작성 (Phase 4 C그룹: 세션 상세 화면에 AI 코치 처방 탭, 기 저장된 리포트 캐시 파싱 렌더링 및 미생성 세션 즉시 생성 파이프라인 통합) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
사용자는 과거 훈련/경기 이력 목록(`HistoryScreen`)에서 특정 세션을 선택하여 상세 분석 화면(`SessionDetailScreen`)으로 진입합니다.
본 태스크(`TASK-049`)는 사용자가 언제든 과거 세션에 대해 **기 저장된 AI 코칭 리포트를 즉시 재열람**하거나, **아직 리포트가 생성되지 않은 과거 세션에 대해 원하는 코칭 톤(격려/분석/엄격)으로 새롭게 AI 코치 처방을 요청**할 수 있도록 `SessionDetailScreen`에 **[🤖 AI 코치 처방] 탭 및 뷰어 파이프라인**을 구축합니다.

### 1.2 범위
- `TennisDocAI/build.gradle.kts`의 `verifyModuleDependencies`에 `:feature:history -> :core:coach` 허용 의존성 추가.
- `feature/history/build.gradle.kts`에 `implementation(project(":core:coach"))` 추가.
- `SessionDetailUiState`에 `selectedTab: SessionDetailTab`, `aiCoachReport: AiCoachReport?`, `isGeneratingAiReport: Boolean` 필드 추가.
- `SessionDetailViewModel`:
  - DB 세션 조회 시 `aiCoachReportJson`이 존재하면 `StructuredReportParser`를 통해 `AiCoachReport`로 역직렬화하여 캐시 로딩.
  - `requestAiCoachReport(tone: CoachTone)` 구현 (세션 `LabRawRecordEntity` 파싱 ➔ `SessionPrescriptionContextBuilder` ➔ `CompositeAiCoachService` ➔ `SwingHistoryRepository.saveAiCoachReport` ➔ UI 갱신).
- `SessionDetailScreen`:
  - 상단 탭 네비게이션(TabRow) 구성: [📊 스윙 분석 / 🎬 동기 리플레이 / 🤖 AI 코치 처방].
  - [🤖 AI 코치 처방] 탭 렌더링:
    - 리포트 존재 시: `AiCoachReportCard(report = aiCoachReport)` 및 [🔄 처방 다시 생성하기] 버튼.
    - 미생성 시: 안내 카드 + `CoachToneSelector` + **[🤖 AI 코치 처방 생성하기]** 액션 버튼.
    - 생성 중 시: `AiCoachLoadingSkeleton` 펄스 애니메이션 렌더링.
- 단위 및 Compose UI 렌더링 테스트 구현.

---

## 2. Definitions & References (정의 및 참조)

- **`SessionDetailScreen`**: 특정 세션의 육각형 레이더 차트, 스윙별 메트릭, Lab 세션 분석 요약을 제공하는 상세 화면.
- **`SessionDetailTab`**: 세션 상세 화면의 3가지 뷰 모드 (`ANALYSIS`, `REPLAY`, `AI_COACH`).
- **`StructuredReportParser`** (`:core:coach`): DB에 저장된 JSON 문자열을 `AiCoachReport` 도메인 객체로 안전하게 변환하는 파서.

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: 모듈 의존성 및 빌드 설정 갱신
- `TennisDocAI/build.gradle.kts`의 `verifyModuleDependencies`에서 `:feature:history`의 허용 목록에 `":core:coach"`를 추가한다.
- `feature/history/build.gradle.kts`의 `dependencies`에 `implementation(project(":core:coach"))`를 추가한다.

### FR-2: `SessionDetailUiState` 및 탭 모델 확장
- `enum class SessionDetailTab { ANALYSIS, REPLAY, AI_COACH }` 정의.
- `SessionDetailUiState`에 다음 필드를 추가한다:
  - `val selectedTab: SessionDetailTab = SessionDetailTab.ANALYSIS`
  - `val aiCoachReport: AiCoachReport? = null`
  - `val isGeneratingAiReport: Boolean = false`
  - `val selectedTone: CoachTone = CoachTone.ENCOURAGING`

### FR-3: `SessionDetailViewModel` 리포트 로딩 및 생성 파이프라인 구현
- `StructuredReportParser` 및 `CompositeAiCoachService` (또는 Hilt 주입 인스턴스) 연동.
- **세션 로딩 시**:
  - `session.aiCoachReportJson`이 null이 아니고 비어있지 않으면 `parser.parseReport(session.aiCoachReportJson, session.sessionId)` 실행 ➔ 성공 시 `aiCoachReport`에 즉시 할당.
- **처방 생성 요청 (`requestAiCoachReport(tone: CoachTone)`)**:
  1. `_uiState.update { it.copy(isGeneratingAiReport = true) }`.
  2. 세션의 `labRawRecords`가 있는 경우 `LabRawRecordParser`를 통해 `FusedSwing` 목록 복원 (또는 센서 이벤트 기반 컨텍스트 생성).
  3. `SessionPrescriptionContextBuilder.buildContext(...)` 호출.
  4. `CompositeAiCoachService.createReport(context, tone = tone)` 비동기 호출.
  5. 생성된 `AiCoachReport`를 `repository.saveAiCoachReport(sessionId, reportJson, generatedAt)`으로 DB에 저장.
  6. `_uiState.update { it.copy(aiCoachReport = report, isGeneratingAiReport = false) }`.
- **탭 전환**: `fun selectTab(tab: SessionDetailTab)` 구현.
- **톤 선택**: `fun selectTone(tone: CoachTone)` 구현.

### FR-4: `SessionDetailScreen` 탭 UI 및 AI 코치 뷰어 구현
- **상단 탭 바 (TabRow / Segmented Control)**:
  - `📊 스윙 분석`, `🎬 동기 리플레이`(Lab 세션 전용), `🤖 AI 코치 처방` 3개 탭 렌더링.
- **`🤖 AI 코치 처방` 탭 콘텐츠**:
  1. **리포트 존재 시 (`aiCoachReport != null`)**:
     - `AiCoachReportCard(report = aiCoachReport)`를 최상단에 렌더링.
     - 하단에 `CoachToneSelector` 및 [🔄 다른 톤으로 다시 분석하기] 버튼 제공.
  2. **리포트 미생성 상태 (`aiCoachReport == null && !isGeneratingAiReport`)**:
     - Empty State 안내 카드: "아직 생성된 AI 코치 처방 리포트가 없습니다."
     - 코칭 톤 선택기 `CoachToneSelector` (기본값: 격려형).
     - **[🤖 AI 코치 처방 생성하기]** 원터치 액션 버튼 (Royal Blue).
  3. **생성 중 상태 (`isGeneratingAiReport == true`)**:
     - `AiCoachLoadingSkeleton()` 컴포넌트 렌더링.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

```kotlin
package io.github.loje0611.tennisdoc.feature.history

enum class SessionDetailTab {
    ANALYSIS,
    REPLAY,
    AI_COACH
}
```

```kotlin
// SessionDetailViewModel.kt
fun selectTab(tab: SessionDetailTab)
fun selectTone(tone: CoachTone)
fun requestAiCoachReport(tone: CoachTone = CoachTone.ENCOURAGING)
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- **Clean Sunlit Court 테마**:
  - 상단 탭 바: 활성 탭은 밑줄 인디케이터(Royal Blue `#2563EB`) 및 볼드 텍스트, 비활성 탭은 Slate Gray (`#64748B`).
  - Empty State 카드: 배경 `#F8FAFC`, 테두리 `#E2E8F0`, 둥근 모서리 `16.dp`.
  - [🤖 AI 코치 처방 생성하기] 버튼: 채움 버튼 (`#2563EB`), 화이트 볼드 텍스트.
  - [🔄 다른 톤으로 다시 분석하기] 버튼: 아웃라인 스타일 버튼 (`0x330066FF`).
- **스크롤 동작**:
  - AI 리포트 탭 내부는 `verticalScroll`이 부드럽게 적용되어 긴 총평 및 추천 드릴 카드가 잘리지 않고 스크롤됨.

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **오프라인 캐시 우선**: 네트워크 없이도 이전에 생성된 리포트가 있는 세션은 즉시 0ms 렌더링되어야 한다.
- **모듈 의존성 규칙 준수**: `verifyModuleDependencies` 검사에서 `:feature:history -> :core:coach`가 정상 통과해야 한다.

---

## 7. Error Handling & Edge Cases (오류 처리 및 예외 상황)

- **손상된 JSON 문자열이 DB에 저장된 경우**: `StructuredReportParser`가 실패를 반환하고, UI는 크래시 없이 리포트 미생성 Empty State를 표시하며 재분석 버튼을 제공한다.
- **스윙 레코드가 0개인 세션에서 처방 요청**: `SessionPrescriptionContextBuilder`의 빈 세션 안전 처리로 정상 Fallback 리포트 생성.

---

## 8. Acceptance Criteria (수용 기준)

- **AC-1 (모듈 의존성 검증)**: `verifyModuleDependencies` 검사에서 `:feature:history -> :core:coach`가 정상 통과해야 한다.
- **AC-2 (기 저장된 리포트 캐시 로딩)**: DB에 `aiCoachReportJson`이 있는 세션을 조회했을 때, ViewModel이 이를 `AiCoachReport`로 역직렬화하여 `aiCoachReport`에 즉시 반영해야 한다.
- **AC-3 (미생성 세션 Empty State 및 처방 버튼)**: 리포트가 없는 세션에서 `AI_COACH` 탭 진입 시 Empty State 문구, 톤 선택기, [🤖 AI 코치 처방 생성하기] 버튼이 표시되어야 한다.
- **AC-4 (처방 생성 요청 및 DB 저장)**: 처방 생성 버튼 클릭 시 `isGeneratingAiReport` 상태 전이, 리포트 생성, `saveAiCoachReport` 호출, 최종 UI 카드 갱신이 단위 테스트로 검증되어야 한다.
- **AC-5 (로딩 스켈레톤 및 탭 전환)**: 탭 전환 시 화면이 정상 변경되고, 생성 중일 때 `AiCoachLoadingSkeleton`이 렌더링되어야 한다.
- **AC-6 (빌드 및 테스트 통과)**: 선언된 테스트 명령이 0 failure로 통과해야 한다.

---

## 9. Testing Instructions (테스트 명령)

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

# 모듈 의존성 및 History 단위/UI 테스트
./gradlew :feature:history:test verifyModuleDependencies --rerun-tasks
```
