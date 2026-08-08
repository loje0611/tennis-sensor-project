# TASK-017 QA Report — `:feature:history` 모듈 신설 및 이력 화면 이관

**Date:** 2026-08-08T05:37:46Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-017-feature-history-module.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

| Path / category | Verdict |
|---|---|
| `feature/history/build.gradle.kts` + `feature/history/src/main/...` (6 files) | OK — Developer production (FR-1·FR-2) |
| `app/.../ui/history/*` deleted | OK — migration away from `:app` (FR-2 / AC-4) |
| `app/build.gradle.kts` (`:feature:history` dep) | OK (FR-6) |
| `AppNavHost.kt` import/wiring updates | OK (FR-4·FR-6) |
| `AI_README.md` module blurb | OK (FR-8) |
| Test sources (`**/src/test/**`, `*Test*`) | **unchanged by Developer** — no boundary violation |
| `settings.gradle.kts` / root `build.gradle.kts` / `libs.versions.toml` / `**/schemas/**` | unchanged |
| `docs/specs/**` | unchanged; only agent `docs/task-board.json` + `docs/turn.json` |

No boundary violation requiring `QA_FAILED`.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug :feature:history:assembleDebug
# after AC-8/AC-9 restore:
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```

- `verifyJniBindings PASSED` (4 ABIs, `EdgeImpulseNative` descriptor)
- `BUILD SUCCESSFUL` (full suite ~4m15s; post-restore recheck SUCCESS)
- Unit tests: **60** total, **0** failures (≥ baseline 60)

### Hilt generation (AC-7)

Under `feature/history/build/generated/ksp/debug/java/`:

- `.../HistoryViewModel_Factory.java`, `HistoryViewModel_HiltModules.java`, aggregated deps
- `.../SessionDetailViewModel_Factory.java`, `SessionDetailViewModel_HiltModules.java`, aggregated deps

### AppNavHost wiring (AC-10)

`AppNavHost` HISTORY / SESSION_DETAIL composables:

1. `onNavigateToSessionDetail = { sessionId -> navController.navigate(AppRoutes.sessionDetail(sessionId)) }`
2. `debugModeEnabled` from `SwingAnalysisSessionState.debugModeEnabled.collectAsStateWithLifecycle()`
3. `SessionDetailScreen(onBack = { navController.popBackStack() }, ...)`
4. FR-4 **(b)**: `val sessionDetailViewModel: SessionDetailViewModel = hiltViewModel()` inside the `SESSION_DETAIL` composable (nav entry scope), passed as `viewModel=`; `SessionDetailViewModel` still reads `savedStateHandle["sessionId"]`

### Mutations

| AC | Action | Result |
|---|---|---|
| AC-8 | temporarily add `implementation(project(":core:analysis"))` to `feature/history/build.gradle.kts` | `verifyModuleDependencies` **FAILED**: `Module :feature:history has forbidden dependency on :core:analysis`; restored → suite **PASSED** |
| AC-9 | temporarily remove `debugModeEnabled = debugModeEnabled` from `HistoryScreen(...)` call | `:app:compileDebugKotlin` **FAILED**: `No value passed for parameter 'debugModeEnabled'`; restored → suite **PASSED** |

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `assembleDebug` in full Gradle run — `BUILD SUCCESSFUL` |
| AC-2 | PASS | `test` XML aggregate **60** / 0 failures |
| AC-3 | PASS | `verifyModuleDependencies` + `verifyJniBindings PASSED` |
| AC-4 | PASS | 6 files under `feature/history/src/main/.../feature/history/` with package `io.github.loje0611.tennisdoc.feature.history`; `app/.../ui/history/` **absent** |
| AC-5 | PASS | project deps exactly `:core:model`, `:core:ui`, `:core:data`; FQCN scan: only `core.model`·`core.ui`·`core.data`·`feature.history` (forbidden prefixes 0) |
| AC-6 | PASS | `./gradlew :feature:history:assembleDebug` succeeded in same run |
| AC-7 | PASS | KSP/Hilt generated `HistoryViewModel_*` and `SessionDetailViewModel_*` under `feature/history/build/generated/` |
| AC-8 | PASS | mutation (see above) |
| AC-9 | PASS | mutation (see above) |
| AC-10 | PASS | AppNavHost wiring inspection (see above) |
| AC-11 | PASS | no diff on `settings.gradle.kts` or root `build.gradle.kts` allowed-deps map |
| AC-12 | PASS | no diff on `gradle/libs.versions.toml` |
| AC-13 | PASS | no `**/schemas/**` diff |
| AC-14 | PASS | Developer production changes confined to `TennisDocAI/` |

## Verdict

**QA_PASSED** — `:feature:history` hosts the six history screens with Hilt codegen proven, module isolation enforced by `verifyModuleDependencies` (including mutation), and `:app` wiring preserved.
