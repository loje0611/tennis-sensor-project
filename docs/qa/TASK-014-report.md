# TASK-014 QA Report — `:core:model` 신설 (SwingMetrics · SwingClassificationKeys)

**Date:** 2026-08-07T02:08:04Z  
**Target:** `TennisDocAI` (`:core:model`)  
**Spec:** `docs/specs/TASK-014-core-model-extraction.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

| Category | Verdict |
|---|---|
| Module scaffolding (`settings`, `core/model`, `allowedDeps`, `app` dep) | OK |
| Type move + call-site / analysis `import` updates | OK (FQCN→short name in ForegroundService is import cleanup) |
| `SwingClassificationKeysTest` move to `:core:model` | **Accepted** — authorized by **FR-4** |
| App analysis tests (`CoachingEngineTest` etc.) | OK — import-only |
| `JvmLibraryConventionPlugin` JVM target fix | OK — required for FR-2 `tennisdoc.jvm.library` |
| `README.md` / `AI_README.md` | OK — FR-8 |
| `SwingMetricsAvg` / Room schemas | unchanged |
| `docs/PHASE2_PLAN` etc. | not modified |

No boundary violation.

### Commands Executed

```bash
cd TennisDocAI
./gradlew projects
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```

- `:core:model` listed in `./gradlew projects`
- `BUILD SUCCESSFUL` (exit 0); `verifyJniBindings PASSED`
- Unit tests: **57** total, **0** failures (includes `:core:model` `SwingClassificationKeysTest` **4**)

### Mutations

| AC | Action | Result |
|---|---|---|
| AC-10 | `implementation(project(":core:data"))` in `core/model/build.gradle.kts` | `verifyModuleDependencies` **FAILED** (`forbidden dependency`); restored → SUCCESS |
| AC-11 | remove `.lowercase(Locale.US)` in `normalize` | `:core:model:test` **FAILED** (1 failure); restored → SUCCESS |

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `include(":core:model")` / projects output |
| AC-2 | PASS | only `tennisdoc.jvm.library`; no android block |
| AC-3 | PASS | `assembleDebug` success |
| AC-4 | PASS | 57 tests, 0 failures (≥57 baseline) |
| AC-5 | PASS | `verifyModuleDependencies` success |
| AC-6 | PASS | `":core:model" to emptySet()` present; sensor has no model |
| AC-7 | PASS | old app files absent; old FQCN strings 0 |
| AC-8 | PASS | no `android.`/`androidx.` imports in `:core:model` |
| AC-9 | PASS | 7 constant string values unchanged |
| AC-10 | PASS | mutation failed then restored |
| AC-11 | PASS | model test executed and mutation failed |
| AC-12 | PASS | changes under `TennisDocAI/` (+ allowed root `README.md`) |
| AC-13 | PASS | no `app/schemas/**` changes |

## Verdict

**QA_PASSED**
