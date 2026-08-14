# TASK-034 QA Report — 센서-비전 융합 인과 코칭 룰 엔진

**Date:** 2026-08-14T13:37:53Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-034-causal-coaching-engine.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected `git diff --name-only` and `git status --short` at tester wake (`next_agent=tester`, `task_id=TASK-034`).

| Path | Role | Verdict |
|---|---|---|
| `.../coaching/CausalCoachingEngine.kt` | production | OK — FR-2 / AC-1 |
| `.../engine/FusionEngineImpl.kt` | production | OK — FR-3 / AC-5 |
| `.../orientation/RacketImpactCalculator.kt` | production | OK — FR-1 |
| `.../coaching/CausalCoachingEngineTest.kt` | test (Developer) | **Accepted** — spec §1.2 및 AC-2~AC-6. assertion 약화 없음 |
| `.../engine/FusionEngineImplTest.kt` | test (Developer) | **Accepted** — AC-5. Tester가 빈 입력 케이스를 추가 |
| `.../orientation/RacketImpactCalculatorTest.kt` | test (Developer) | **Accepted** — FR-1 OPEN/CLOSED/SQUARE |
| `docs/qa/TASK-012`–`030`, `A-B-group-gap-fill-report.md` | prior Tester | TASK-034 Developer 범위 밖 |
| `docs/task-board.json`, `docs/turn.json` | workflow | 보드/턴 상태 |
| `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties` | untracked leftover | TASK-034과 무관 |
| `docs/specs/**` | PM | 이번 사이클에서 수정 없음 |

Tester가 AC-6 JSON 골든 픽스처를 추가함. 경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :core:fusion:cleanTest :core:fusion:test verifyModuleDependencies :app:assembleDebug
# BUILD SUCCESSFUL in 2s
```

`:core:fusion:test` — **42 tests, 0 failures**

| Suite | Tests | Failures |
|---|---|---|
| `CausalCoachingEngineTest` | 5 | 0 |
| `CausalCoachingEngineGoldenFixtureTest` (5 JSON cases) | 1 | 0 |
| `FusionEngineImplTest` | 2 | 0 |
| `RacketImpactCalculatorTest` | 3 | 0 |
| 회귀 | 31 | 0 |
| **Total** | **42** | **0** |

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `:core:fusion:compileKotlin` + `:core:fusion:test` SUCCESS. `CausalCoachingEngine.diagnose` / `FusionEngineImpl.fuse` JVM 실행 |
| AC-2 | PASS | golden `rule1_early_body_open_face_open` 및 `AC-2 early body open...`: tags `FACE_OPEN`, `EARLY_BODY_OPEN`; primaryCause 상체 조기 회전 |
| AC-3 | PASS | golden `rule2_late_contact_face_closed`: tags `FACE_CLOSED`, `LATE_CONTACT`; feedback에 「몸 앞쪽에서 공을 맞추도록」 |
| AC-4 | PASS | golden `rule4_optimal_clean_strike`: tags `CLEAN_STRIKE`, `OPTIMAL_CHAIN`, `SQUARE_FACE` |
| AC-5 | PASS | `AC-5 full pipeline fuse...`: FusedSwing에 drillType, 5-stage chain, racketImpact, diagnosis 포함. 빈 입력은 `SYNC_FAILED` |
| AC-6 | PASS | `golden_causal_coaching_fixture.json` 5케이스 (Rule 1~4 + SYNC_FAILED) 1/0 |
| AC-7 | PASS | 선언 명령 BUILD SUCCESSFUL, fusion **42/0** |

### Notes (not AC failures)

- Spec §5 UI/UX는 **N/A**. 실기기 수동 테스트 대상 없음.
- 범위의 `DRILL_TRAJECTORY_MISMATCH` 룰은 AC 목록에 없어 이번 판정에 사용하지 않음.
- Rule 2는 손목 후방 좌표를 검사하지 않고 `faceState==CLOSED`로 발화한다. AC-3 관측 계약(태그·피드백)은 충족.

## Verdict

**QA_PASSED** — 인과 코칭 4룰과 동기화 실패 폴백, `FusionEngineImpl.fuse` 파이프라인이 JVM에서 확인됨. UI가 없어 실기기 수동 QA 항목은 없다.
