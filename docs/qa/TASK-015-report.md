# TASK-015 QA Report — `:core:analysis` 추출 (Kinematic · Coaching · Edge Impulse NDK)

**Date:** 2026-08-07T04:07:25Z  
**Target:** `TennisDocAI` (`:core:analysis`)  
**Spec:** `docs/specs/TASK-015-core-analysis-extraction.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

| Category | Verdict |
|---|---|
| Analysis 7 files + `EdgeImpulseNative` + `cpp/` move | OK (In Scope) |
| Test move (4 suites) to `:core:analysis` | **Accepted** — authorized by §3.1 |
| Call-site / KDoc import updates | OK |
| `ImuFrameSpecConsistencyTest` import-only | OK (FR-6) |
| ProGuard / `consumer-rules.pro` | OK (FR-7) |
| `AI_README.md` | OK (FR-9); root `README.md` already lists `:core:analysis` |
| `:core:data/model/sensor/ui` / Room schemas | unchanged |
| `edge_impulse/` content | **0** content modifications; rename path only |

No boundary violation.

### Commands Executed

```bash
cd TennisDocAI
./gradlew projects
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```

- `:core:analysis` present in projects
- `BUILD SUCCESSFUL`; `verifyJniBindings PASSED` with descriptor  
  `io/github/loje0611/tennisdoc/core/analysis/inference/EdgeImpulseNative`
- Unit tests: **57** total, **0** failures
- `:core:analysis` suites executed: CoachingEngine(12), KinematicAnalyzer(7), SwingInferenceBuffer(7), VolleyDetector(11) = **37**

### AC-4 APK packaging

`app/build/outputs/apk/debug/app-debug.apk` contains:
- `lib/arm64-v8a/libswingsense_ei.so`
- `lib/armeabi-v7a/libswingsense_ei.so`
- `lib/x86/libswingsense_ei.so`
- `lib/x86_64/libswingsense_ei.so`

### Mutations

| AC | Action | Result |
|---|---|---|
| AC-9 | `NATIVE_CLASS_PATH` → old `.../tennisdoc/inference/...`; native rebuild | verify **FAILED** (missing expected descriptor); restored → **PASSED** |
| AC-10 | remove `x86` from merged+stripped lib trees | verify **FAILED** (`missing for ABIs: [x86]`); restored → **PASSED** |
| AC-11 | `CoachingEngine.generateComment` early `return ""` | `:core:analysis` CoachingEngineTest **11 failed**; restored → **PASSED** |

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | projects lists `:core:analysis` |
| AC-2 | PASS | cmake/ABI in `:core:analysis`; removed from `:app` |
| AC-3 | PASS | assembleDebug success |
| AC-4 | PASS | 4 ABI `.so` in APK |
| AC-5 | PASS | 57 tests; 4 analysis suites in `:core:analysis` reports |
| AC-6 | PASS | verifyModuleDependencies |
| AC-7 | PASS | verifyJniBindings + new descriptor |
| AC-8 | PASS | keep FQCN matches; old `tennisdoc.inference` string count 0 |
| AC-9 | PASS | mutation |
| AC-10 | PASS | mutation |
| AC-11 | PASS | mutation |
| AC-12 | PASS | ~1395 renames (`git status` `R` / `git diff --summary rename`) |
| AC-13 | PASS | no content diffs under `edge_impulse/` |
| AC-14 | PASS | app `cpp/` / `analysis/` / `inference/` absent |
| AC-15 | PASS | other core modules / schemas untouched |
| AC-16 | PASS | changes under `TennisDocAI/` (+ allowed docs pipeline files) |

## Verdict

**QA_PASSED** — NDK move, JNI descriptor sync, packaging, and safety-net mutations all verified.
