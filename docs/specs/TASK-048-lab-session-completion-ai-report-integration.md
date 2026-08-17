# TASK-048 명세서: :feature:lab 세션 완료 다이얼로그 AI 코칭 리포트 연동

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-17 | PM | 최초 작성 (Phase 4 C그룹: Lab 세션 완료 다이얼로그에 AI 코치 처방받기 인터랙션, 비동기 생성 파이프라인 및 리포트 카드 뷰어 연동) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
`TASK-046`과 `TASK-047`을 통해 AI 코치 비즈니스 로직(`:core:coach`)과 Clean Sunlit Court 테마 기반 스포츠 카드 UI(`:core:ui`)가 완성되었습니다.
본 태스크(`TASK-048`)는 사용자가 Lab 훈련을 마치고 세션을 종료했을 때 나타나는 **세션 완료 다이얼로그(`SessionCompletionDialog`)에 [🤖 AI 코치 처방받기] 기능을 연동**하고, **`LabViewModel`에서 세션 융합 지표를 바탕으로 리포트를 생성·영속화하여 다이얼로그 내에 즉시 시각화**하는 파이프라인을 구축합니다.

### 1.2 범위
- `TennisDocAI/build.gradle.kts`의 `verifyModuleDependencies`에 `:feature:lab -> :core:coach` 허용 의존성 추가.
- `feature/lab/build.gradle.kts`에 `implementation(project(":core:coach"))` 추가.
- `LabUiState` 및 `SessionCompletionSummary`에 AI 코치 리포트 관련 상태(`aiCoachReport: AiCoachReport?`, `isGeneratingAiReport: Boolean`) 추가.
- `LabViewModel`에 `requestAiCoachReport()` 비동기 액션 구현 (세션 융합 스윙들 ➔ `SessionPrescriptionContextBuilder` ➔ `CompositeAiCoachService` ➔ `SwingHistoryRepository.saveAiCoachReport` 영속화 ➔ UI 상태 갱신).
- `SessionCompletionDialog`에 [🤖 AI 코치 처방받기] 원터치 버튼, `AiCoachLoadingSkeleton` 로딩 뷰, 생성 완료 시 `AiCoachReportCard` 확장 렌더링 지원.
- 단위 및 Compose UI 렌더링 테스트 구현.

---

## 2. Definitions & References (정의 및 참조)

- **`SessionCompletionDialog`**: Lab 세션 측정 종료 시 훈련 요약(총 스윙, 소요 시간, 정타율, 평균 효율) 및 동기 리플레이 이동을 제공하는 다이얼로그.
- **`CompositeAiCoachService`** (`:core:coach`): API Key 유무 및 네트워크 상태에 따라 Gemini 또는 로컬 Fallback 리포트를 무중단으로 생성하는 서비스.
- **`SessionPrescriptionContextBuilder`** (`:core:fusion`): 세션 스윙 목록으로부터 결정론적 집계 컨텍스트를 생성하는 빌더.

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: 모듈 의존성 및 빌드 설정 갱신
- `TennisDocAI/build.gradle.kts`의 `verifyModuleDependencies`에서 `:feature:lab`의 허용 목록에 `":core:coach"`를 추가한다.
- `feature/lab/build.gradle.kts`의 `dependencies`에 `implementation(project(":core:coach"))`를 추가한다.

### FR-2: `LabUiState` 및 세션 완료 상태 모델 확장
- `LabUiState`에 다음 필드를 추가한다:
  - `val aiCoachReport: AiCoachReport? = null` (생성된 AI 코치 리포트)
  - `val isGeneratingAiReport: Boolean = false` (AI 리포트 비동기 생성 중 여부)
- `SessionCompletionSummary`에 `aiCoachReport` 및 `isGeneratingAiReport` 필드를 연결하거나 다이얼로그 파라미터로 제공한다.

### FR-3: `LabViewModel` AI 리포트 생성 및 영속화 로직 구현
- `LabViewModel`에 `CompositeAiCoachService` (또는 주입받은 서비스) 및 `SessionPrescriptionContextBuilder` 연동:
- `fun requestAiCoachReport(tone: CoachTone = CoachTone.ENCOURAGING)`:
  1. 현재 세션의 스윙 목록(`recordedSwings` / `completedSessionSwings`)이 존재하는지 확인.
  2. `_uiState`를 업데이트하여 `isGeneratingAiReport = true`로 설정.
  3. `SessionPrescriptionContextBuilder.buildContext(sessionId, drillType, recordedSwings, baseline, durationSeconds)` 호출.
  4. `CompositeAiCoachService.createReport(context, tone = tone)` 비동기 호출.
  5. 반환된 `AiCoachReport`를 `SwingHistoryRepository.saveAiCoachReport(sessionId, reportJson, generatedAt)`으로 DB에 영속화.
  6. `_uiState`를 `aiCoachReport = report`, `isGeneratingAiReport = false`로 갱신.

### FR-4: `SessionCompletionDialog` UI 확장
- **AI 리포트 미생성 상태 (`aiReport == null && !isGenerating`)**:
  - 기존 세션 요약 카드 아래에 **[🤖 AI 코치 처방받기]** 액션 버튼(Royal Blue 테두리 아웃라인 버튼)을 배치.
  - 클릭 시 `onGenerateAiReport()` 콜백 트리거.
- **AI 리포트 생성 중 상태 (`isGenerating == true`)**:
  - `AiCoachLoadingSkeleton` 컴포넌트를 표시하여 펄스 로딩 피드백 제공.
- **AI 리포트 생성 완료 상태 (`aiReport != null`)**:
  - 다이얼로그 내부(또는 스크롤 가능한 컬럼)에 `AiCoachReportCard(report = aiReport)`를 확장 렌더링.
  - 출처 배지(`✨ Gemini AI 분석` 또는 `⚡ 로컬 룰 엔진 분석`), 인과 결함 진단, 추천 드릴 카드 등이 매끄럽게 표시됨.
- 하단 [🎬 리플레이 보기] 및 [닫기 / 새 훈련] 버튼 유지.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

```kotlin
package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.loje0611.tennisdoc.core.model.AiCoachReport

@Composable
fun SessionCompletionDialog(
    summary: SessionCompletionSummary?,
    aiReport: AiCoachReport? = null,
    isGeneratingAiReport: Boolean = false,
    onGenerateAiReport: () -> Unit = {},
    onDismiss: () -> Unit,
    onNavigateToReplay: (sessionId: String, recordId: Long) -> Unit,
    modifier: Modifier = Modifier
)
```

```kotlin
// LabViewModel.kt
fun requestAiCoachReport(tone: CoachTone = CoachTone.ENCOURAGING)
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- **Clean Sunlit Court 테마 유지**:
  - 다이얼로그 배경: `#FFFFFF`, 곡률 `20.dp`.
  - [🤖 AI 코치 처방받기] 버튼: Royal Blue 테두리(`0x330066FF`), 텍스트 `#0066FF`, 배경 `#F8FAFC`.
  - 생성 완료 시 다이얼로그 최대 높이를 화면의 80%로 제한하고 내부 `verticalScroll`을 적용하여 화면 밖으로 넘치지 않도록 구성.
- **인터랙션 피드백**:
  - 처방받기 터치 시 즉시 스켈레톤 로딩으로 전환되어 지연 없는 반응성 제공.

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **무중단 렌더링**: 네트워크 오류나 타임아웃 발생 시에도 `CompositeAiCoachService`의 로컬 Fallback 리포트가 안전하게 주입되어 다이얼로그가 멈추거나 크래시되지 않아야 한다.
- **단방향 아키텍처**: ViewModel은 `StateFlow`를 통해 `isGeneratingAiReport`와 `aiCoachReport`를 발행하며, UI는 단방향으로 이를 소비한다.
- **모듈 의존성 검증**: `verifyModuleDependencies` 태스크가 0 위반으로 통과해야 한다.

---

## 7. Error Handling & Edge Cases (오류 처리 및 예외 상황)

- **스윙이 0개인 세션에서 처방 요청**: `SessionPrescriptionContextBuilder`의 빈 세션 안전 처리 및 Fallback 엔진을 통해 정상적인 기본 안내 리포트가 렌더링됨.
- **다이얼로그 닫기 후 재오픈**: ViewModel 상태가 유지되어 이미 생성된 리포트가 즉시 재표시됨.

---

## 8. Acceptance Criteria (수용 기준)

- **AC-1 (모듈 의존성 통과)**: `verifyModuleDependencies` 검사에서 `:feature:lab -> :core:coach`가 정상 통과해야 한다.
- **AC-2 (처방받기 버튼 표시 및 콜백)**: AI 리포트가 없을 때 `SessionCompletionDialog`에 [🤖 AI 코치 처방받기] 버튼이 표시되고 클릭 시 콜백이 트리거되어야 한다.
- **AC-3 (스켈레톤 로딩 렌더링)**: `isGeneratingAiReport == true`일 때 다이얼로그 내에 로딩 스켈레톤 컴포넌트가 렌더링되어야 한다.
- **AC-4 (AI 리포트 카드 확장 렌더링)**: `aiReport != null`일 때 다이얼로그 내에 `AiCoachReportCard`가 포함되어 총평, 출처 배지, 추천 드릴이 모두 정상 표시되어야 한다.
- **AC-5 (ViewModel 파이프라인 검증)**: `LabViewModel.requestAiCoachReport` 호출 시 `isGeneratingAiReport` 상태 전이, 서비스 호출, DB 저장, 최종 `aiCoachReport` 갱신이 단위 테스트로 검증되어야 한다.
- **AC-6 (빌드 및 테스트 통과)**: 선언된 테스트 명령이 0 failure로 통과해야 한다.

---

## 9. Testing Instructions (테스트 명령)

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

# 모듈 의존성 및 Lab 단위/UI 테스트
./gradlew :feature:lab:test verifyModuleDependencies --rerun-tasks
```
