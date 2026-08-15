# TASK-038 QA Report — Lab 세션 드릴 가이드 및 실시간 융합 피드백 UI

**Date:** 2026-08-14T14:25:14Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-038-lab-drill-guide-realtime-feedback-ui.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected `git diff --name-only` and `git status --short` at tester wake (`next_agent=tester`, `task_id=TASK-038`).

| Path | Role | Verdict |
|---|---|---|
| `feature/lab/ui/DrillSelectorBar.kt` 등 4 composable + `LabUiState.kt` | production | OK — FR-1~4 / AC-1 |
| `feature/lab/ui/LabScreen.kt`, `LabViewModel.kt` | production | OK — 오버레이 배선, 세션 액션 |
| `feature/lab/session/LabSessionPort.kt`, `di/LabModule.kt` | production | OK — 세션 포트 / 파이프라인 DI |
| `app/.../LabSessionPortImpl.kt`, `AppModule.kt` | production | OK — `SwingAnalysisSessionState` 바인딩 |
| `feature/lab/build.gradle.kts` | production | OK — Hilt/KSP |
| `.../ui/LabViewModelTest.kt` | test (Developer) | **Accepted** — spec §1.2 및 AC-2/AC-3/AC-6. assertion 약화 없음. Tester가 `SessionType.LAB` 인자 기록·피로/CRITICAL `uiState`·pose/IMU 회귀를 추가 |
| `docs/qa/TASK-012`–`030`, `A-B-group-gap-fill-report.md` | prior Tester | TASK-038 Developer 범위 밖 |
| `docs/task-board.json`, `docs/turn.json` | workflow | 보드/턴 상태 |
| `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties` | untracked leftover | TASK-038과 무관 |
| `docs/specs/**` | PM | 이번 사이클에서 수정 없음 |

경계 위반으로 `QA_FAILED`할 항목 없음. 실패는 Lab 탭 ViewModel 미주입(AC-3).

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
# BUILD SUCCESSFUL in 9s
```

`:feature:lab:test` — **22 tests, 0 failures**

| Suite | Tests | Failures |
|---|---|---|
| `LabViewModelTest` | 5 | 0 |
| 회귀 (`LabFusion*` / `Pose*`) | 17 | 0 |

`:app:testDebugUnitTest` — **29 tests, 0 failures**

| Suite | Tests | Failures |
|---|---|---|
| `LabDrillGuideUiTest` | 2 | 0 |
| `LabSessionPortImplTest` | 1 | 0 |
| 회귀 | 26 | 0 |

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### Failure

**FAIL-1 — AC-3: Lab 탭에서 「측정 시작」이 `startSession(SessionType.LAB, drillType)`을 호출하지 않는다**

관측:

- `LabSessionControlHeader` 단독 Compose 테스트는 「측정 시작」 클릭 → 콜백 → 「측정 종료」 토글까지 통과했다.
- `LabViewModel.startSession()`은 `LabSessionPort.startSession(SessionType.LAB, selectedDrill)`을 호출한다 (`LabViewModelTest` AC-3: SERVE → `lastStartType=LAB`).
- `LabSessionPortImpl`은 그 인자를 `SwingAnalysisSessionState`에 전달한다 (`LabSessionPortImplTest` 1/0).
- Lab 시작 목적지 `AppNavHost`는 `LabScreen()`만 호출하고 `LabViewModel`을 넘기지 않는다. `LabScreen` 기본값은 `viewModel = null`이므로 `onStartSession = { viewModel?.startSession() }`는 no-op이다. 드릴 칩 `onSelectDrill`도 동일하다.

**Developer 수정 방향 (관측 가능한 계약):** Lab 라우트에서 `hiltViewModel<LabViewModel>()`을 받아 `LabScreen(viewModel = …)`에 전달한다. 카메라 권한 허용 후 「측정 시작」을 누르면 버튼 문구가 「측정 종료」로 바뀌고, `SwingAnalysisSessionState.activeSessionType == LAB`이어야 한다.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `:feature:lab:compileDebugKotlin` SUCCESS. `LabDrillGuideUiTest`가 `DrillSelectorBar`·`LabSessionControlHeader`를 렌더·클릭. 나머지 두 composable은 lab 컴파일 + `LabScreen` 오버레이에 포함되어 assembleDebug 성공 |
| AC-2 | PASS | `LabViewModelTest` `AC-2…`: SERVE 선택 후 세션 중 BACKHAND_TOPSPIN 무시. `LabDrillGuideUiTest` `ac1AndAc2…`: 「포핸드 플랫」 클릭 → `FOREHAND_FLAT`, 세션 중 칩 disabled |
| AC-3 | **FAIL** | Header/ViewModel/Port 단위는 통과했으나 Lab 탭이 ViewModel을 주입하지 않아 제품 경로에서 startSession이 호출되지 않음 (FAIL-1) |
| AC-4 | PASS | `LabViewModelTest` `AC-4 AC-5…`: triggerSwing 후 `faceState=SQUARE`, `coachingFeedback=훌륭한 임팩트입니다.` Spec §1.2 상태 바인딩 |
| AC-5 | PASS | `LabViewModelTest` `AC-5 fatigued or critical…`: `isFatigued=true` 및 `severity=CRITICAL`이 `uiState.latestAnomalyReport`에 노출 |
| AC-6 | PASS | lab **22/0**, app **29/0** (`LabViewModelTest` 5 + `LabDrillGuideUiTest` 2) |
| AC-7 | PASS | 선언 명령 BUILD SUCCESSFUL, 0 failures |

### Notes (not AC failures)

- Spec `LabUiState.bleConnectionState` 대신 `isSensorConnected: Boolean`을 사용. AC 목록에 없어 판정에 사용하지 않음.
- FR-7 BLE 미연결 토스트 「센서를 먼저 연결해 주세요」는 AC에 없음. 구현도 없음.
- NFR 6.1 `AnimatedVisibility` / §5 48dp 터치 타겟은 JVM에서 미검증. 실기기 확인 대상.

### Human follow-up (실기기 — FAIL-1 수정 후)

현재 Lab 탭은 시작/종료 버튼이 보여도 ViewModel이 없어 동작하지 않습니다. 주입 수정 후:

1. Lab 탭 → 카메라·근처 기기 권한 허용 → 센서 연결  
2. 드릴 칩에서 구종 선택 → **측정 시작** → 버튼이 **측정 종료**로 바뀌는지, 세션 중 칩이 비활성인지  
3. 스윙 한 번 → 하단 카드에 페이스 뱃지·5단계 체인·코칭 문구  
4. 피로/CRITICAL이면 상단 경고 배너  
5. **측정 종료** → History에 세션이 남는지  

## Verdict

**QA_FAILED** (`retry_count` 0 → 1). 드릴 선택·세션 포트·융합 결과 `uiState` 바인딩은 JVM에서 통과했으나, Lab 탭이 `LabViewModel`을 주입하지 않아 AC-3의 「측정 시작」 제품 경로가 no-op이다.

## Run 2 (spec v1) — FAIL-1 재검증

**Date:** 2026-08-14T14:30:26Z

### Boundary Check

Inspected `git diff --name-only` and `git status --short` at tester wake (`next_agent=tester`, `task_id=TASK-038`, `retry_count=1`, `status=DEV_DONE`).

| Path | Role | Verdict |
|---|---|---|
| `app/.../navigation/AppNavHost.kt` | production | OK — FAIL-1 수정: Lab 라우트에서 `hiltViewModel<LabViewModel>()`을 `LabScreen`에 전달 |
| 기타 TASK-038 production/test | 이전 사이클 | 이번 retry에서 테스트 assertion 변경 없음 |
| `docs/specs/**` | PM | 미변경 |

Developer가 테스트 파일을 수정하지 않음. 경계 위반 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:lab:cleanTest :app:cleanTestDebugUnitTest :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
# BUILD SUCCESSFUL in 6s
./gradlew :feature:lab:testDebugUnitTest --rerun-tasks
# BUILD SUCCESSFUL in 14s
```

`:feature:lab:testDebugUnitTest` — **22 tests, 0 failures** (timestamp `2026-08-14T14:29:26Z`)

| Suite | Tests | Failures |
|---|---|---|
| `LabViewModelTest` | 5 | 0 |
| 회귀 (`LabFusion*` / `Pose*`) | 17 | 0 |

`:app:testDebugUnitTest` — **29 tests, 0 failures** (timestamp `2026-08-14T14:28:55Z`)

| Suite | Tests | Failures |
|---|---|---|
| `LabDrillGuideUiTest` | 2 | 0 |
| `LabSessionPortImplTest` | 1 | 0 |
| 회귀 | 26 | 0 |

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### FAIL-1 / Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| FAIL-1 / AC-3 | PASS | Lab 라우트가 `LabViewModel`을 `LabScreen`에 전달. `LabViewModelTest` `AC-3…`: SERVE + `startSession` → `lastStartType=LAB`, `lastStartDrill=SERVE`, 종료 후 `isSessionActive=false`. `LabDrillGuideUiTest` `ac3…`: 「측정 시작」 클릭 → 「측정 종료」. `LabSessionPortImplTest`: `SessionType.LAB` + `VOLLEY`가 `SwingAnalysisSessionState`에 전달 |
| AC-1 | PASS | lab compile + `LabDrillGuideUiTest` 2/0 + assembleDebug |
| AC-2 | PASS | ViewModel SERVE 잠금 + Compose 「포핸드 플랫」 선택/세션 중 disabled |
| AC-4 | PASS | triggerSwing 후 `SQUARE` + coaching 문구 |
| AC-5 | PASS | fatigued + CRITICAL이 `uiState.latestAnomalyReport`에 노출 |
| AC-6 | PASS | lab **22/0**, app **29/0** |
| AC-7 | PASS | 선언 명령 BUILD SUCCESSFUL, 0 failures |

### Human follow-up (실기기)

Lab 탭에 ViewModel이 연결되었으므로 아래 순서로 보면 됩니다.

1. Lab 탭 → 카메라·근처 기기 권한 허용 → 센서 연결  
2. 드릴 칩에서 구종 선택 → **측정 시작** → 버튼이 **측정 종료**로 바뀌는지, 세션 중 칩이 비활성인지  
3. 스윙 한 번 → 하단 카드에 페이스 뱃지·5단계 체인·코칭 문구  
4. 피로/CRITICAL이면 상단 경고 배너  
5. **측정 종료** → History에 세션이 남는지  

## Verdict (Run 2)

**QA_PASSED** — FAIL-1(Lab 탭 ViewModel 미주입)이 해소되었고, 드릴 선택·세션 시작/종료·융합 `uiState` 바인딩 JVM 테스트가 모두 통과한다.
