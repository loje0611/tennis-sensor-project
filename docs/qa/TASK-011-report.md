# TASK-011 QA Report — `:core:sensor` 모듈 추출 (BLE · IMU 파싱)

**Date:** 2026-08-07T00:31:48Z  
**Target:** `TennisDocAI` (`:core:sensor`, `:app` wiring)  
**Spec:** `docs/specs/TASK-011-core-sensor-extraction.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Paths from `git status --short` / `git diff --name-only` / untracked:

| Category | Paths | Verdict |
|---|---|---|
| Production move/wiring (Developer OK) | Deleted app BLE/sensor sources; new `core/sensor/src/main/**`; `app`/`core/sensor` `build.gradle.kts`; call-site import updates | OK |
| Call sites FR-7 | `MainViewModel`, `SwingAnalysisForegroundService`, `SwingAnalysisSessionState`, `PracticeScreen` | OK — import-only diffs |
| Test: move `ImuPayloadParserTest` app → `:core:sensor` | Authorized by **FR-10** | Accepted |
| Test: add `ImuFrameSpecConsistencyTest` in `:app` | Authorized by **FR-4 / AC-8** | Accepted |
| Tester update | Extended consistency test with WINDOW/FLAT assertions for **AC-10** | Tester write permission |
| Spec | `docs/specs/TASK-011-*.md` | PM |

No boundary violation.

### Command Executed

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies test assembleDebug
```

**Result:** `BUILD SUCCESSFUL` (exit 0)

Additional evidence runs:
- `./gradlew :core:sensor:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks` → SUCCESS
- AC-9 mutation: temporarily set `EdgeImpulseInputSpec.AXES_PER_SAMPLE = 7` → `ImuFrameSpecConsistencyTest` **2 failed**; restored to `6` → SUCCESS; working tree clean on that file

### Unit Test Evidence

| Suite | Tests | Failures | Errors |
|---|---:|---:|---:|
| `:core:sensor` `ImuPayloadParserTest` | 7 | 0 | 0 |
| `:app` `ImuFrameSpecConsistencyTest` | 2 | 0 | 0 |
| Remaining `:app` unit tests | 42 | 0 | 0 |

FR-5 coverage in `ImuPayloadParserTest`: valid 6-axis; spaces; empty/blank; `ERR:`/`err:`; wrong axis count; non-numeric; negatives/zero.

### Acceptance Criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| AC-1 | Full Gradle command green | PASS | `BUILD SUCCESSFUL`, exit 0 |
| AC-2 | Required types in `:core:sensor` artifact | PASS | `classes.jar` contains `BleManager`, `BleConnectionState`, `SensorDataSource`, `RealBleDataSource`, `MockBleDataSource`, `ImuPayloadParser` (+ `ImuFrameSpec`) |
| AC-3 | No project module deps from `:core:sensor` | PASS | `verifyModuleDependencies` green; `build.gradle.kts` has no `project(` |
| AC-4 | No `analysis` import in `:core:sensor` | PASS | `rg analysis` → none |
| AC-5 | Six types absent from `:app` | PASS | Former paths absent |
| AC-6 | `MockSwingDataGenerator` unchanged in `:app` | PASS | File present; `git diff` empty |
| AC-7 | FR-5 parser tests in `:core:sensor` | PASS | 7/7 tests in module report |
| AC-8 | Axes consistency test | PASS | `ImuFrameSpecConsistencyTest` compares both constants |
| AC-9 | Consistency test fails on mismatch | PASS | Mutation to 7 failed both tests; restore passed |
| AC-10 | WINDOW=40, AXES=6, FLAT=240 | PASS | Executed assertions in consistency test |
| AC-11 | Merged manifest retains BLE perms | PASS | Merged debug manifest has `BLUETOOTH_SCAN/CONNECT`, `ACCESS_FINE_LOCATION`, `BLUETOOTH`, `BLUETOOTH_ADMIN`, `bluetooth_le` |
| AC-12 | No duplicate parser tests in `:app` | PASS | `app/.../sensor/` only has `ImuFrameSpecConsistencyTest.kt` |
| AC-13 | App unit tests pass | PASS | App suite 0 failures |
| AC-14 | BleConnectionState 4 states + 5 ErrorReasons | PASS | Types present in compiled classes / source names match |
| AC-15 | Out-of-scope projects untouched | PASS | Changes confined to `TennisDocAI/` + `docs/specs`/`docs/qa`/`task-board`/`turn` |

## Verdict

**QA_PASSED** — declared command completed; every AC has executed evidence.

---

## Run 2 (A-group test gap fill — supplemental)

**Date:** 2026-08-11T04:33:43Z  
**Result:** **QA_PASSED** (supplemental retest; original verdict unchanged)  
**Note:** Tester-only test additions + re-execution. Production sources untouched.

### Commands Executed

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
# androidTest 소스 컴파일만 (adb/기기 없음 → connected 미실행)
./gradlew :core:data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin
```

- `verifyJniBindings PASSED` (4 ABIs)
- `BUILD SUCCESSFUL`
- Unit tests: **76** total, **0** failures (이전 기준선 60 → +16)


### Scope of this retest
`ImuPayloadParserTest` 7건 재실행 통과. `:core:sensor` 추출 회귀 없음.

