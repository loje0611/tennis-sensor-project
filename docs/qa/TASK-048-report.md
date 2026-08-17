# TASK-048 QA Report — Lab 세션 완료 다이얼로그 AI 코칭 리포트 연동

**Date:** 2026-08-17T10:22:43Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-048-lab-session-completion-ai-report-integration.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake (HEAD `251757e`는 스펙). Working tree leftover `.cursor/` / spike gradle props, TASK-042 Tester 강화분(`LabSunlitCourtUiTest.kt`, `PoseOverlayCanvasTest.kt`). Developer scratch `docs/task-board.tmp.json`, `docs/turn.tmp.json`는 핸드오프 전 삭제.

| Path | Role | Verdict |
|---|---|---|
| `TennisDocAI/build.gradle.kts` (`:core:coach` allow-list) | production | OK — FR-1 |
| `feature/lab/build.gradle.kts` | production | OK — FR-1 |
| `LabUiState.kt`, `LabViewModel.kt`, `LabScreen.kt`, `SessionCompletionDialog.kt` | production | OK — FR-2~4 |
| `LabReplayViewModel.kt` (`saveAiCoachReport` Fake stub) | production | OK — 인터페이스 추가에 따른 컴파일 맞춤 |
| `LabViewModelTest.kt` | test (Developer, Tester 강화) | **Accepted** — spec §1.2 / AC-5 단위 테스트. `reportJson == "{}"` tautology를 **제거**하고 실제 총평 직렬화 단정 **추가**. 약화 없음 |

경계 위반으로 `QA_FAILED`할 항목 없음. 실패는 AC-5 영속화 및 Hilt 바인딩.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew :feature:lab:test verifyModuleDependencies --rerun-tasks
# BUILD FAILED — 45 tests completed, 1 failed (timestamp 2026-08-17T10:15Z)

./gradlew :app:testDebugUnitTest --tests io.github.loje0611.tennisdoc.lab.LabSessionCompletionAiReportUiTest --rerun-tasks
# BUILD FAILED — :app:hiltJavaCompileDebug MissingBinding CompositeAiCoachService
```

`:feature:lab:test` — **45 tests, 1 failure**.  
`verifyModuleDependencies` SUCCESS.  
`:app:testDebugUnitTest` (AC-2/3/4 Compose) — **did not execute**; Hilt compile failed first.

### Failures

**FAIL-1 — AC-5: `saveAiCoachReport`가 생성된 리포트가 아니라 빈 JSON `"{}"`를 저장한다**

실행: `LabViewModelTest.TASK-048 AC-5 requestAiCoachReport triggers loading and updates state with report`  
기대: `savedReportJson != "{}"` 이고 `savedReportJson`이 `aiCoachReport.overallSummary`를 포함  
실제: `assertNotEquals("{}", savedReportJson)` 실패 (`LabViewModelTest.kt:627`)

`requestAiCoachReport`는 Fallback 리포트를 UI 상태에 넣고 `saveAiCoachReport`를 호출하지만, 영속 페이로드가 스텁 `"{}"`이다. spec FR-3 step 5 / AC-5 「DB 저장」은 반환된 `AiCoachReport`의 JSON이어야 한다.

**Developer 수정 방향 (관측 가능한 계약):** `saveAiCoachReport(sessionId, reportJson, generatedAt)`의 `reportJson`이 생성 리포트의 `overallSummary`(및 카드 복원에 필요한 필드)를 포함해야 한다. 동일 테스트가 `assertNotEquals("{}", …)`와 `contains(overallSummary)`를 통과해야 한다.

**FAIL-2 — Hilt: `CompositeAiCoachService` 바인딩 없음 (앱 컴파일 실패)**

실행: `:app:hiltJavaCompileDebug`  
에러: `[Dagger/MissingBinding] CompositeAiCoachService cannot be provided without an @Inject constructor or an @Provides-annotated method.`  
주입 지점: `LabViewModel(…, aiCoachService, …)`

Lab 세션 완료 UI를 실기기에서 띄울 수 없다. AC-2/3/4 Compose 테스트(`LabSessionCompletionAiReportUiTest`)도 이 컴파일 실패로 미실행.

**Developer 수정 방향 (관측 가능한 계약):** `:app:hiltJavaCompileDebug`(또는 `:app:testDebugUnitTest`)가 MissingBinding 없이 성공해야 한다. `LabViewModel`이 Hilt로 `CompositeAiCoachService`를 받을 수 있어야 한다.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `verifyModuleDependencies` SUCCESS (`:feature:lab` allow-list에 `:core:coach`) |
| AC-2 | not verified | `LabSessionCompletionAiReportUiTest.ac2_generateButtonShownWhenNoReportAndClickInvokesCallback` — `:app` Hilt 컴파일 실패로 미실행 (FAIL-2) |
| AC-3 | not verified | `ac3_skeletonShownWhileGeneratingAndButtonHidden` — 동일 |
| AC-4 | not verified | `ac4_reportCardExpandedWithSummaryBadgeAndDrill` — 동일 |
| AC-5 | FAIL | FAIL-1: 로딩 종료·Fallback 리포트 UI 갱신·`saveAiCoachReport` 호출은 되나 payload가 `"{}"` |
| AC-6 | FAIL | 선언 명령 `:feature:lab:test` 1 failure |

### Notes (not AC failures)

- spec §7 빈 세션 Fallback: `requestAiCoachReport`가 `swings.isEmpty() && swingCount == 0`이면 즉시 return. 이번 Run의 실패 원인은 아님.
- `LabViewModel` catch가 예외를 삼키고 `isGeneratingAiReport`만 false로 되돌림. 관측 가능한 실패 UI는 spec AC에 없음.

## Verdict

**QA_FAILED** (`retry_count` 0→1). 모듈 허용 목록은 통과했으나 AI 리포트 DB 영속화가 스텁 JSON이고, Hilt에 `CompositeAiCoachService` 바인딩이 없어 앱이 컴파일되지 않는다.

## Run 2 (spec v1) — JSON 영속화 + Hilt 바인딩

**Date:** 2026-08-17T10:22:43Z  
**Result:** **QA_PASSED**

### Boundary Check

Developer 재시도: `LabViewModel.requestAiCoachReport`가 `overallSummary`를 포함한 JSON을 `saveAiCoachReport`에 넘김. `LabModule.provideCompositeAiCoachService` 추가. Tester assertion 약화 없음.

| Path | Role | Verdict |
|---|---|---|
| `LabViewModel.kt` (reportJson 직렬화) | production | OK — FAIL-1 수정 |
| `LabModule.kt` (`@Provides CompositeAiCoachService`) | production | OK — FAIL-2 수정 |
| `RecordingSwingHistoryRepository.kt` | test Fake | **Accepted** — 인터페이스 메서드 추가. Developer `TODO`를 no-op stub으로 교체(단정 약화 아님) |
| `LabViewModelTest.kt` | test | Tester 유지. 약화 없음 |

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew :feature:lab:test verifyModuleDependencies :app:testDebugUnitTest --tests io.github.loje0611.tennisdoc.lab.LabSessionCompletionAiReportUiTest --rerun-tasks
# BUILD SUCCESSFUL in 19s
```

`:feature:lab:test` — **45 tests, 0 failures** (timestamp `2026-08-17T10:22:21Z`).  
`LabViewModelTest` — **18/0**.  
`verifyModuleDependencies` SUCCESS.  
`LabSessionCompletionAiReportUiTest` — **3/0**. Hilt compile SUCCESS.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `verifyModuleDependencies` SUCCESS |
| AC-2 | PASS | `ac2_generateButtonShownWhenNoReportAndClickInvokesCallback`: 「🤖 AI 코치 처방받기」표시·클릭 1회, 스켈레톤/리포트 카드 0 |
| AC-3 | PASS | `ac3_skeletonShownWhileGeneratingAndButtonHidden`: 분석 안내 문구, 처방받기 버튼 0, 리플레이/닫기 유지 |
| AC-4 | PASS | `ac4_reportCardExpandedWithSummaryBadgeAndDrill`: 「🤖 AI 코치 처방 리포트」·「✨ Gemini AI 분석」·총평·드릴 제목 |
| AC-5 | PASS | `TASK-048 AC-5 …`: Fallback 리포트, `savedReportJson != "{}"`, `contains(overallSummary)`, sessionId `test-session-id` |
| AC-6 | PASS | 선언 명령 0 failures |

### Notes (not AC failures)

- 영속 JSON은 `reportId`/`sessionId`/`overallSummary`/`isFallbackReport`만 포함. 다이얼로그 카드는 ViewModel 메모리 리포트로 렌더되므로 AC-4는 통과. History 재로드 round-trip은 본 태스크 AC 밖.
- spec §7 빈 세션은 여전히 early return.

## Verdict

**QA_PASSED** (`retry_count` 유지 1). 처방받기 버튼·스켈레톤·리포트 카드·리포트 JSON 영속화·Hilt 바인딩이 단위/Compose 테스트 0 failures로 확인됨.
