# TASK-039 QA Report — 동기 리플레이 및 정밀 진단 뷰어

**Date:** 2026-08-15T07:12:16Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-039-synchronized-replay-diagnostic-viewer.md` (v1)  
**Result:** **QA_FAILED**

## Run 1 (spec v1)

### Boundary Check

Inspected commit `22e3fcf` (`feat(lab): implement TASK-039 synchronized replay and diagnostic viewer`) and `git status --short` (clean except leftover `.cursor/` / spike gradle props).

| Path | Role | Verdict |
|---|---|---|
| `feature/lab/replay/*.kt` (8 production files) | production | OK — FR-1~5 / AC-1 범위 |
| `LabReplayViewModelTest.kt`, `TooltipPlacementCalculatorTest.kt` | test (Developer) | **Accepted** — spec §1.2가 `LabReplayViewModelTest` 및 툴팁 불변식 단위 테스트를 명시. assertion 약화 없음. Tester가 AC-2 IMU 타임스탬프 락과 INV-1 `MIN_GAP` 검사를 강화 |
| `docs/task-board.json`, `docs/turn.json` | workflow | 보드/턴 |
| leftover `.cursor/`, spike gradle props | 무관 | TASK-039 범위 밖 |

경계 위반으로 `QA_FAILED`할 항목 없음. 실패는 AC-2 동기 락.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug --rerun-tasks
# BUILD FAILED in 13s — :feature:lab:testDebugUnitTest 35 tests, 1 failed
```

`:feature:lab:test` — **35 tests, 1 failure** (timestamp `2026-08-15T07:12:16Z`)

| Suite | Tests | Failures |
|---|---|---|
| `LabReplayViewModelTest` | 6 | **1** |
| `TooltipPlacementCalculatorTest` | 4 | 0 |
| `LabViewModelTest` | 8 | 0 |
| 회귀 (`LabFusion*` / `Pose*`) | 17 | 0 |

`:app:testDebugUnitTest` / `assembleDebug` — lab 실패로 **미실행**.

### Failure

**FAIL-1 — AC-2: 시크 시 IMU 샘플이 `t_current + timeOffsetMs`에 락되지 않는다**

`LabReplayViewModelTest.seekTo_updatesCurrentPoseAndImuWithSyncAnchorOffset`:

- 비전 임팩트 `t_current = 495ms`, `timeOffsetMs = 1005` → 기대 IMU timestamp **1500ms** (센서 임팩트).
- 실제 `currentImuPoint.timestampMs` = **1980ms** (IMU 시계열 마지막 샘플).

관측 가능한 원인: `seekTo`가 `findNearestImu(samples, clampedTs + timeOffset)`로 이미 보정된 시각을 넘긴 뒤, `findNearestImu`가 `imuSamples.first().timestampMs + targetTs`를 다시 더해 목표가 2500ms가 되고 끝 샘플로 클램핑된다.

**Developer 수정 방향 (관측 가능한 계약):** `seekTo(visionImpact)` 후 `currentImuPoint.timestampMs`가 `sensorImpactTimestampMs`(이 픽스처에서는 1500)와 IMU 주기(20ms) 이내로 일치해야 한다. IMU 절대 타임스탬프에서 `t_current + timeOffsetMs`를 한 번만 적용해 최근접 샘플을 고른다.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `:feature:lab:compileDebugKotlin` SUCCESS. replay 패키지 5 컴포저블 컴파일 |
| AC-2 | **FAIL** | FAIL-1. `seekTo(495)` IMU timestamp expected 1500 actual 1980 |
| AC-3 | PASS* | `jumpToImpact_seeksDirectlyToImpactTimestamp`: `currentTimestampMs=495`, `isImpactFrame=true`. IMPACT! 뱃지 Compose는 lab 실패로 app 스위트 미실행 |
| AC-4 | PASS | `stepForwardAndStepBackward…` ±33ms 및 0 클램프. `playbackSpeed_canBeToggled` 0.5/1.0. `togglePlay_startsAndPausesPlayback` |
| AC-5 | PASS | `TooltipPlacementCalculatorTest` 4/0. 다중 툴팁 INV-1(겹침 0 + MIN_GAP) · INV-2 경계 · 결정성 |
| AC-6 | not verified | `KineticChainSummaryCard` Compose 테스트는 `:app:testDebugUnitTest`가 실행되지 않아 미실행 |
| AC-7 | **FAIL** | lab **35 tests, 1 failure** |
| AC-8 | **FAIL** | 선언 명령 `:feature:lab:test` FAILED |

### Notes (not AC failures)

- `LabReplayScreen`이 `AppNavHost`에 연결되어 있지 않다. 명세 AC에 내비게이션 항목은 없으나, 실기기에서 리플레이 화면에 들어가는 경로가 없다.

## Verdict

**QA_FAILED** (`retry_count` 0 → 1). 툴팁 불변식·재생 스텝은 통과했으나, 시크 시 IMU가 `SyncAnchor.timeOffsetMs`로 락되지 않는다 (AC-2).

## Run 2 (spec v1) — FAIL-1 재검증

**Date:** 2026-08-15T07:15:28Z  
**Result:** **QA_PASSED**

### Boundary Check

Inspected commit `01efbf4` (`fix(lab): correct IMU timestamp seek locking with SyncAnchor offset`).

| Path | Role | Verdict |
|---|---|---|
| `LabReplayViewModel.kt`, `ImuWaveformChart.kt` | production | OK — FAIL-1: IMU 절대 시각으로 최근접 샘플, 차트 커서는 `t + offset - baseTime` |
| `app/build.gradle.kts` | production/test deps | OK — `LabReplayUiTest`용 `:core:vision` / `:core:fusion` testImplementation |
| `LabReplayViewModelTest.kt`, `TooltipPlacementCalculatorTest.kt`, `LabReplayUiTest.kt` | test | Tester Run 1 강화분 포함. assertion 약화 없음 (`1500L` 유지) |

경계 위반 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug --rerun-tasks
# BUILD SUCCESSFUL in 29s
```

`:feature:lab:test` — **35 tests, 0 failures** (timestamp `2026-08-15T07:15:28Z`)  
`:app:testDebugUnitTest` — **37 tests, 0 failures** (timestamp `2026-08-15T07:15:40Z`) including `LabReplayUiTest` 3/0.

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### FAIL-1 / Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| FAIL-1 / AC-2 | PASS | `LabReplayViewModelTest` `seekTo_updatesCurrentPoseAndImuWithSyncAnchorOffset`: `seekTo(495)` → IMU `timestampMs=1500` |
| AC-1 | PASS | lab compile + replay 컴포저블 |
| AC-3 | PASS | VM `jumpToImpact` 495ms + `isImpactFrame`. `LabReplayUiTest` `ac3AndAc4…` 「🎯 임팩트 점프」클릭. `ac3_impactBadge…` 「⚡ IMPACT!」표시 |
| AC-4 | PASS | VM ±33ms / 배속 / play-pause. UI 「재생」→「정지」, 「1.0x」→「0.5x」, ◀▶ 스텝 |
| AC-5 | PASS | `TooltipPlacementCalculatorTest` 4/0 |
| AC-6 | PASS | `LabReplayUiTest` `ac6_kineticChainCard…`: 골반~임팩트, Face SQUARE, 효율 92%, 코칭 문구 |
| AC-7 | PASS | lab **35/0**, app **37/0** (`LabReplayViewModelTest` 6 + `LabReplayUiTest` 3) |
| AC-8 | PASS | 선언 명령 BUILD SUCCESSFUL, 0 failures |

### Human follow-up (실기기)

`LabReplayScreen`은 아직 `AppNavHost`에 없어 앱에서 리플레이 화면으로 들어가는 경로가 없습니다. JVM 컴포저블은 통과했습니다.

## Verdict (Run 2)

**QA_PASSED** (`retry_count` 유지 1). FAIL-1 IMU 동기 락이 해소되었고 선언 명령 0 failures.
