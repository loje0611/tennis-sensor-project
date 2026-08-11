# TASK-021 QA Report — `:core:vision` PoseFrame + AngleCalculator 포팅

**Date:** 2026-08-11T05:32:23Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-021-core-vision-angle-calculator.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

| Category | Verdict |
|---|---|
| `core/vision` models + `AngleCalculator` | OK (FR-1~3) |
| `AngleCalculatorTest` + `golden_angles_fixture.json` | **Accepted** — FR-4 / AC-5·AC-6 mandate Tester-or-Developer golden tests; Developer authored, assertions match 1e-5 |
| `tennis-vision-analyzer/src/generate_fixture.py` | OK (FR-4 / AC-8) |
| `core/vision/build.gradle.kts` junit + org.json test deps | OK (FR-1) |
| Prior uncommitted gap-fill (`app` Robolectric, qa reports, etc.) | Outside TASK-021 Developer scope; does not alter vision ACs |
| Android/CameraX/MediaPipe in `:core:vision` | none |

No boundary violation requiring `QA_FAILED`.

### Commands Executed

```bash
cd TennisDocAI
# AC-1 note: pure JVM module has no assembleDebug task
./gradlew :core:vision:assemble :core:vision:test
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```

- `:core:vision:assembleDebug` → **task not found** (expected for `tennisdoc.jvm.library`)
- `:core:vision:assemble` + `:core:vision:test` → SUCCESS (`AngleCalculatorTest` 1 suite / 0 failures; covers all golden cases)
- `verifyJniBindings PASSED`
- Full `test` + `assembleDebug`: **BUILD SUCCESSFUL**
- Unit tests: **86** total, **0** failures

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS* | `assemble` + `test` SUCCESS; `*assembleDebug` N/A on JVM module — equivalent `assemble` executed |
| AC-2 | PASS | full `test` 86/0 |
| AC-3 | PASS | verifyModuleDependencies + verifyJniBindings PASSED |
| AC-4 | PASS | no `android.` / `androidx.` / ndk in `core/vision` sources or build.gradle.kts |
| AC-5 | PASS | `src/test/resources/golden_angles_fixture.json` present (6 cases) |
| AC-6 | PASS | `AngleCalculatorTest.testGoldenFixtures` passed (1e-5) |
| AC-7 | PASS | fixture includes NaN / zero-length / boundary cases exercised by same suite |
| AC-8 | PASS | vision sources + `generate_fixture.py` (+ docs pipeline) |

## Verdict

**QA_PASSED** — Kotlin angle calculator matches Python golden fixture within 1e-5; `:core:vision` remains pure JVM.
