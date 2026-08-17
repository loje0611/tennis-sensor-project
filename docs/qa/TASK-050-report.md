# TASK-050 QA Report — AI 코치 환경설정 UX 및 전 구간 E2E

**Date:** 2026-08-17T13:35:16Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-050-ai-coach-settings-and-e2e-integration.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake (HEAD `42314de`는 스펙). Leftover: TASK-042 Tester 강화분, TASK-048 산출물, `.cursor/` / spike gradle props.

| Path | Role | Verdict |
|---|---|---|
| `AiCoachPreferencesRepository.kt` (new), `CoreDataModule.kt` | production | OK — FR-1 |
| `SettingsViewModel.kt`, `SettingsScreen.kt`, `AppModule.kt`, `app/build.gradle.kts` (`:core:coach`) | production | OK — FR-2/3 |
| `LabViewModel.kt`, `SessionDetailViewModel.kt` | production | OK — FR-4 배선(톤 미반영은 AC 실패) |
| `AiCoachReport.kt` `LOCAL_RULE_ONLY` | production | OK — FR-3 프로바이더 |
| `AiCoachReportTest.kt` enum 집합 | test | **Accepted** — spec FR-3 로컬 룰 전용 값 추가. 약화 없음 |
| `SessionDetailViewModelTest.kt`, navigation/history Fakes | test | **Accepted** — 생성자 `aiCoachPreferences` 식별자 추가. 단정 약화 없음 |

경계 위반으로 `QA_FAILED`할 항목 없음. 실패는 FR-4 기본 톤 미반영.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew test verifyModuleDependencies --rerun-tasks
# BUILD FAILED — :feature:lab:testDebugUnitTest 46 tests, 1 failed (2026-08-17T13:28Z)

./gradlew verifyModuleDependencies :app:testDebugUnitTest \
  --tests 'io.github.loje0611.tennisdoc.settings.*' \
  --tests 'io.github.loje0611.tennisdoc.coach.Phase4EndToEndIntegrationTest' --rerun-tasks
# BUILD SUCCESSFUL in 17s
```

`verifyModuleDependencies` SUCCESS.  
`:feature:lab:test` — **46 tests, 1 failure**.  
`AiCoachPreferencesRepositoryTest` — **2/0**.  
`AiCoachSettingsUiTest` — **4/0**.  
`Phase4EndToEndIntegrationTest` — **1/0**.

### Failures

**FAIL-1 — FR-4 / AC-5: Lab 처방 요청이 설정의 기본 코칭 톤을 쓰지 않는다**

실행: `LabViewModelTest.TASK-050 FR-4 requestAiCoachReport uses preferences tone and blank key fallback`  
기대: prefs `defaultCoachTone = STRICT` + blank API key로 `requestAiCoachReport()`(인자 없음) 시 총평이 `"결과에 집중해야 합니다."`로 시작  
실제: Fallback 리포트는 생성되나 STRICT 접두가 없음 (`LabViewModelTest.kt:693`)

`requestAiCoachReport(tone: CoachTone = ENCOURAGING)`가 prefs `defaultCoachTone`을 읽지 않고 파라미터 기본값만 사용한다. `LabScreen`은 `requestAiCoachReport()`만 호출한다. spec FR-4 「API Key와 기본 톤을 자동 반영」위배.

**Developer 수정 방향 (관측 가능한 계약):** 인자 없이 `requestAiCoachReport()`를 호출하면 `AiCoachPreferencesRepository.defaultCoachTone`이 `createReport(..., tone = …)`에 들어가야 한다. 동일 테스트가 STRICT 접두 총평을 통과해야 한다. `SessionDetailViewModel`도 초기/미지정 톤이 prefs 기본값을 따라야 한다.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `ac1_persistsApiKeyProviderAndCoachToneRoundTrip`: Key/MOCK/STRICT 저장 후 동일 값, blank Key → null |
| AC-2 | PASS | `ac2_settingsSectionRendersProviderKeyTestButtonAndToneSelector`: 섹션 타이틀·Gemini 프로바이더·API Key·연결 테스트·도움말·톤 3종 |
| AC-3 | PASS | `ac3_testGeminiApiKeyTransitionsTestingThenSuccessOrError`: `AIza…` → Testing → Success, 그 외 → Testing → Error |
| AC-4 | PASS | `ac4_toneChangeIsPersistedToPreferencesImmediately` + `ac4_toneSelectorClickPersistsStrict` |
| AC-5 | FAIL | E2E 집계→Fallback→다이얼로그 카드는 통과. LabViewModel이 설정 톤을 안 씀 (FAIL-1) |
| AC-6 | FAIL | 선언 명령 `./gradlew test` 1 failure |

### Notes (not AC failures)

- `testGeminiApiKey`는 Gemini 핑이 아니라 `AIza` 접두 검사. AC-3은 상태 전이만 요구.
- DataStore 암호화는 명세 정의에 있으나 AC 없음. 평문 Preferences 저장.

## Verdict

**QA_FAILED** (`retry_count` 0→1). 설정 영속화·설정 화면·연결 테스트 상태 전이는 통과했으나, Lab 처방 파이프라인이 설정 화면의 기본 코칭 톤을 반영하지 않는다.

## Run 2 (spec v1) — 기본 코칭 톤 prefs 반영

**Date:** 2026-08-17T13:35:16Z  
**Result:** **QA_PASSED**

### Boundary Check

Developer 재시도: `LabViewModel.requestAiCoachReport(tone: CoachTone? = null)`가 `tone ?: aiCoachPreferences.defaultCoachTone`을 `createReport`에 전달. `SessionDetailViewModel`도 동일. Tester FR-4 단정 약화 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew test verifyModuleDependencies --rerun-tasks
# BUILD SUCCESSFUL in 32s
```

`verifyModuleDependencies` SUCCESS.  
`LabViewModelTest` — **19/0** including FR-4 STRICT 톤.  
`AiCoachPreferencesRepositoryTest` — **2/0**.  
`AiCoachSettingsUiTest` — **4/0**.  
`Phase4EndToEndIntegrationTest` — **1/0**.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | Run 1과 동일, 재실행 0 failures |
| AC-2 | PASS | Run 1과 동일 |
| AC-3 | PASS | Run 1과 동일 |
| AC-4 | PASS | Run 1과 동일 |
| AC-5 | PASS | FR-4: blank key + STRICT prefs → `"결과에 집중해야 합니다."` + Fallback JSON. E2E 다이얼로그 `⚡ 로컬 룰 엔진 분석` |
| AC-6 | PASS | 선언 명령 `./gradlew test` 0 failures |

### Notes (not AC failures)

- History 화면은 `requestAiCoachReport(state.selectedTone)`를 넘기므로, 탭에서 톤을 바꾸면 그 값이 우선한다. 인자 생략 시에만 prefs 기본 톤을 쓴다.

## Verdict

**QA_PASSED** (`retry_count` 유지 1). 설정 영속화·설정 UI·연결 테스트·Lab 처방 시 기본 톤/빈 키 Fallback이 전체 단위 테스트 0 failures로 확인됨.
