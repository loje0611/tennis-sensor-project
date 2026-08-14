# TASK-037 QA Report — 실시간 센서-비전 스트림 융합 파이프라인 연동

**Date:** 2026-08-14T14:03:00Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-037-realtime-fusion-pipeline-integration.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected `git diff --name-only` and `git status --short` at tester wake (`next_agent=tester`, `task_id=TASK-037`).

| Path | Role | Verdict |
|---|---|---|
| `feature/lab/build.gradle.kts` | production | OK — `:core:fusion` 의존 (verify 허용 목록에 이미 포함) |
| `.../pipeline/LabFusionStreamBuffer.kt` | production | OK — FR-1 / AC-2 |
| `.../pipeline/LabFusionPipeline.kt` | production | OK — FR-2 / AC-3~5 |
| `.../ui/LabViewModel.kt` | production | OK — FR-4 / AC-6 |
| `.../pipeline/*Test.kt`, `.../ui/LabViewModelTest.kt` | test (Developer) | **Accepted** — spec §1.2 및 AC-2~AC-6. assertion 약화 없음 |
| `docs/qa/TASK-012`–`030`, `A-B-group-gap-fill-report.md` | prior Tester | TASK-037 Developer 범위 밖 |
| `docs/task-board.json`, `docs/turn.json` | workflow | 보드/턴 상태 |
| `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties` | untracked leftover | TASK-037과 무관 |
| `docs/specs/**` | PM | 이번 사이클에서 수정 없음 |

Tester가 빈 버퍼/reset 케이스를 추가함. 경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
# BUILD SUCCESSFUL in 4s
```

`:feature:lab:test` — **18 tests, 0 failures**

| Suite | Tests | Failures |
|---|---|---|
| `LabFusionStreamBufferTest` | 2 | 0 |
| `LabFusionPipelineTest` | 3 | 0 |
| `LabViewModelTest` | 1 | 0 |
| 회귀 (`PoseLandmarkerWrapper`/`PoseAnalysisAnalyzer`) | 12 | 0 |

`:app:testDebugUnitTest` — **26/0**.  
`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `:feature:lab:compileDebugKotlin` + `:feature:lab:test` SUCCESS |
| AC-2 | PASS | `AC-2 ring buffer...`: IMU t=0..4000 step 100 → 31샘플, first=1000, last=4000 (3초 윈도우) |
| AC-3 | PASS | `onSwingTriggered...`: `latestFusedSwing`가 반환 `FusedSwing`과 동일 |
| AC-4 | PASS | 같은 테스트: `latestAnomalyReport` non-null, `drillType=FOREHAND_TOPSPIN` |
| AC-5 | PASS | Fake DAO 1행: `sessionId=session-test-123`, `drillType=FOREHAND_TOPSPIN`, imu/pose JSON 포함 |
| AC-6 | PASS | `LabViewModel`이 pose/IMU를 pipeline에 전달하고 `latestFusedSwing`/`latestAnomalyReport`를 노출 |
| AC-7 | PASS | 선언 명령 BUILD SUCCESSFUL, lab **18/0**, app **26/0** |

### Notes (not AC failures)

- FR-3 세션 미활성 가드는 `onSwingTriggered`에 아직 없음. AC 목록에는 없어 이번 판정에 사용하지 않음.
- `LabScreen`은 카메라 오버레이만 있고 `LabViewModel`/파이프라인에 연결되어 있지 않음. Hilt 바인딩도 없음.
- Spec §5 50ms UI 반응은 StateFlow 갱신으로 JVM에서 확인. 화면에 융합 결과를 그리는 UI는 이번 범위의 AC에 없음.

### Human follow-up (실기기에서 아직 수행 불가)

Lab에 **세션 시작 버튼**과 **융합 결과 UI**가 없어 아래는 지금은 실행할 수 없습니다.

1. Lab 탭 → 카메라·BLE 권한 → 세션 시작  
2. 스윙 한 번 → 융합 진단이 바로 보이는지  
3. 세션 종료 후 History/`lab_raw_records`에 원시 JSON이 남는지  

시작/표시 UI가 생기면 위 순서로 보면 됩니다.

## Verdict

**QA_PASSED** — 링 버퍼 3초 보존, 스윙 트리거 시 융합·이상탐지·Fake Room 삽입, ViewModel StateFlow 노출이 JVM에서 확인됨. 실기기 수동 경로는 Lab UI 미연결로 보류.
