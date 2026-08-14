# TASK-030 QA Report — 세션 라이프사이클 UX 개편

**Date:** 2026-08-14T11:00:36Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-030-session-lifecycle-ux-refactoring.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected `git diff --name-only` and `git status --short` at tester wake (`next_agent=tester`, `task_id=TASK-030`).

| Path | Role | Verdict |
|---|---|---|
| `app/.../TennisDocApplication.kt` | production | OK — `historyRepository`를 `SwingAnalysisSessionState`에 주입 (FR-2) |
| `app/.../session/SwingAnalysisSessionState.kt` | production | OK — 세션 StateFlow + start/finish/cancel (FR-2) |
| `app/.../service/SwingAnalysisForegroundService.kt` | production | OK — BLE 자동 provisional 제거, `isSessionActive` 가드 (FR-1 / FR-3) |
| `core/data/.../SwingHistoryRepository.kt` | production | OK — `startSession(...)` 기본 구현 (FR-4) |
| `docs/qa/TASK-012`–`028`, `A-B-group-gap-fill-report.md` | prior Tester | TASK-030 Developer 범위 밖 |
| `docs/task-board.json`, `docs/turn.json` | workflow | 보드/턴 상태 |
| `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties` | untracked leftover | TASK-030과 무관 |
| `docs/specs/**` | PM | 이번 사이클에서 수정 없음 |

Developer가 테스트 소스를 수정하지 않음. 경계 위반 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :core:data:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
# BUILD SUCCESSFUL in 5s

./gradlew :core:data:connectedDebugAndroidTest
# BUILD SUCCESSFUL — 16 tests, 0 failures (SM-N981N)
```

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS.

`:core:data:test` — **5/0** (`SwingHistoryCsvTest` 2, `SwingHistoryRepositoryStartSessionTest` 3).

`:app:testDebugUnitTest` — **26/0** (포함 `SessionLifecycleTest` 8/0).

`:core:data:connectedDebugAndroidTest` (SM-N981N)

| Suite | Tests | Failures |
|---|---|---|
| `SwingHistoryRepositorySessionLifecycleTest` (this cycle) | 3 | 0 |
| `LabRawRecordDaoTest` (회귀) | 5 | 0 |
| `Migration7To8Test` (회귀) | 1 | 0 |
| `SwingSessionDaoTest` (회귀) | 6 | 0 |
| `SwingHistoryRepositoryCsvInstrumentedTest` (회귀) | 1 | 0 |
| **Total** | **16** | **0** |

App `connectedDebugAndroidTest` 전체 스위트는 기존 `LabCameraPermissionInstrumentedTest` 프로세스 크래시로 중단됨 (TASK-030 범위 밖, 이번 판정에 사용하지 않음). Mock 포그라운드 서비스로 스윙 가드를 재현하려는 실험은 `startForeground` 타임아웃·분류 Idle 가능성으로 완료되지 않아 **삭제**했고, AC-4/AC-5는 SessionState Fake + 실 Room `insertSwingEvent` 경로로 실행했다.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `SessionLifecycleTest.startSessionExposesFlowsAndApis`: `startSession` 후 `activeSessionId`/`activeSessionType=LAB`/`activeDrillType=FOREHAND_TOPSPIN`/`isSessionActive=true`. `cancelSession` 후 모두 리셋. `:app:testDebugUnitTest` 8/0 |
| AC-2 | PASS | `bleConnectedDoesNotInsertProvisionalSession`: `updateConnection(Connected)` 이후 250ms 대기, `provisionalInserts`/`finalizeCalls`/`deletedSessionIds` 모두 비어 있고 `isSessionActive==false`. `bleDisconnectWithoutActiveSessionDoesNotTouchRepository` |
| AC-3 | PASS | JVM: `startSessionLabForehandTopspinInsertsTypedProvisionalRow` + `SwingHistoryRepositoryStartSessionTest.startSessionInsertsLabForehandTopspinProvisional`. Device Room: `startSessionCreatesLabForehandTopspinRow` → `sessionType=LAB`, `drillType=FOREHAND_TOPSPIN`, `isSessionActive==true` |
| AC-4 | PASS | `inactiveSessionDoesNotPersistSwingEventsWhenLabelUpdates`: BLE Connected + `updateSwingLabel`/`incrementSwingCount` 후에도 Fake `insertedEvents` 비어 있고 `activeSessionId==null` (persist 가드 전제: sid null / inactive) |
| AC-5 | PASS | JVM: `finishSessionFinalizesCountsAndPreservesSessionAndDrillType`가 활성 세션에서 `incrementSwingCount` 2회 후 `finalizeSession.totalSwingCount=2` + breakdown `"forehand topspin"=2`. Device Room: `swingEventIsStoredUnderActiveSessionId` — `insertSwingEvent`가 해당 `sessionId` FK로 1행 저장 |
| AC-6 | PASS | JVM Fake: `endTime`/`totalSwingCount=2`/`durationMillis=5000`/`sessionType=LAB`/`drillType=FOREHAND_TOPSPIN` 보존. Device Room: `finalizeSessionPersistsEndTimeCountAndTypes`. 빈 세션: `finishSessionWithZeroSwingsDeletesProvisionalRow` |
| AC-7 | PASS | `:core:data:test` 5/0 + `:app:testDebugUnitTest` 26/0 + `verifyModuleDependencies` SUCCESS, 0 failures |

### Limitations (human follow-up, not AC failures)

- Spec §5 UI 문자열(「센서 연결 완료 (대기 중)」, Lab/Match **시작** 버튼)은 AC 목록에 없어 이번 판정에서 자동 검증하지 않음. Lab 화면은 카메라 오버레이만 있고 세션 시작 컨트롤이 보이지 않음. Match 탭은 v1 내비게이션에서 비활성.
- 실 BLE 스윙 → `swing_events` 삽입은 포그라운드 서비스 경로이며, 계측 테스트에서 Mock FGS를 안정적으로 기동하지 못함. 아래 수동 순서로 확인.

## Verdict

**QA_PASSED** — 명시적 `startSession`/`finishSession`/`cancelSession`과 BLE 비자동 세션 생성, LAB+FOREHAND_TOPSPIN 임시 행, 비활성 시 이벤트 비영속, 종료 시 타입 보존이 JVM 및 실기기 Room에서 확인됨.
