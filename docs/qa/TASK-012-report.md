# TASK-012 QA Report — `:core:data` 모듈 추출 (Room · DataStore)

**Date:** 2026-08-07T01:06:02Z  
**Target:** `TennisDocAI` (`:core:data`, `:app` wiring)  
**Spec:** `docs/specs/TASK-012-core-data-extraction.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

| Category | Paths | Verdict |
|---|---|---|
| Production move/wiring | Room/DataStore sources → `core/data/src/main/**`; `build.gradle.kts`; proguard keep rules for new packages; call-site imports | OK |
| Call sites / `SwingHistoryRepository` | import-only diffs (FR-7/FR-8) | OK |
| `SwingSessionDaoTest` move to `:core:data` androidTest | Authorized by **FR-11** | Accepted |
| Spec | `docs/specs/TASK-012-*.md` | PM |

No boundary violation. Tester did not alter production sources.

### Commands Executed

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies test assembleDebug :core:data:assembleDebugAndroidTest
```

**Result:** `BUILD SUCCESSFUL` (exit 0)

Evidence refresh:
```bash
./gradlew :core:data:testDebugUnitTest :core:sensor:testDebugUnitTest :core:ui:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```
→ `BUILD SUCCESSFUL`; **57** unit tests, **0** failures.

### Unit Test Evidence

| Module suite | Tests | Failures |
|---|---:|---:|
| `:app` (incl. `ImuFrameSpecConsistencyTest`) | 44 | 0 |
| `:core:ui` | 6 | 0 |
| `:core:sensor` | 7 | 0 |
| `:core:data` unit | 0 (no unit tests required) | — |
| **Total executed** | **57** | **0** |

### Schema / Artifact Evidence

- Exported v7 JSON: `core/data/schemas/io.github.loje0611.tennisdoc.core.data.db.TennisDocDatabase/7.json`
- `identityHash` = `c8e201a871aaf3813dd535f4f0e6eefb`, `version` = `7`
- Tables: `swing_sessions`, `session_swing_counts`, `swing_events`, `global_statistics`
- `swing_events` fields include `rawMaxAccel`, `rawDurationMs`, `rawGyroFollow`
- Legacy schema JSONs under `app/schemas/**` still present (AC-10)
- `classes.jar` contains all 11 public types; string `swingsense.db` present
- Migration classes `MIGRATION_5_6` / `MIGRATION_6_7` present in jar; ALTER TABLE SQL for raw* columns present
- androidTest APK: `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | Full command exit 0 |
| AC-2 | PASS | 11 types in `classes.jar` |
| AC-3 | PASS | `verifyModuleDependencies` + no `project(` in `core/data/build.gradle.kts` |
| AC-4 | PASS | no `analysis` imports under `:core:data` |
| AC-5 | PASS | 11 former app paths absent |
| AC-6 | PASS | `SwingHistoryRepository` remains; import-only diff |
| AC-7 | PASS | v7 schema JSON exported under `core/data/schemas/...` |
| AC-8 | PASS | identityHash exact match |
| AC-9 | PASS | table set exact match |
| AC-10 | PASS | legacy `app/schemas/**` files still tracked/present |
| AC-11 | PASS | `swingsense.db` in jar strings |
| AC-12 | PASS | raw* columns in schema JSON |
| AC-13 | PASS | `MIGRATION_5_6`/`MIGRATION_6_7` classes + migration SQL in artifact |
| AC-14 | PASS | DataStore string literals identical to HEAD (CalibrationStore/ThemePreferences) |
| AC-15 | PASS | `:core:data:assembleDebugAndroidTest` success; app androidTest copy absent |
| AC-16 | PASS | 57 unit tests, 0 failures (includes TASK-011 consistency test) |
| AC-17 | PASS | Hilt generate + `assembleDebug` success |
| AC-18 | PASS | changes confined to TennisDocAI + docs specs/qa/board/turn |

## Verdict

**QA_PASSED** — declared commands completed; every AC has executed/artifact evidence.

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
Room DAO 계측 테스트는 기기 부재로 미실행. JVM 스위트 및 `:core:data` CSV 계약 테스트(2)는 통과.
추가된 `SwingHistoryRepositoryCsvInstrumentedTest`는 **컴파일 성공**, `connectedDebugAndroidTest`는 adb 없어 미실행.

