# TASK-045 QA Report — :core:coach 프롬프트 템플릿 / 구조화 파서

**Date:** 2026-08-17T09:26:18Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-045-core-coach-prompt-and-structured-parser.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake (HEAD `0eef031`는 스펙). Working tree leftover `.cursor/` / spike gradle props, TASK-042 Tester 강화분(`LabSunlitCourtUiTest.kt`, `PoseOverlayCanvasTest.kt`).

| Path | Role | Verdict |
|---|---|---|
| `settings.gradle.kts`, `build.gradle.kts` (`verifyModuleDependencies`) | production | OK — FR-1 |
| `core/coach/build.gradle.kts`, `CoachPromptBuilder.kt`, `StructuredReportParser.kt`, `LlmCoachClient.kt` | production | OK — FR-1~4 |
| `CoachModuleTest.kt` | test (Developer, Tester 강화) | **Accepted** — spec §1.2 단위 테스트. 빈 세션 프롬프트·펜스 JSON·unique `reportId`·Mock 고효율 경로 **추가**, 약화 없음 |

경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew :core:coach:test verifyModuleDependencies --rerun-tasks
# BUILD SUCCESSFUL in 3s
```

`:core:coach:test` — **8 tests, 0 failures** (timestamp `2026-08-17T09:26:13Z`) `CoachModuleTest` 8/0.  
`verifyModuleDependencies` SUCCESS.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `:core:coach:test` 태스크가 모듈을 컴파일·실행. `verifyModuleDependencies` SUCCESS (`:core:coach` → model/fusion allow-list) |
| AC-2 | PASS | `testPromptBuilderTonesAndContext`: `toJsonString()` 포함, ENCOURAGING/ANALYTICAL/STRICT 지침. `ac2_emptySessionStillEmbedsZeroCountContext`: `"totalSwingCount":0` |
| AC-3 | PASS | `testStructuredReportParserSuccessWithMarkdown`: ```json 블록 → `overallSummary`/`LATE_HIT`/`FOREHAND` 드릴. `ac3_parserAcceptsBareFenceAndPlainJson`. `ac3_reportIdsAreUniquePerParse` |
| AC-4 | PASS | `testStructuredReportParserFailureAndResilience`: 비-JSON·필수 필드 누락·빈 문자열 → `Result.failure`. 부분 JSON은 `emptyList` |
| AC-5 | PASS | `testMockLlmCoachClient`: STRICT·효율 60·`EARLY_BODY_OPEN`·정타 50%. `ac5_mockClientUsesHighEfficiencyAndNullFlaw`: 결함 null·드릴 FOREHAND |
| AC-6 | PASS | 선언 명령 0 failures |

### Notes (not AC failures)

- spec §5 UI 없음. 앱 APK 미배포.
- FR-3 파라미터명 `fallbackModelName`과 §4 `rawModelName`이 다름. 구현은 §4를 따르며 파싱 결과는 `rawModelName`으로 관찰됨.

## Verdict

**QA_PASSED** (`retry_count` 유지 0). `:core:coach` 프롬프트·마크다운 JSON 파서·Mock 클라이언트가 선언 명령 0 failures로 확인됨.
