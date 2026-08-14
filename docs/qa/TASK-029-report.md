# TASK-029 QA Report — Room DB v8 마이그레이션 및 Lab 세션 원시 데이터 스키마

**Date:** 2026-08-14T10:38:50Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-029-room-db-v7-lab-session-schema.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected `git diff --name-only` and `git status --short` at tester wake.

| Path | Role | Verdict |
|---|---|---|
| `core/model/.../SessionType.kt`, `DrillType.kt`, `LabRawSwingRecord.kt` | production | OK (FR-1) |
| `core/data/.../SwingSessionEntity.kt` `sessionType`/`drillType` | production | OK (FR-2) |
| `core/data/.../LabRawRecordEntity.kt`, `LabRawRecordDao.kt` | production | OK (FR-3 / FR-4) |
| `core/data/.../TennisDocDatabase.kt` version 8 + `MIGRATION_7_8` | production | OK (FR-5) |
| `core/data/.../CoreDataModule.kt` `provideLabRawRecordDao` | production | OK (FR-4 / AC-4) |
| `core/data/schemas/.../8.json` | generated schema | OK (NFR 6.2) |
| `core/data/src/androidTest/.../SwingHistoryRepositoryCsvInstrumentedTest.kt` | test | **Not Developer** — prior Tester constructor fix (`SwingHistoryRepositoryImpl`). Assertions unchanged. |
| `app/build.gradle.kts` androidTest deps, `app/src/androidTest/**` (lab/camera/EI/nav) | prior Tester leftovers | outside TASK-029 Developer scope |
| `docs/qa/TASK-012`–`028` supplemental device runs | prior Tester | outside TASK-029 |
| `docs/specs/**` | PM | untouched this cycle (`059404b` already on main) |

No Developer test-file edit. No boundary violation requiring `QA_FAILED`.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew verifyModuleDependencies :core:model:test :core:data:test :app:assembleDebug
# BUILD SUCCESSFUL

./gradlew :core:data:connectedDebugAndroidTest
# BUILD SUCCESSFUL in 14s — 13 tests, 0 failures (SM-N981N)
```

`verifyModuleDependencies` SUCCESS.  
`:app:assembleDebug` SUCCESS (Hilt `provideLabRawRecordDao` compiles).

`:core:model:test` — `LabDomainModelTest` **3/0** (`sessionTypeContainsMatchAndLab`, `drillTypeContainsRequiredValues`, `labRawSwingRecordHoldsDrillAndPayloads`).

`:core:data:connectedDebugAndroidTest` (this cycle)

| Suite | Tests | Failures |
|---|---|---|
| `Migration7To8Test` | 1 | 0 |
| `LabRawRecordDaoTest` | 5 | 0 |
| `SwingSessionDaoTest` (회귀) | 6 | 0 |
| `SwingHistoryRepositoryCsvInstrumentedTest` (회귀) | 1 | 0 |
| **Total** | **13** | **0** |

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `LabDomainModelTest` 3/0: `SessionType` = {MATCH, LAB}; `DrillType` 8값; `LabRawSwingRecord` 필드 보존. `:core:model:test` SUCCESS |
| AC-2 | PASS | In-memory Room insert/query of `LabRawRecordEntity`; migrated `swing_sessions` has `sessionType`/`drillType`. Match default `sessionType=MATCH`, `drillType=null` |
| AC-3 | PASS | `LabRawRecordDaoTest.databaseVersionIs8` (version=8). `Migration7To8Test` executes `MIGRATION_7_8` |
| AC-4 | PASS | `database.labRawRecordDao()` CRUD 성공 (Hilt `provideLabRawRecordDao`가 호출하는 동일 팩토리). `:app:assembleDebug`가 `CoreDataModule` @Provides 컴파일 |
| AC-5 | PASS | v7 `swing_sessions`에 `legacy-1`/`Old Session`/count=4 insert 후 migrate: `sessionType=MATCH`, `drillType` NULL, 이름·카운트 보존; `lab_raw_records` 테이블·`index_lab_raw_records_sessionId` 존재 |
| AC-6 | PASS | insert+getById+session query; session delete CASCADE empties records; missing `sessionId` → `SQLiteConstraintException` |
| AC-7 | PASS | `:core:model:test` + `:core:data:test` + `verifyModuleDependencies` 0 failures |

## Verdict

**QA_PASSED** — v8 마이그레이션이 레거시 세션을 `MATCH`로 보존하고 `lab_raw_records`를 만들며, DAO CRUD·FK·CASCADE가 실기기 SQLite에서 확인됨.
