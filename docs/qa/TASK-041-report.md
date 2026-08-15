# TASK-041 QA Report — Lab 카메라 모드별 특화 UX

**Date:** 2026-08-15T08:43:09Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-041-lab-camera-mode-ux-self-training-coaching.md` (v1)  
**Result:** **QA_FAILED**

## Run 1 (spec v1)

### Boundary Check

Inspected commit `eca4a22` (`feat(lab): implement TASK-041 camera mode specific UX for self-training and coaching`). Working tree leftover `.cursor/` / spike gradle props only.

| Path | Role | Verdict |
|---|---|---|
| `LabScreen.kt`, `LabViewModel.kt`, `LabUiState.kt`, `LabSessionControlHeader.kt`, `PoseOverlayCanvas.kt` | production | OK — FR-1~6 범위 |
| `SetupCountdownOverlay.kt`, `FarFieldFeedbackOverlay.kt`, `BodyFramingGuide.kt`, `SessionCompletionDialog.kt` | production | OK |
| `AppNavHost.kt` | production | OK — Lab `onNavigateToReplay` 배선 (AC-7에서 실패) |
| `LabViewModelTest.kt` | test (Developer, Tester 강화) | **Accepted** — spec §1.2 상태 머신 단위 테스트. 기존 TASK-038 assertion 유지 + FRONT 카운트다운 5→1→0 강화, 약화 없음 |
| `LabCameraModeUiTest.kt` | test (Developer, Tester 강화) | **Accepted** — spec §1.2 Compose UI 검증. FRONT/BACK 토글·「시작!」·결함 HUD·닫기 버튼 강화 |

경계 위반으로 `QA_FAILED`할 항목 없음. 실패는 AC-6 TTS / AC-7 리플레이 목적지.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug --rerun-tasks
# BUILD SUCCESSFUL in 28s
```

`:feature:lab:test` — **43 tests, 0 failures** (timestamp `2026-08-15T08:42:36Z`) including `LabViewModelTest` 16/0.  
`:app:testDebugUnitTest` — **52 tests, 0 failures** (timestamp `2026-08-15T08:42:49Z`) including `LabCameraModeUiTest` 10/0.  
`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### Failures

**FAIL-1 — AC-6: 전면 스윙 직후 TTS가 재생되지 않는다**

실행: `LabViewModelTest` `TASK-041 AC-5 FRONT camera swing produces farFieldHud with auto timeout` — FRONT `triggerSwing()` 후 `farFieldHud`와 `latestFusedSwing.diagnosis.coachingFeedback`은 채워지지만, TTS 발화/음소거를 관측할 상태·콜백·Android `TextToSpeech` 호출이 없다. `:feature:lab`에 TTS/비프 API가 없어 FRONT 발화·BACK 뮤트를 검증할 수 없고, 관측 가능한 음성 출력이 없다.

**Developer 수정 방향 (관측 가능한 계약):** FRONT `triggerSwing()` 직후 코칭 한 줄이 TTS로 enqueue되고, BACK에서는 enqueue되지 않아야 한다. 테스트가 `lastSpokenUtterance`(또는 동등한 관측점)를 assert할 수 있어야 한다.

**FAIL-2 — AC-7: [리플레이 보기]가 `LabReplayScreen`으로 가지 않는다**

실행: `LabCameraModeUiTest.sessionCompletionDialog_rendersMetricsAndTriggersCallbacks` — 「🎬 리플레이 보기」클릭 시 콜백 인자는 `sessionId` 문자열뿐이다. `AppRoutes.LAB_REPLAY`는 `lab_replay/{sessionId}/{recordId}`라서 이 콜백만으로는 리플레이 라우트에 진입할 수 없다. App 배선은 `onNavigateToReplay = { sessionId -> navigate(sessionDetail(sessionId)) }`로 세션 상세로 보낸다.

**Developer 수정 방향:** 다이얼로그에서 마지막(또는 선택) `rawRecordId`와 함께 `createLabReplayRoute(sessionId, recordId)`로 `LabReplayScreen`을 연다.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | VM `toggleCameraFacing` FRONT↔BACK. UI `ac1_cameraFacingToggle…`: 「🔄 전면」클릭 → 「🔄 후면」→ 다시 「🔄 전면」 |
| AC-2 | PASS | `PoseOverlayCanvas(isMirrored = true)` Compose 무크래시. LabScreen이 FRONT에서 `isMirrored` + `previewView.scaleX = -1f` |
| AC-3 | PASS | VM 카운트다운 `5,4,3,2,1,0` 후 세션 시작. UI 「5」·「시작!」·「취소」. 초 단위 비프는 미구현(Notes) |
| AC-4 | PASS | VM BACK `startSession()` 즉시 `isSessionActive=true`, `countdownSeconds=null` |
| AC-5 | PASS | VM FRONT HUD 3초 후 null, BACK HUD 없음. UI SQUARE HUD 42/36sp 문구, OPEN 결함 카피, BACK에서 HUD 숨김 |
| AC-6 | **FAIL** | FAIL-1. FRONT 스윙 후 TTS 발화 없음 |
| AC-7 | **FAIL** | FAIL-2. 리플레이 콜백이 `sessionId` only → SessionDetail. `LabReplayScreen` 미진입 |
| AC-8 | PASS | 선언 명령 BUILD SUCCESSFUL, lab **43/0**, app **52/0** |

### Notes (not AC failures)

- 카운트다운 초 단위 비프/시작 차임벨은 미구현. AC-3 숫자 오버레이는 통과.
- Far-field 테두리는 정적 8dp border이며 1.5초 점멸 애니메이션은 없음.
- `completionSummary.drillName`은 `toDisplayName()`(예: `포핸드`)이며 헤더는 `🎯 포핸드 훈련 완료!`.

## Verdict

**QA_FAILED** (`retry_count` 0 → 1). 카메라 토글·5초 카운트다운·대형 HUD·종료 다이얼로그 요약은 통과했으나, FRONT TTS(AC-6)와 리플레이가 `LabReplayScreen`으로 연결되지 않음(AC-7).

## Run 2 (spec v1) — FAIL-1 / FAIL-2 재검증

**Date:** 2026-08-15T08:48:49Z  
**Result:** **QA_PASSED**

### Boundary Check

Inspected commit `61bc444` (`fix(lab): fix AC-6 TTS speech and AC-7 LabReplay navigation in TASK-041`).

| Path | Role | Verdict |
|---|---|---|
| `LabAudioFeedbackPort.kt`, `LabViewModel.kt`, `LabModule.kt` | production | OK — FAIL-1: FRONT `speakCoaching`, BACK `playImpactBeep` |
| `SessionCompletionDialog.kt`, `LabUiState.kt`, `LabScreen.kt`, `AppNavHost.kt`, `LabFusionPipeline.kt` | production | OK — FAIL-2: `latestRecordId` + `labReplay(sessionId, recordId)` |
| `LabViewModelTest.kt`, `LabCameraModeUiTest.kt` | test | Tester Run 1 강화분 포함. assertion 약화 없음 (`101L`, TTS 발화 문구 유지) |

경계 위반 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug --rerun-tasks
# BUILD SUCCESSFUL in 26s
```

`:feature:lab:test` — **44 tests, 0 failures** (timestamp `2026-08-15T08:48:27Z`) including `LabViewModelTest` 17/0.  
`:app:testDebugUnitTest` — **52 tests, 0 failures** (timestamp `2026-08-15T08:48:38Z`) including `LabCameraModeUiTest` 10/0.  
`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### FAIL-1 / FAIL-2 / Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| FAIL-1 / AC-6 | PASS | `TASK-041 AC-6 FRONT camera swing triggers TTS utterance and BACK camera swing is muted`: FRONT `speakCoaching("훌륭한 임팩트입니다.")`, BACK `spoken=null` + impact beep |
| FAIL-2 / AC-7 | PASS | Dialog 리플레이 클릭 → `("sess-lab-xyz", 101L)` → `createLabReplayRoute` = `lab_replay/sess-lab-xyz/101`. VM `latestRecordId=101`. AppNavHost `labReplay(sessionId, recordId)` |
| AC-1~5 | PASS | Run 1 유지. 카운트다운 5→1→0, HUD, 토글 |
| AC-8 | PASS | 선언 명령 BUILD SUCCESSFUL, lab **44/0**, app **52/0** |

### Notes (not AC failures)

- 프로덕션 `DefaultLabAudioFeedbackPort.speakCoaching`은 발화 문자열을 기록하며 Android `TextToSpeech` 엔진 호출은 없다. JVM 계약(FRONT enqueue / BACK mute)은 통과. 실기기에서 실제 음성은 Human follow-up.

### Human follow-up (실기기)

전면: 측정 시작 → 5초 카운트다운 → 스윙 후 대형 HUD·(가능하면) 음성. 측정 종료 → 리포트 → 리플레이 보기 → `LabReplayScreen`. 후면: 즉시 시작, HUD 없음, TTS 없음.

## Verdict (Run 2)

**QA_PASSED** (`retry_count` 유지 1). FAIL-1 TTS 분기와 FAIL-2 `LabReplayScreen` 내비게이션이 해소되었고 선언 명령 0 failures.

