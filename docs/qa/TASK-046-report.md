# TASK-046 QA Report — 로컬 Fallback 엔진 & Gemini 클라이언트

**Date:** 2026-08-17T09:32:05Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-046-local-fallback-engine-and-gemini-client.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake (HEAD `842fd7e`는 스펙). Working tree leftover `.cursor/` / spike gradle props, TASK-042 Tester 강화분.

| Path | Role | Verdict |
|---|---|---|
| `LocalRuleBasedCoachEngine.kt` | production | OK — FR-1 |
| `HttpTransport.kt`, `DefaultHttpTransport` | production | OK — FR-2 |
| `GeminiCoachClient.kt` | production | OK — FR-2 |
| `CompositeAiCoachService.kt` | production | OK — FR-3 |
| `FallbackEngineTest.kt` | test (Developer, Tester 강화) | **Accepted** — spec §1.2 Fake HTTP/Fallback 검증. 태그 매핑·blank key 무호출·파싱 실패 Fallback **추가**, 약화 없음 |

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

`:core:coach:test` — **17 tests, 0 failures** (timestamp `2026-08-17T09:31:58Z`): `CoachModuleTest` 8/0, `FallbackEngineTest` 9/0.  
`verifyModuleDependencies` SUCCESS.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `testLocalRuleBasedCoachEngineFallbackReport`: `isFallbackReport=true`, `rawModelName=local-rule-engine`, EARLY_BODY_OPEN 한국어 큐, 효율≥80/정타≥70 강점. `ac1_faceOpenAndCleanStrikeMapKoreanDiagnosis`: FACE_OPEN/CLEAN_STRIKE/LATE_CONTACT |
| AC-2 | PASS | `testGeminiCoachClientSuccess`: Fake HTTP 200 → `Remote OK`, `isFallbackReport=false`, URL `generativelanguage.googleapis.com`/`gemini-1.5-flash` |
| AC-3 | PASS | `testGeminiCoachClientNetworkError`: HTTP 500·네트워크 예외·401 모두 `Result.failure`, 크래시 없음 |
| AC-4 | PASS | `testCompositeServiceMissingApiKeyFallback`: `apiKey=null` → Fallback. `ac4_blankApiKeyDoesNotCallTransport`: 공백 키, `postCount=0` |
| AC-5 | PASS | `testCompositeServiceNetworkFailureAutoFallback`: 500 → local-rule-engine. `ac5_invalidGeminiJsonFallsBackWithoutCrash`: 200+비JSON → FACE_OPEN Fallback |
| AC-6 | PASS | 선언 명령 0 failures |

### Notes (not AC failures)

- FR-1 「추천 드릴 2~3종」은 구현이 1건이다. AC-1은 태그·효율 반영과 플래그만 요구하므로 실패로 보지 않음.
- spec §5 UI 없음. 앱 APK 미배포.

## Verdict

**QA_PASSED** (`retry_count` 유지 0). 로컬 Fallback·Fake Gemini 성공/실패·API Key 부재 무중단 전환이 선언 명령 0 failures로 확인됨.
