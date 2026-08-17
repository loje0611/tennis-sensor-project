# TASK-044 QA Report — 세션 융합 지표 집계 및 LLM Context Builder

**Date:** 2026-08-17T02:56:05Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-044-session-fusion-context-builder.md` (v1)  
**Result:** **QA_FAILED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake (HEAD `7526a0d`는 스펙). Working tree leftover `.cursor/` / spike gradle props, TASK-042 Tester 강화분(`LabSunlitCourtUiTest.kt`, `PoseOverlayCanvasTest.kt`).

| Path | Role | Verdict |
|---|---|---|
| `core/fusion/.../context/SessionPrescriptionContext.kt` | production | OK — FR-1 / FR-3 |
| `core/fusion/.../context/SessionPrescriptionContextBuilder.kt` | production | OK — FR-2 |
| `SessionPrescriptionContextBuilderTest.kt` | test (Developer, Tester 강화) | **Accepted** — spec §1.2 골든/단위 테스트. 집계·프라이버시·빈 세션 assertion **추가**, 약화 없음 |

경계 위반으로 `QA_FAILED`할 항목 없음. 실패는 AC-1 불변성.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew :core:fusion:test verifyModuleDependencies --rerun-tasks
# BUILD FAILED — 65 tests completed, 1 failed
```

`:core:fusion:test` — **65 tests, 1 failure** (timestamp `2026-08-17T02:55:47Z`).  
`SessionPrescriptionContextBuilderTest` — **10 tests, 1 failure**.  
`verifyModuleDependencies` SUCCESS (테스트 실패 전에 실행됨).

### Failures

**FAIL-1 — AC-1: `flawTagCounts` 불변성이 보장되지 않는다**

실행: `SessionPrescriptionContextBuilderTest.ac1_flawTagCountsAreNotExternallyMutable`  
기대: `expected:<{FACE_OPEN=1}>`  
실제: `{FACE_OPEN=1, HACKED_TAG=99}`

`buildContext`가 내부 `mutableMapOf`를 `SessionPrescriptionContext.flawTagCounts`에 그대로 넘긴다. 호출자가 `MutableMap`으로 캐스팅해 `put`하면 컨텍스트가 변한다. spec AC-1 「불변성이 보장되어야 한다」에 위배.

**Developer 수정 방향 (관측 가능한 계약):** `flawTagCounts`에 할당할 때 `toMap()`(또는 동등한 읽기 전용 복사)을 써서 외부 `put`이 컨텍스트를 바꾸지 않아야 한다. 동일 테스트가 `{FACE_OPEN=1}`을 유지해야 한다.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | FAIL | 데이터 클래스 `copy`는 독립적이나 `flawTagCounts` 외부 변이로 컨텍스트가 바뀜 (FAIL-1) |
| AC-2 | PASS | `testAccurateAggregationAndPrimaryFlaw`: 4스윙 순차 50%·효율 70/90·페이스 25/50/25·`FACE_OPEN` primary. 딜레이 평균 20/20/20/10. `ac2_singleSwingAveragesMatchMaxima`. `ac2_allBrokenChainsYieldZeroSequentialRate` |
| AC-3 | PASS | 동일 집계 테스트: representative `s4`(eff 50), `s2`(eff 60). 클린 `s1` 제외 |
| AC-4 | PASS | `ac4_jsonOmitsLandmarkCoordinatesImuSeriesAndStaysUnder2kb`: landmarks/`"x":`/`accelX`/`gyroZ`/좌표 리터럴 없음, length ≤ 2048 |
| AC-5 | PASS | `testEmptySessionReturnsDefault` + `ac5_emptySessionJsonHasZeroRatesAndNoRawSeries`: count 0, 비율 0, 예외 없음 |
| AC-6 | FAIL | 선언 명령 1 failure |

## Verdict

**QA_FAILED** (`retry_count` 0→1). 집계·대표 결함·프라이버시 JSON은 통과했으나 `flawTagCounts` 불변성(AC-1)이 깨진다.

## Run 2 (spec v1) — AC-1 불변성 수정

**Date:** 2026-08-17T02:59:05Z  
**Result:** **QA_PASSED**

### Boundary Check

Developer 재시도 트리: `SessionPrescriptionContextBuilder.kt`에 `ImmutableMapWrapper` + `flawCounts.toMap()` 할당. Tester assertion 약화 없음. 루트 `test_map.kt` 스크래치는 핸드오프 전 삭제.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew :core:fusion:test verifyModuleDependencies --rerun-tasks
# BUILD SUCCESSFUL in 3s
```

`:core:fusion:test` — **65 tests, 0 failures** (timestamp `2026-08-17T02:58:58Z`) including `SessionPrescriptionContextBuilderTest` 10/0.  
`verifyModuleDependencies` SUCCESS.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `ac1_flawTagCountsAreNotExternallyMutable`: `as? MutableMap` put 후에도 `{FACE_OPEN=1}` 유지, `HACKED_TAG` 없음. `ac1_contextIsDataClassCopyIndependent` |
| AC-2 | PASS | Run 1 유지. `testAccurateAggregationAndPrimaryFlaw` 0 failure |
| AC-3 | PASS | Run 1 유지. representative `s4`/`s2`, 클린 `s1` 제외 |
| AC-4 | PASS | `ac4_jsonOmitsLandmarkCoordinatesImuSeriesAndStaysUnder2kb` |
| AC-5 | PASS | `testEmptySessionReturnsDefault` / `ac5_emptySessionJsonHasZeroRatesAndNoRawSeries` |
| AC-6 | PASS | 선언 명령 0 failures |

## Verdict (Run 2)

**QA_PASSED** (`retry_count` 유지 1). `flawTagCounts` 외부 변이가 컨텍스트를 바꾸지 않으며 집계·프라이버시 JSON도 0 failure.

