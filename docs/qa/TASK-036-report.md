# TASK-036 QA Report — 개인 Baseline 통계적 이상 탐지 및 피로도 (D-7.4)

**Date:** 2026-08-14T13:55:57Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-036-personal-baseline-anomaly-detection.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected `git diff --name-only` and `git status --short` at tester wake (`next_agent=tester`, `task_id=TASK-036`).

| Path | Role | Verdict |
|---|---|---|
| `.../anomaly/StatisticalAnomalyDetector.kt` | production | OK — FR-1~4 / AC-1 |
| `.../anomaly/StatisticalAnomalyDetectorTest.kt` | test (Developer) | **Accepted** — spec §1.2 및 AC-2~AC-7. assertion 약화 없음 |
| `docs/qa/TASK-012`–`030`, `A-B-group-gap-fill-report.md` | prior Tester | TASK-036 Developer 범위 밖 |
| `docs/task-board.json`, `docs/turn.json` | workflow | 보드/턴 상태 |
| `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties` | untracked leftover | TASK-036과 무관 |
| `docs/specs/**` | PM | 이번 사이클에서 수정 없음 |

Tester가 AC-7 JSON 골든 픽스처를 추가함. 경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :core:fusion:cleanTest :core:fusion:test verifyModuleDependencies :app:assembleDebug
# BUILD SUCCESSFUL in 2s
```

`:core:fusion:test` — **55 tests, 0 failures**

| Suite | Tests | Failures |
|---|---|---|
| `StatisticalAnomalyDetectorTest` | 5 | 0 |
| `StatisticalAnomalyDetectorGoldenFixtureTest` | 1 | 0 |
| 회귀 | 49 | 0 |
| **Total** | **55** | **0** |

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `:core:fusion:compileKotlin` + test SUCCESS. `updateBaseline` / `detectAnomalies` / `analyzeFatigueTrend` JVM 실행 |
| AC-2 | PASS | 10회 정상 스피드 후 mean=1500, stdDev≈17.638 (Welford). golden `expected_mean`/`expected_std_dev` |
| AC-3 | PASS | speed=1350 → `CRITICAL`, `isAnomaly=true`, `zScore <= -2.5` |
| AC-4 | PASS | speed=1505 (mean±1σ 이내) → `NORMAL`, `isAnomaly=false` |
| AC-5 | PASS | 스피드 급락+지연 증가 5연속 → `isFatigued=true`, `fatigueScore>=0.7`, 요약에 「피로 누적」 |
| AC-6 | PASS | N=3 → `isReliable=false`, 극단값도 경고 억제, 「Baseline 축적 중 (3/5)」 |
| AC-7 | PASS | golden JSON + 선언 명령 BUILD SUCCESSFUL, fusion **55/0** |

### Notes (not AC failures)

- Spec §5 UI/UX는 **N/A**. 실기기 수동 테스트 대상 없음.
- FR-1은 `N>=3`을 신뢰 기준으로 적시하나 AC-6과 데이터 클래스 기본값은 `N>=5`. 구현은 AC-6을 따름.
- 범위의 `elbowAngleDeg`는 추출 지표에 없고 `totalDurationMs`로 대체됨. AC는 스피드/지연/효율로 검증.

## Verdict

**QA_PASSED** — Welford Baseline, z-score CRITICAL/NORMAL, N<5 경고 억제, 세션 피로 폼 붕괴가 JVM에서 확인됨. UI가 없어 실기기 수동 QA 항목은 없다.
