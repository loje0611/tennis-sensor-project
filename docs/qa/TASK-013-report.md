# TASK-013 QA Report — JNI RegisterNatives 바인딩 전환

**Date:** 2026-08-07T01:45:32Z  
**Target:** `TennisDocAI` (`ei_jni_bridge.cpp`, `verifyJniBindings`)  
**Spec:** `docs/specs/TASK-013-jni-registernatives-binding.md` (v1)  
**Result:** **QA_PASSED** (see Run 2)

## Run 1 (spec v1)

### Boundary Check

| Path | Role | Verdict |
|---|---|---|
| `TennisDocAI/app/src/main/cpp/ei_jni_bridge.cpp` | production (In Scope) | OK |
| `TennisDocAI/build.gradle.kts` (`verifyJniBindings`) | FR-6 | OK |
| `TennisDocAI/AI_README.md` | FR-8 | OK |
| `docs/AGENT_WORKFLOW.md` | FR-8 | OK |
| `docs/PHASE2_PLAN.md` (+104/-29 planning doc rewrite) | Not authorized by FR-8; outside Developer target_project confinement | **VIOLATION** |
| Test sources | none touched by Developer | OK |

**Confirmed boundary violation** — cycle fails even though technical checks are green.

### Commands Executed

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```

Result: `BUILD SUCCESSFUL` (exit 0). `verifyJniBindings PASSED` for 4 ABIs.

Unit tests (`--rerun-tasks`): **57** tests, **0** failures.

### Mutation / negative checks

| AC | Action | Result |
|---|---|---|
| AC-7 | C++ `NATIVE_CLASS_PATH` wrong; rebuild + verify | FAILED as required; restored → PASSED |
| AC-8 | Kotlin package → `...inference.moved`; verify | FAILED as required; restored → PASSED |
| AC-9 | Remove merged + stripped `.so` trees; verify with native tasks excluded | FAILED (`missing for ABIs`); restored → PASSED |

### Per-ABI artifact spot-check

All of `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`: `JNI_OnLoad` present; no `Java_com_example_swingsenseai`; descriptor `io/github/loje0611/tennisdoc/inference/EdgeImpulseNative` present.

### Acceptance Criteria

| # | Result | Notes |
|---|---|---|
| AC-1..AC-16 | PASS (technical) | Standard command, mutations, unit tests, docs FR-8 files |
| AC-17 | **FAIL** | Unauthorized `docs/PHASE2_PLAN.md` modification |

## Required Developer Fix

1. Revert `docs/PHASE2_PLAN.md` to pre-task state (PM-owned; not in FR-8).
2. Keep JNI / `verifyJniBindings` / `AI_README.md` / `AGENT_WORKFLOW.md` changes.
3. Re-hand to Tester (`DEV_DONE`).

## Verdict

**QA_FAILED** — JNI work meets AC-1..16, but AC-17 / Developer write boundary violated by `docs/PHASE2_PLAN.md`.

## Run 2 (spec v1) — after boundary fix

**Date:** 2026-08-07T01:47:50Z  
**Result:** **QA_PASSED**

### Boundary Check

| Path | Verdict |
|---|---|
| `docs/PHASE2_PLAN.md` | **Not modified** (prior violation cleared) |
| `TennisDocAI/**` JNI + `verifyJniBindings` + `AI_README.md` | OK (In Scope / FR-6 / FR-8) |
| `docs/AGENT_WORKFLOW.md` | OK (FR-8) |
| Test sources | untouched by Developer |

No boundary violation.

### Commands Executed

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```

- `verifyJniBindings PASSED` (4 ABIs, descriptor `io/github/loje0611/tennisdoc/inference/EdgeImpulseNative`)
- `BUILD SUCCESSFUL` (exit 0)
- Unit tests: **57** / failures **0**

### Mutation re-check

| AC | Result |
|---|---|
| AC-8 | Kotlin package drift → verify **FAILED**; restored → **PASSED** |
| AC-7 / AC-9 | Implementation unchanged from Run 1; Run 1 mutation evidence still applies; current `verifyJniBindings` green confirms restored artifact state |

### Acceptance Criteria (Run 2)

AC-1..AC-16: PASS (re-executed / prior mutation evidence).  
AC-17: PASS (`PHASE2_PLAN.md` no longer in change set).

## Verdict

**QA_PASSED** — boundary cleared; JNI RegisterNatives + `verifyJniBindings` meet all ACs.

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
- `EdgeImpulseNativeJvmFallbackTest` (1): JVM에서 네이티브 미적재 시 `runClassifier`가 빈 문자열 폴백인지 고정.

### Evidence
해당 스위트 포함 전체 76/0. `verifyJniBindings` 재통과.
추론 **정확성**은 여전히 실기기 영역(계획 §4.1·§9) — 이번 보완으로도 미해소.

