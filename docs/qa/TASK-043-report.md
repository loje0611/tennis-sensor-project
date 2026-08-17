# TASK-043 QA Report — Room DB v9 마이그레이션 및 AI 코치 도메인 계약

**Date:** 2026-08-17T02:48:03Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-043-room-db-v9-ai-coach-domain-contracts.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake (HEAD `f7df01f`는 스펙만). Working tree leftover `.cursor/` / spike gradle props, 및 TASK-042 Tester 강화분(`LabSunlitCourtUiTest.kt`, `PoseOverlayCanvasTest.kt`).

| Path | Role | Verdict |
|---|---|---|
| `AiCoachReport.kt` | production | OK — FR-1 도메인 계약 |
| `SwingSessionEntity.kt`, `TennisDocDatabase.kt` `MIGRATION_8_9`, schema `9.json` | production | OK — FR-2 / FR-3 |
| `SwingSessionDao.kt`, `SwingHistoryRepository.kt`, `SwingHistoryRepositoryImpl.kt` | production | OK — FR-4 |
| `AiCoachReportTest.kt` | test (Developer, Tester 강화) | **Accepted** — spec AC-1 인스턴스 생성 검증. 필드·기본값·enum 강화, 약화 없음 |
| `SwingHistoryRepositoryAiReportTest.kt` | test (Developer) | **Accepted** — spec AC-3 단위 스텁. Fake는 실 DAO가 아니므로 AC-3 증거로 쓰지 않음 |
| `SwingHistoryRepositoryStartSessionTest.kt` | test (Developer) | **Accepted** — FR-4 인터페이스 추가에 따른 Fake stub. 기존 assertion 유지 |
| `Migration8To9Test.kt` | test (Developer) | **Accepted** — spec AC-2 `MIGRATION_8_9` 실행. 선언 명령 밖 androidTest이므로 기기에서 별도 실행 |
| `LabRawRecordDaoTest.kt` / `SwingSessionDaoTest.kt` / `SwingHistoryRepositorySessionLifecycleTest.kt` | test (Tester) | version 8→9, 사문화 `FOREHAND_TOPSPIN`/`VOLLEY` 컴파일 수정, 실 Room AC-3 추가 |

경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew :core:model:test :core:data:test verifyModuleDependencies --rerun-tasks
# BUILD SUCCESSFUL in 7s

export ANDROID_SERIAL=192.168.68.105:37131
./gradlew :core:data:connectedDebugAndroidTest
# BUILD SUCCESSFUL in 11s — 20 tests, 0 failures (SM-N981N)
```

`:core:model:test` — **10 tests, 0 failures** (timestamp `2026-08-17T02:47:15Z`) including `AiCoachReportTest` 3/0.  
`:core:data:test` — **6 tests, 0 failures** (timestamp `2026-08-17T02:47:19Z`).  
`verifyModuleDependencies` SUCCESS.

`:core:data:connectedDebugAndroidTest` (SM-N981N, timestamp `2026-08-17T02:47:54Z`)

| Suite | Tests | Failures |
|---|---|---|
| `LabRawRecordDaoTest` (`databaseVersionIs9` 포함) | 5 | 0 |
| `Migration7To8Test` | 1 | 0 |
| `Migration8To9Test` | 1 | 0 |
| `SwingSessionDaoTest` (`updateAiCoachReport` 2건 포함) | 8 | 0 |
| `SwingHistoryRepositoryCsvInstrumentedTest` | 1 | 0 |
| `SwingHistoryRepositorySessionLifecycleTest` (`saveAiCoachReport` 포함) | 4 | 0 |
| **Total** | **20** | **0** |

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `AiCoachReportTest` 3/0: 전 필드 보존, `targetRepetitions` 기본 10, `CoachTone` {ENCOURAGING, ANALYTICAL, STRICT}, `LlmProvider` {MOCK, GEMINI, OPENAI} |
| AC-2 | PASS | `LabRawRecordDaoTest.databaseVersionIs9` → version=9. `Migration8To9Test.migrate8to9_addsAiCoachReportColumns`: v8 세션 `s-8to9`/`Old Session 8` 보존, `aiCoachReportJson`/`aiReportGeneratedAt` NULL |
| AC-3 | PASS | `SwingSessionDaoTest.ac3_updateAiCoachReportPersistsJsonAndTimestamp`: JSON·timestamp 갱신, 기존 세션명/카운트 유지. missing `sessionId`는 예외 없이 0행. `SwingHistoryRepositorySessionLifecycleTest.ac3_saveAiCoachReportUpdatesSessionAndLeavesMissingIdQuiet`: Impl이 동일 세션만 갱신 |
| AC-4 | PASS | `verifyModuleDependencies` SUCCESS (`:core:model` JVM 잎 모듈 유지) |
| AC-5 | PASS | 선언 명령 0 failures + 기기 androidTest 20/0 |

### Notes (not AC failures)

- Developer `SwingHistoryRepositoryAiReportTest`는 in-memory Fake라 실 Room을 증명하지 않음. AC-3 증거는 계측 테스트.
- spec §5 UI 없음. 앱 APK 미배포.

## Verdict

**QA_PASSED** (`retry_count` 유지 0). Room v9 마이그레이션과 AI 코치 도메인 계약이 단위·실기기 SQLite에서 확인됨.
