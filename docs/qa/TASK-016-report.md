# TASK-016 QA Report — `:feature:history` 추출 준비 (결합 해소 리팩터링)

**Date:** 2026-08-07T05:20:37Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-016-history-decoupling.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

| Category | Verdict |
|---|---|
| `SwingHistoryRepository` → `:core:data` + CSV split (`CsvFileExporter` in `:app`) | OK (FR-1·FR-2) |
| `SwingCategoryUi` → `:core:ui` | OK (FR-3) |
| `CoachingCommentGenerator` + app impl / Hilt | OK (FR-4) |
| `HistoryScreen` / `AppNavHost` / call-site updates | OK (FR-5) |
| KinematicAnalyzer / VolleyDetector | OK — KDoc path update only (repo package move) |
| `core/data/build.gradle.kts` (`:core:model` dep) | OK (FR-1) |
| Developer-authored `SwingHistoryCsvTest` | **Accepted** — AC-7 assigns CSV JVM test to Tester; Tester adopted & strengthened (11-column assert). No assertion weakening. |
| Tester-added `CoachingCommentGeneratorImplTest` | OK (AC-11 harness) |
| `settings.gradle.kts` / root `build.gradle.kts` / Room schemas | unchanged |
| Outside `TennisDocAI/` (except docs pipeline) | none from Developer production work |

No boundary violation requiring `QA_FAILED`.

### Commands Executed

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```

- `verifyJniBindings PASSED` (4 ABIs, `EdgeImpulseNative` descriptor)
- `BUILD SUCCESSFUL`
- Unit tests: **60** total, **0** failures (≥ baseline 57)
  - `:core:data` `SwingHistoryCsvTest` (2)
  - `:app` `CoachingCommentGeneratorImplTest` (1)

### AppNavHost wiring (AC-9 / §6)

`AppNavHost` HISTORY composable supplies:

- `onNavigateToSessionDetail = { sessionId -> navController.navigate(AppRoutes.sessionDetail(sessionId)) }`
- `debugModeEnabled` from `SwingAnalysisSessionState.debugModeEnabled`

`HistoryScreen` itself has no `NavController` / `SwingAnalysisSessionState` references.

### Mutations

| AC | Action | Result |
|---|---|---|
| AC-11 | `CoachingEngine.generateComment` early `return ""` | `:app` `CoachingCommentGeneratorImplTest` **FAILED** (AssertionError); restored → full suite **PASSED** |
| AC-12 | `CSV_HEADER` → `"BROKEN,Header"` | `:core:data` `SwingHistoryCsvTest` **FAILED** (ComparisonFailure); restored → full suite **PASSED** |

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `assembleDebug` in full Gradle run |
| AC-2 | PASS | `test` 60 / 0 failures |
| AC-3 | PASS | `verifyModuleDependencies` + `verifyJniBindings PASSED` |
| AC-4 | PASS | history `.kt` FQCN scan: only `core.model`·`core.ui`·`core.data` (bad count 0) |
| AC-5 | PASS | repo under `core/data/.../repository/`; absent under `:app` |
| AC-6 | PASS | no code refs to `FileProvider`/`Uri` in `:core:data` (KDoc mention only) |
| AC-7 | PASS | `SwingHistoryCsvTest` — 11-column header + formatted event row |
| AC-8 | PASS | `SessionDetailViewModel` uses `CoachingCommentGenerator`; interface in `:core:model` |
| AC-9 | PASS | `HistoryScreen` params: callback + `debugModeEnabled`; no Nav/SessionState |
| AC-10 | PASS | `PracticeScreen` → `core.ui.accentColorForCategory`; old FQCN count 0 |
| AC-11 | PASS | mutation |
| AC-12 | PASS | mutation |
| AC-13 | PASS | no schema / settings / root build.gradle diffs |
| AC-14 | PASS | production changes under `TennisDocAI/` |

## Verdict

**QA_PASSED** — four history couplings removed; CSV/coaching contracts locked by JVM tests and mutations.

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


### Added / relocated coverage
- `SwingHistoryCsvTest` (JVM, 2) 유지
- `SwingHistoryRepositoryCsvInstrumentedTest` (androidTest): `generateCsvString` 실경로 — **컴파일 성공**, connected 미실행(adb 없음)
- `CoachingCommentGeneratorImplTest`를 `:core:analysis`로 이전(구현체가 analysis로 이동한 현재 구조에 맞춤) 후 1건 통과

### Evidence
전체 76/0. CSV **실경로 계측 실행**은 환경 제약으로 보류.

