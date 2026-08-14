# TASK-035 QA Report — Ablation 자동 채점 검증 도구 (D-7.1)

**Date:** 2026-08-14T13:48:36Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-035-ablation-benchmark-evaluator.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected `git diff --name-only` and `git status --short` at tester wake (`next_agent=tester`, `task_id=TASK-035`).

| Path | Role | Verdict |
|---|---|---|
| `.../evaluation/AblationEvaluator.kt` | production | OK — FR-1~5 / AC-1 |
| `.../evaluation/AblationEvaluatorTest.kt` | test (Developer) | **Accepted** — spec §1.2 및 AC-2~AC-6. assertion 약화 없음. Tester가 빈 태그 엣지 케이스를 추가 |
| `docs/qa/TASK-012`–`030`, `A-B-group-gap-fill-report.md` | prior Tester | TASK-035 Developer 범위 밖 |
| `docs/task-board.json`, `docs/turn.json` | workflow | 보드/턴 상태 |
| `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties` | untracked leftover | TASK-035과 무관 |
| `docs/specs/**` | PM | 이번 사이클에서 수정 없음 |

Tester가 AC-6 `golden_ablation_dataset.json` 및 로더 테스트를 추가함. 경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :core:fusion:cleanTest :core:fusion:test verifyModuleDependencies :app:assembleDebug
# BUILD SUCCESSFUL in 2s
```

`:core:fusion:test` — **49 tests, 0 failures**

| Suite | Tests | Failures |
|---|---|---|
| `AblationEvaluatorTest` | 6 | 0 |
| `AblationEvaluatorGoldenDatasetTest` (5 JSON cases) | 1 | 0 |
| 회귀 | 42 | 0 |
| **Total** | **49** | **0** |

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `:core:fusion:compileKotlin` + `:core:fusion:test` SUCCESS. `AblationEvaluator.evaluate` / `evaluateDataset` JVM 실행 |
| AC-2 | PASS | `AC-2 Jaccard...`: fusion `{FACE_OPEN, EARLY_BODY_OPEN}` vs vision `{EARLY_BODY_OPEN}` → `DJ=0.5`, `isJaccardCriteriaMet=true` |
| AC-3 | PASS | `AC-3 identical tagsets...`: `DJ=0.0`, `isJaccardCriteriaMet=false`, `overallPass=false` |
| AC-4 | PASS | `AC-4 causal keywords...`: `hasCausalExplanation=true` |
| AC-5 | PASS | `AC-5 5-stage...`: `kineticChainStageGain=2` |
| AC-6 | PASS | `golden_ablation_dataset.json` 5케이스 `evaluateDataset` → `passRate=1.0`, `averageJaccardDistance>=0.3` |
| AC-7 | PASS | 선언 명령 BUILD SUCCESSFUL, fusion **49/0** |

### Notes (not AC failures)

- Spec §5 UI/UX는 **N/A**. 실기기 수동 테스트 대상 없음.
- 빈 태그셋은 `DJ=0`으로 크래시 없이 불합격 (FR-7 엣지).

## Verdict

**QA_PASSED** — 비전 단독 대비 융합 태그 Jaccard 거리, 인과 설명, 5단계 체인 이득이 자동 채점되고, 골든 5케이스 통과율 100%다. UI가 없어 실기기 수동 QA 항목은 없다.
