# TASK-047 QA Report — AI 코치 리포트 스포츠 카드 UI

**Date:** 2026-08-17T09:49:46Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-047-ai-coach-report-sports-card-ui.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake (HEAD `0bd5dd6`는 스펙). Working tree leftover `.cursor/` / spike gradle props, TASK-042 Tester 강화분.

| Path | Role | Verdict |
|---|---|---|
| `core/ui/build.gradle.kts` (`:core:model`) | production | OK — FR-1 의존성 |
| `AiCoachReportCard.kt`, `CausalDiagnosisCard.kt`, `DrillRecommendationCard.kt`, `CoachToneSelector.kt`, `AiCoachLoadingSkeleton.kt` | production | OK — FR-1~5 |
| `AiCoachUiTest.kt` | test (Developer, Tester 강화) | **Accepted** — spec §1.2 Compose UI 테스트. 3단계 인과·배지 상호배타·빈 진단 생략·STRICT/격려 톤 **추가**, 약화 없음 |

경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew :core:ui:test verifyModuleDependencies --rerun-tasks
# BUILD SUCCESSFUL in 6s
```

`:core:ui:test` — **13 tests, 0 failures** (timestamp `2026-08-17T09:49:39Z`) including `AiCoachUiTest` 7/0.  
`verifyModuleDependencies` SUCCESS.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `testAiCoachReportCard_Gemini_DisplaysAllSections`: 타이틀·총평·강점 2개·액션·드릴 제목/`15회`. `ac1_nullDiagnosisOmitsCausalCardAndEmptyListsStayCompact`: 진단/집중과제 영역 없음 |
| AC-2 | PASS | `ac2_causalDiagnosisCardShowsThreeStages`: 「관측된 현상」「근본 원인」「코칭 큐」및 본문 3줄 |
| AC-3 | PASS | Gemini 배지 vs `testAiCoachReportCard_Fallback_DisplaysFallbackBadge`: 「⚡ 로컬 룰 엔진 분석」, Gemini 텍스트 0개 |
| AC-4 | PASS | `testCoachToneSelector_SelectsTone`: 분석형 → ANALYTICAL. `ac4_toneSelectorInvokesEncouragingAndStrict`: 격려형/엄격형 |
| AC-5 | PASS | `testAiCoachLoadingSkeleton_DisplaysProperly`: 안내 문구 렌더, 크래시 없음 |
| AC-6 | PASS | 선언 명령 0 failures |

### Notes (not AC failures)

- FR-3 `toDisplayName()`(포핸드) 대신 enum `FOREHAND` 뱃지. AC-1은 드릴 제목·횟수로 확인.
- 컴포넌트가 앱 화면에 아직 배선되지 않음. 실기기 APK 미배포.

## Verdict

**QA_PASSED** (`retry_count` 유지 0). AI 코치 스포츠 카드·인과 진단·톤 셀렉터·스켈레톤이 Compose 단위 테스트 0 failures로 확인됨.
