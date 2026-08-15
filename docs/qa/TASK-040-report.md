# TASK-040 QA Report — History Lab 세션 상세 및 리플레이 내비게이션

**Date:** 2026-08-15T08:14:44Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-040-history-lab-session-detail-replay-navigation.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected commit `706f195` (`feat(history): implement TASK-040 Lab session fusion detail and replay navigation`). Working tree otherwise leftover `.cursor/` / spike gradle props only.

| Path | Role | Verdict |
|---|---|---|
| `HistoryScreen.kt`, `SessionDetailScreen.kt`, `SessionDetailViewModel.kt`, `LabSessionDetailModels.kt` | production | OK — FR-1/FR-2 / AC-1~2 |
| `AppNavHost.kt`, `AppRoutes.kt`, `LabReplayViewModel.kt`, `LabRawRecordParser.kt`, repository + `verifyModuleDependencies` allow-list | production | OK — FR-3 / NFR 6.2 |
| `SessionDetailViewModelTest.kt` | test (Developer) | **Accepted** — spec §1.2가 Lab 레코드 로드/매핑 단위 테스트를 명시. 기존 assertion 유지 + LAB 매핑 테스트 추가, 약화 없음 |
| `HistoryViewModelTest.kt` | test | 미변경 |
| `AppRoutesContractTest.kt` | test (Developer) | **Accepted** — spec FR-3 / AC-3 라우트 계약. assertion **추가**(강화) |
| `SessionDetailNavigationUiTest.kt` | test (Developer, Tester 강화) | **Accepted** — spec §1.2 `SessionDetailScreenTest`. 스윙 #2·콜백→`createLabReplayRoute` assertion 강화 |
| `FakeSwingHistoryRepository.kt`, `RecordingSwingHistoryRepository.kt`, `SwingHistoryRepositoryStartSessionTest` RecordingRepo | test doubles | **Accepted** — 인터페이스에 추가된 Lab raw-record 메서드 스텁. 기존 assertion 변경 없음 (`batchUpdateGlobalStatistics` 파라미터명만 인터페이스와 정합) |
| `docs/task-board.json`, `docs/turn.json` | workflow | 보드/턴 |

경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew :feature:history:test :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug --rerun-tasks
# BUILD SUCCESSFUL in 35s
```

`:feature:history:test` — **10 tests, 0 failures** (timestamp `2026-08-15T08:14:15Z`)

| Suite | Tests | Failures |
|---|---|---|
| `HistoryViewModelTest` | 3 | 0 |
| `SessionDetailViewModelTest` | 7 | 0 |

`:feature:lab:test` — **35 tests, 0 failures** (timestamp `2026-08-15T08:14:18Z`)

`:app:testDebugUnitTest` — **42 tests, 0 failures** (timestamp `2026-08-15T08:14:33Z`) including:

| Suite | Tests | Failures |
|---|---|---|
| `HistoryScreenUiTest` | 1 | 0 |
| `SessionDetailNavigationUiTest` | 2 | 0 |
| `LabReplayNavigationUiTest` | 1 | 0 |
| `AppRoutesContractTest` | 4 | 0 |

`verifyModuleDependencies` SUCCESS (`:feature:history` → `:feature:lab` 없음).  
`:app:assembleDebug` SUCCESS.

선언 명령은 JVM only. spec §1.2 `AppNavigationInstrumentedTest`는 `connectedAndroidTest`가 아니므로 AC-3/4는 JVM NavHost Compose 테스트로 실행.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `HistoryScreenUiTest.ac1_labHistoryCardShowsDrillDatetimeSwingCountAndDuration`: 「포핸드 훈련」, `formatSessionName(startTime)`, 「7회 스윙 · 2m 0s」, 「포발리 훈련」, fallback 「Lab 훈련」. 카드 탭 → `sess-hist-fh` |
| AC-2 | PASS | `SessionDetailNavigationUiTest.ac2AndAc3…`: 「포핸드 훈련」, 「5회 스윙 · 2m 0s」, 「정타율 (SQUARE)」, 「평균 체인 효율」, 「스윙 #1」/「스윙 #2」. empty: 「기록된 스윙 데이터가 없습니다.」. VM: LAB 레코드 2건 index 1/2, empty list |
| AC-3 | PASS | 스윙 #1 탭 → `onNavigateToReplay("sess-lab-999", 555L)` → `createLabReplayRoute` = `lab_replay/sess-lab-999/555`. `LabReplayNavigationUiTest`: NavHost `LAB_REPLAY`에서 「동기 리플레이 & 정밀 진단」. `AppRoutesContractTest.labReplay embeds sessionId and recordId` |
| AC-4 | PASS | `LabReplayNavigationUiTest`: 리플레이 상단 「⟵」클릭 후 「스윙 #1」·「포핸드 훈련」복귀 |
| AC-5 | PASS | `SessionDetailViewModelTest` 7/0, `HistoryViewModelTest` 3/0 |
| AC-6 | PASS | 선언 명령 BUILD SUCCESSFUL, history **10/0**, lab **35/0**, app **42/0**, `verifyModuleDependencies`, `assembleDebug` |

### Notes (not AC failures)

- spec §7 「분석 데이터 파싱 실패」 문구는 프로덕션 UI에 없음. 파서는 잘못된 JSON을 빈 시계열로 흡수하고 크래시하지 않음.
- spec §7 유효하지 않은 `recordId` 시 Toast 「스윙 데이터를 찾을 수 없습니다」 + `popBackStack()`은 미구현. `LabReplayScreen`은 「리플레이 데이터가 없습니다」를 표시함.
- `AppNavigationInstrumentedTest`는 선언 명령에 없어 기기 History→Detail→Replay 경로는 Human follow-up.

### Human follow-up (실기기)

History → Lab 세션 카드 → 스윙 카드 → 동기 리플레이 → 뒤로가기로 세션 상세 복귀.

## Verdict

**QA_PASSED** (`retry_count` 유지 0). History 카드·Lab 상세 스윙 리스트·`LAB_REPLAY` 왕복 내비게이션이 선언 명령 0 failures로 확인됨.
