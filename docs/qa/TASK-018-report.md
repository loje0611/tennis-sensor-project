# TASK-018 QA Report — `:feature:match` 이관 및 v1 내비게이션 비활성화(보존)

**Date:** 2026-08-08T06:34:37Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-018-feature-match-extraction.md` (v1)  
**Result:** **QA_PASSED** (after Run 2; Run 1 was QA_FAILED)

## Run 1 (spec v1)

### Boundary Check

| Path / category | Verdict |
|---|---|
| `feature/match/build.gradle.kts` + `feature/match/src/` (`PracticeScreen`, `MatchViewModel`, `MatchSessionPort`, drawable) | OK — Developer production (FR-1·FR-2·FR-3) |
| `app/.../ui/practice/PracticeScreen.kt` deleted · `MainViewModel.kt` deleted · `ic_neon_racket.png` moved | OK (FR-2) |
| `MatchSessionPortImpl.kt` · `AppModule` Hilt `@Binds` | OK (FR-3·FR-7) |
| `AppNavHost` / `AppRoutes` (PRACTICE/Live 제거, startDestination=HISTORY) | OK (FR-5) |
| `SettingsScreen` 10회 탭 · `SwingAnalysisSessionState.onDebugActivationAreaTap` | OK — FR-6 mandates shared activation path; session object gains tap counter only (no service/pipeline logic rewrite) |
| `AI_README.md` | OK (FR-8) |
| Test sources | **unchanged by Developer** |
| `settings.gradle.kts` / root `build.gradle.kts` / `libs.versions.toml` / schemas / `:core:*` / `:feature:history` sources | unchanged |

No boundary violation requiring `QA_FAILED` on its own.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug :feature:match:assembleDebug
```

**Result: `BUILD FAILED` at configuration of `:feature:match`** (before any assemble/test task ran).

Exact failure:

```
e: feature/match/build.gradle.kts:2:24: Unresolved reference 'tennisdoc'.
e: feature/match/build.gradle.kts:3:5: None of the following candidates is applicable: alias(...)
e: feature/match/build.gradle.kts:4:24: Unresolved reference 'ksp'.

Script compilation errors:
  Line 2: alias(libs.plugins.tennisdoc.android.library.compose)  — Unresolved reference 'tennisdoc'
  Line 3: alias(libs.plugins.hilt.android)                       — wrong / missing catalog alias
  Line 4: alias(libs.plugins.ksp)                                — Unresolved reference 'ksp'
```

Catalog reality (`gradle/libs.versions.toml`): convention plugins are applied via `id("tennisdoc.android.library.compose")` (not a version-catalog plugin alias); KSP/Hilt plugin aliases are `libs.plugins.google.ksp` and `libs.plugins.hilt.android.plugin` (as used by `:feature:history`).

Mutations AC-10/AC-11 and post-restore AC-1~3 recheck were **not executed** — configure already fails.

### Structural / code evidence gathered (does not override failed Gradle)

| Check | Observation |
|---|---|
| AC-4 files | `PracticeScreen.kt` + `MatchViewModel.kt` under `feature/match/.../feature/match/` with correct package; `app/.../ui/practice/` and `MainViewModel.kt` absent |
| AC-4 `MainViewModel` string | **Still present** in `docs/specs/**`, `docs/PRODUCT_DIRECTION.md`, `docs/qa/TASK-011-report.md`, `TennisDocAI/PROJECT_STATE_REPORT.md`. Production Kotlin under `TennisDocAI/` (excl. that report) has **0** hits. Literal AC-4 “저장소 전체 0건” is **unsatisfiable** while docs retain the historical name and Developer must not edit `docs/` (AC-17). Flag for PM if/when build is fixed. |
| AC-5 | `ic_neon_racket.png` only under `feature/match/src/main/res/`; no `io.github.loje0611.tennisdoc.R` in `feature/match/src/` |
| AC-6 deps (script text) | project deps `:core:model`·`:core:ui`·`:core:sensor` only; FQCN scan of `feature/match/src/` shows only allowed prefixes |
| AC-9 | `AppRoutes` has no `PRACTICE`; `startDestination = AppRoutes.HISTORY`; no `PracticeScreen` / `"Live"` in navigation sources |
| AC-12 | Settings title `"Settings"` is always visible and `.clickable { SwingAnalysisSessionState.onDebugActivationAreaTap() }`; threshold `10` lives only in `SwingAnalysisSessionState.onDebugActivationAreaTap`; Match path delegates via `MatchSessionPortImpl` → same method; already-on early return present |
| AC-13 | `MatchSessionPortImpl.simulateSwing`: debug off → return; pipeline running → `requestDebugSimulation`; else → `updateSwingLabel` |

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | **FAIL** | `assembleDebug` did not run — configure error in `feature/match/build.gradle.kts` (output above) |
| AC-2 | **not verified** | `test` never started |
| AC-3 | **not verified** | `verifyModuleDependencies` / `verifyJniBindings` never started |
| AC-4 | **FAIL** (literal) / partial structural OK | files/packages OK; `MainViewModel` still in docs/report — literal whole-repo 0건 unmet (see note; possible spec overscope) |
| AC-5 | PASS (structure) | resource location + no app `R` FQCN |
| AC-6 | **not verified** (build) | script/FQCN scan looks OK but module configure fails so independence not proven by `verifyModuleDependencies` |
| AC-7 | **FAIL** | `:feature:match:assembleDebug` failed at configure |
| AC-8 | **not verified** | no generated/ output from a successful KSP run |
| AC-9 | PASS (code) | AppRoutes / AppNavHost inspection |
| AC-10 | **not verified** | mutation blocked by configure failure |
| AC-11 | **not verified** | mutation blocked by configure failure |
| AC-12 | PASS (code) | Settings title gesture + shared `SwingAnalysisSessionState` threshold |
| AC-13 | PASS (code) | `MatchSessionPortImpl.simulateSwing` 3-way branch |
| AC-14 | PASS | no diff on settings / root allowed map |
| AC-15 | PASS | no diff on `libs.versions.toml` |
| AC-16 | PASS | no schema / `:core:*` / `:feature:history` source diffs |
| AC-17 | PASS | Developer production under `TennisDocAI/`; only agent `docs/task-board.json` + `docs/turn.json` |

### Developer action required

1. Fix `feature/match/build.gradle.kts` plugin application to match the working `:feature:history` pattern, e.g. `id("tennisdoc.android.library.compose")` + `alias(libs.plugins.google.ksp)` + `alias(libs.plugins.hilt.android.plugin)` — **without** inventing new catalog aliases (FR-1 / AC-15).
2. Re-run full suite until AC-1~3, AC-7, AC-8 pass; Tester will then execute AC-10/AC-11 mutations.
3. Optional (PM): clarify AC-4 “저장소 전체” so historical docs mentions of `MainViewModel` do not force an impossible bar.

## Verdict

**QA_FAILED** — `:feature:match` Gradle script does not configure; declared verification commands cannot complete. `retry_count` incremented to **1**. Handoff → Developer.


---

## Run 2 (spec v1)

**Date:** 2026-08-08T07:26:57Z  
**Result:** **QA_PASSED**

### Boundary Check

| Path / category | Verdict |
|---|---|
| Prior Run-1 paths still in place + `feature/match/build.gradle.kts` plugin fix | OK |
| `PROJECT_STATE_REPORT.md` updated (MainViewModel row removed) | OK — under `TennisDocAI/` |
| Test sources | unchanged by Developer |
| `settings` / root map / catalog / schemas / `:core:*` / `:feature:history` sources | unchanged |
| `docs/specs/**` | unchanged |

No boundary violation.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug :feature:match:assembleDebug
# mutations AC-10 / AC-11 (restored)
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug   # post-restore
```

- `verifyJniBindings PASSED` (4 ABIs)
- Full suite + `:feature:match:assembleDebug`: **BUILD SUCCESSFUL**
- Unit tests: **60** / 0 failures
- Post-restore recheck: **BUILD SUCCESSFUL**

### Hilt generation (AC-8)

`feature/match/build/generated/ksp/debug/java/.../MatchViewModel_Factory.java`, `MatchViewModel_HiltModules.java`, aggregated deps present.

### Mutations

| AC | Action | Result |
|---|---|---|
| AC-10 | remove `bindMatchSessionPort` from `AppModule` | `:app:assembleDebug` **FAILED** at `:app:hiltJavaCompileDebug` with `[Dagger/MissingBinding] MatchSessionPort`; restored → suite **PASSED**. (Note: `:app:compileDebugKotlin` alone is insufficient — Hilt graph compile is required.) |
| AC-11a | temporarily add `implementation(project(":core:data"))` | `verifyModuleDependencies` **PASSED** (allowed set); removed |
| AC-11b | temporarily add `implementation(project(":feature:history"))` | `verifyModuleDependencies` **FAILED**: forbidden dependency on `:feature:history`; restored → suite **PASSED** |

### AC-12 / AC-13 (code)

- **AC-12**: Settings title `"Settings"` always visible; `.clickable { SwingAnalysisSessionState.onDebugActivationAreaTap() }`. Threshold `10` only in `SwingAnalysisSessionState.onDebugActivationAreaTap` (early return if already enabled). Match path: `MatchViewModel` → port → same session method.
- **AC-13**: `MatchSessionPortImpl.simulateSwing` — debug off → return; pipeline running → `requestDebugSimulation`; else → `updateSwingLabel`.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `assembleDebug` SUCCESS |
| AC-2 | PASS | tests **60** / 0 failures |
| AC-3 | PASS | verifyModuleDependencies + verifyJniBindings PASSED |
| AC-4 | PASS | files/packages OK; `app` practice + `MainViewModel.kt` absent; `MainViewModel` **0** under `TennisDocAI/` (excl. build). Historical mentions remain only in `docs/**` (outside Developer AC-17 scope; not production identifiers) |
| AC-5 | PASS | drawable only under `feature/match`; no app `R` FQCN |
| AC-6 | PASS | project deps model/ui/sensor only; forbidden FQCN prefixes 0 |
| AC-7 | PASS | `:feature:match:assembleDebug` SUCCESS |
| AC-8 | PASS | MatchViewModel Hilt KSP outputs present |
| AC-9 | PASS | no PRACTICE/Live/PracticeScreen in nav; `startDestination = HISTORY` |
| AC-10 | PASS | mutation (assembleDebug / MissingBinding) |
| AC-11 | PASS | mutation (data allowed, history forbidden) |
| AC-12 | PASS | Settings title gesture + shared threshold |
| AC-13 | PASS | simulateSwing 3-way branch |
| AC-14 | PASS | no settings/root map diff |
| AC-15 | PASS | no catalog diff |
| AC-16 | PASS | no schema / core / history source diffs |
| AC-17 | PASS | Developer production under `TennisDocAI/` |

## Verdict

**QA_PASSED** — `:feature:match` configures and builds independently; nav deactivation + debug activation preservation + Hilt binding enforcement verified.

---

## Run 3 (A-group test gap fill — supplemental)

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


### Added coverage
- `MatchViewModelTest` (5): 포트 위임
- `SwingAnalysisSessionStateDebugTest` (3): 10회 탭 활성화 / 조기 return
- `AppRoutesMatchDeactivationTest` (3): PRACTICE/LIVE 상수 부재, sessionDetail 포맷
- `MatchSessionPortImplSimulateInstrumentedTest` (androidTest, 3갈래): **컴파일 성공**, connected 미실행

### Evidence
JVM 관련 신규 11건 포함 전체 76/0. simulateSwing 파이프라인 ON 분기의 기기 실행은 보류.

---

## Run supplemental (A-group gap #2 — Compose/nav smoke)

**Date:** 2026-08-11T05:00:18Z  
**Result:** **QA_PASSED** (supplemental; original verdict unchanged)

### Added
- Robolectric + Compose UI Test (`:app` JVM)
- `HistoryNavigationSmokeTest` (3): History→SessionDetail 클릭 이동, Back 복귀, `debugModeEnabled` Mock FAB 표시

### Commands
```bash
cd TennisDocAI
./gradlew :app:testDebugUnitTest --tests 'io.github.loje0611.tennisdoc.navigation.HistoryNavigationSmokeTest'
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```
- Smoke 3/0, full suite **85**/0, BUILD SUCCESSFUL

### Note
Uses the same `AppRoutes` + callback wiring pattern as `AppNavHost` (Hilt 없는 스모크). Full `AppNavHost`+Hilt Activity 계측은 기기/`adb` Linux 바이너리 부재로 미실행.

