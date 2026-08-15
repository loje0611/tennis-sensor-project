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

## Run 3 (spec v3)

**Date:** 2026-08-15T05:45:15Z  
**Spec revision:** v3 (auto-connect, 단일 측정 시작/종료, debug-only FPS)  
**Result:** **QA_PASSED**

### Boundary Check

Inspected `git diff --name-only` and `git status --short` at tester wake (`next_agent=tester`, `task_id=TASK-038`, `retry_count=0`, `status=DEV_DONE`).

| Path | Role | Verdict |
|---|---|---|
| `feature/lab/ui/LabScreen.kt`, `LabSessionControlHeader.kt`, `LabViewModel.kt`, `LabUiState.kt` | production | OK — AC-8/9/10, FR-2 |
| `feature/lab/session/LabSessionPort.kt`, `app/.../LabSessionPortImpl.kt` | production | OK — `isDebugModeEnabled`, connect/disconnect |
| `feature/lab/.../LabViewModelTest.kt` | test (Developer) | **Accepted** — spec §1.2 · AC-9 · AC-10. fake에 `isDebugModeEnabled` 추가, 미연결 `startSession`에 `connectCalled` 강화, AC-10 `uiState` 테스트 추가. assertion 약화 없음 |
| `app/.../LabSessionPortImplTest.kt` | test (Developer) | **Accepted** — spec AC-10 / §1.2 debug 토글. `isDebugModeEnabledReflectsSessionState` 추가. 기존 LAB 세션 전달 assertion 유지 |
| `app/.../LabDrillGuideUiTest.kt` | test (Tester) | Tester가 AC-8에 맞게 「센서 연결」버튼 기대를 제거하고 인디케이터+단일 측정 버튼 검증으로 교체 |
| `docs/task-board.json`, `docs/turn.json` | workflow | 보드/턴 |
| `docs/specs/**` | PM | 이번 사이클에서 Tester 미수정 |
| `.cursor/`, `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties` | leftover | TASK-038과 무관 |

경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug --rerun-tasks
# BUILD SUCCESSFUL in 41s
```

`:feature:lab:test` — **25 tests, 0 failures** (timestamp `2026-08-15T05:44:32Z`)

| Suite | Tests | Failures |
|---|---|---|
| `LabViewModelTest` | 8 | 0 |
| 회귀 (`LabFusion*` / `Pose*`) | 17 | 0 |

`:app:testDebugUnitTest` — **32 tests, 0 failures** (timestamp `2026-08-15T05:44:42Z`)

| Suite | Tests | Failures |
|---|---|---|
| `LabDrillGuideUiTest` | 4 | 0 |
| `LabSessionPortImplTest` | 2 | 0 |
| 회귀 | 26 | 0 |

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | lab `compileDebugKotlin` + `LabDrillGuideUiTest`가 `DrillSelectorBar`·`LabSessionControlHeader` 렌더. assembleDebug SUCCESS |
| AC-2 | PASS | `LabViewModelTest` `AC-2…`: 세션 중 드릴 변경 무시. `LabDrillGuideUiTest` `ac1AndAc2…`: 「포핸드 플랫」 선택 후 세션 중 칩 disabled |
| AC-3 | PASS | `LabViewModelTest` `AC-3…`: 연결 상태에서 `startSession` → `lastStartType=LAB`. `LabDrillGuideUiTest` `ac3…`: 「측정 시작」→「측정 종료」 단일 버튼 토글 |
| AC-4 | PASS | `LabViewModelTest` `AC-4 AC-5…`: `faceState=SQUARE`, coaching 문구 `uiState` 노출 |
| AC-5 | PASS | `LabViewModelTest` `AC-5 fatigued or critical…`: `isFatigued` / `CRITICAL`이 `latestAnomalyReport`에 노출 |
| AC-6 | PASS | lab **25/0**, app **32/0** (`LabViewModelTest` 8 + `LabDrillGuideUiTest` 4) |
| AC-7 | PASS | 선언 명령 BUILD SUCCESSFUL, 0 failures |
| AC-8 | PASS | `LabDrillGuideUiTest` `ac8_disconnected…`: 「센서 미연결」+「측정 시작」, 「센서 연결」노드 0개. `ac8_scanningAndConnected…`: 「센서 찾는 중...」→「센서 연결됨」, 「센서 연결」노드 0개. `LabViewModelTest` `connectSensorForwardsToSessionPort`: `connectCalled` + `isSensorScanning`. 진입 시 auto-connect LaunchedEffect는 카메라 바인딩 경로라 JVM에서 LabScreen 전체를 compose하지 않음 — 실기기 확인 |
| AC-9 | PASS | `LabViewModelTest` `startSessionIsRejectedWhenSensorDisconnected`: `startSession()==false`, `isSessionActive==false`, `lastStartType==null`, `connectCalled==true`. `LabDrillGuideUiTest` `ac8_disconnected…`: 미연결에서 「측정 시작」클릭이 `onStartSession` 호출. Toast 문자열은 LabScreen 카메라 오버레이 핸들러에 있어 ShadowToast JVM 미실행 — 실기기 확인 |
| AC-10 | PASS | `LabViewModelTest` `AC-10 isDebugModeEnabled updates uiState`: 기본 `false` → port `true` 후 `uiState.isDebugModeEnabled==true`. `LabSessionPortImplTest` `isDebugModeEnabledReflectsSessionState`: `setDebugMode(true/false)`가 port에 반영. FPS 텍스트 오버레이는 카메라 프리뷰 내부라 JVM에서 노드 미compose — 실기기에서 debug on/off로 확인 |

### Human follow-up (실기기)

1. Lab 탭 진입 → 카메라·근처 기기(BLE) 권한 허용  
2. 별도 「센서 연결」버튼 없이 상태가 **센서 찾는 중...** → **센서 연결됨**으로 바뀌는지  
3. 미연결이면 **측정 시작** 시 「센서를 먼저 연결해 주세요」토스트, 세션이 시작되지 않는지  
4. 연결 후 **측정 시작** → **측정 종료** 토글, 세션 중 드릴 칩 비활성  
5. 일반 모드에서 FPS/ms 오버레이가 없고, 개발자(debug) 모드에서만 보이는지  

## Verdict (Run 3)

**QA_PASSED** (`retry_count` 유지 0). spec v3 AC-8/9/10 JVM 증거가 선언 명령 0 failures로 통과했다. auto-connect·Toast·FPS 오버레이 픽셀은 실기기 Human follow-up.

## Run 4 (spec v4)

**Date:** 2026-08-15T06:06:33Z  
**Spec revision:** v4 (5종 드릴, 세션 중만 상주 알림 / AC-11)  
**Result:** **QA_FAILED**

### Boundary Check

Inspected commit `a829b24` (`feat: implement TASK-038 v4 spec`) plus working tree (`git status --short` clean except leftover `.cursor/` / spike gradle props).

| Path | Role | Verdict |
|---|---|---|
| `core/model/DrillType.kt`, `DrillSelectorBar.kt`, `LabUiState.kt`, `LabViewModel.kt` | production | OK — AC-2 5종 드릴 |
| `LabScreen.kt` | production | OK — 화면 이탈 시 `!isSessionActive`이면 `disconnectSensor()` |
| `LabViewModelTest.kt`, `LabDrillGuideUiTest.kt`, `LabSessionPortImplTest.kt`, fusion/data/model 테스트·golden | test (Developer) | **Accepted** — spec §1.2 / AC-2 enum 정비로 제거된 `FOREHAND_TOPSPIN` 등 식별자 치환. assertion 약화 없음 (선택/잠금/LAB 전달 계약 유지) |
| `docs/specs/**` | PM | v4 개정 (이번 사이클 Tester 미수정) |
| leftover `.cursor/`, spike gradle props | 무관 | TASK-038 범위 밖 |

경계 위반으로 `QA_FAILED`할 항목 없음. 실패는 AC-11 상주 알림 라이프사이클.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug --rerun-tasks
# BUILD FAILED in 29s — :app:testDebugUnitTest 34 tests, 2 failed
```

`:feature:lab:test` — **25 tests, 0 failures** (timestamp `2026-08-15T06:06:21Z`)

| Suite | Tests | Failures |
|---|---|---|
| `LabViewModelTest` | 8 | 0 |
| 회귀 (`LabFusion*` / `Pose*`) | 17 | 0 |

`:app:testDebugUnitTest` — **34 tests, 2 failures** (timestamp `2026-08-15T06:06:33Z`)

| Suite | Tests | Failures |
|---|---|---|
| `LabDrillGuideUiTest` | 4 | 0 |
| `LabSessionPortImplTest` | 4 | **2** |
| 회귀 | 26 | 0 |

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS (테스트 실패 전에 패키징됨).

### Failure

**FAIL-1 — AC-11: 대기/미실행 상태에서 세션 상주 알림 경로가 켜지고, 측정 시작·종료와 알림 라이프사이클이 분리되어 있다**

관측 (`LabSessionPortImplTest`):

- `ac11_connectSensorWhileIdleDoesNotStartSessionRunningForegroundService` **FAIL**  
  `connectSensor()`가 `SwingAnalysisForegroundService.ACTION_START` (`io.github.loje0611.tennisdoc.action.START_ANALYSIS`)를 보낸다. 이 액션은 서비스에서 `startForeground` + `"스윙 분석이 실행 중입니다"` 알림을 즉시 올린다. Lab 진입 auto-connect는 세션 시작 전 대기 상태이므로 AC-11 위반.
- `ac11_startSessionStartsForegroundNotificationAndFinishStopsIt` **FAIL**  
  `startSession()` 후 시작된 서비스 Intent가 `null`이다. `finishSession()`도 `ACTION_STOP`을 보내지 않는다. 알림은 측정 시작과 함께 켜지지 않고, 측정 종료와 함께 닫히지도 않는다.

Lab 화면 `onDispose`에서 `!isSessionActive`이면 `disconnectSensor()`를 호출하는 정리는 있으나, 포트의 connect/start/finish와 알림 수명이 명세와 어긋난다.

**Developer 수정 방향 (관측 가능한 계약):**

- 센서만 연결하는 `connectSensor()`는 세션 실행 알림(`ACTION_START` / `"스윙 분석이 실행 중입니다"`)을 올리지 않는다.
- `startSession()`이 그 상주 알림을 켜고, `finishSession()`이 `ACTION_STOP`(또는 동등한 즉시 해제)으로 닫는다.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | lab compile + `LabDrillGuideUiTest` 헤더/드릴 바 렌더 |
| AC-2 | PASS | `LabDrillGuideUiTest` `ac1AndAc2…`: 칩 「포핸드」「백핸드」「서브」「포발리」「백발리」표시, 구 라벨 0개, 「포발리」선택 후 세션 중 「서브」클릭이 무시. `LabViewModelTest` `AC-2…`: 세션 중 `BACKHAND` 무시 |
| AC-3 | PASS | `LabViewModelTest` `AC-3…` + `LabDrillGuideUiTest` `ac3…` 측정 시작/종료 토글 |
| AC-4 | PASS | `LabViewModelTest` `AC-4 AC-5…` `SQUARE` + coaching `uiState` |
| AC-5 | PASS | `LabViewModelTest` `AC-5 fatigued or critical…` |
| AC-6 | **FAIL** | app **34 tests, 2 failures** (`LabSessionPortImplTest` AC-11) |
| AC-7 | **FAIL** | 선언 명령 `:app:testDebugUnitTest` FAILED |
| AC-8 | PASS | `LabDrillGuideUiTest` `ac8_*` 인디케이터 + 단일 측정 버튼, 「센서 연결」노드 0 |
| AC-9 | PASS | `LabViewModelTest` `startSessionIsRejectedWhenSensorDisconnected` |
| AC-10 | PASS | `LabViewModelTest` `AC-10…` + `LabSessionPortImplTest` `isDebugModeEnabledReflectsSessionState` |
| AC-11 | **FAIL** | FAIL-1. idle `connectSensor` → `ACTION_START`. `startSession`/`finishSession`은 알림 start/stop Intent를 보내지 않음 |

## Verdict (Run 4)

**QA_FAILED** (`retry_count` 0 → 1). 5종 드릴 UI는 JVM에서 통과했으나, AC-11 상주 알림이 센서 연결과 묶여 있고 측정 시작/종료와 동기화되지 않는다.
