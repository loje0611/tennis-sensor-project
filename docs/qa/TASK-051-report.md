# TASK-051 QA Report — 스윙 비디오 스토리지 & 설정 인프라

**Date:** 2026-08-18T09:56:57Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-051-video-storage-and-preferences-infrastructure.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake (`docs/turn.json` `TASK-051`). Leftover: `.cursor/`, `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties`.

| Path | Role | Verdict |
|---|---|---|
| `VideoRetentionOption.kt` (new) | production | OK — FR-1 |
| `LabRawRecordEntity.kt` `videoPath`, `TennisDocDatabase.kt` v10 + `MIGRATION_9_10`, schema `10.json` | production | OK — FR-2 |
| `LabRawRecordDao.kt` (`updateVideoPath`, `getRecordsWithVideoAsc`, `observeVideoRecordCount`, plus `clearVideoPathByPath`/`clearAllVideoPaths`) | production | OK — FR-2 (extra clear helpers used by `VideoFileManagerImpl`) |
| `VideoPreferencesRepository.kt`, `VideoFileManager.kt`, `CoreDataModule.kt` | production | OK — FR-3 / FR-4 |
| `SettingsViewModel.kt`, `SettingsScreen.kt` (`VideoStorageSettingsSection`) | production | OK — FR-5 |
| `AiCoachSettingsUiTest.kt` | test (Developer) | **Accepted** — FR-5 `SettingsViewModel` 생성자 주입. Fake stub만 추가, 기존 assertion·기대값·테스트명 변경 없음 |
| `LabFusionPipelineTest.kt` | test (Developer) | **Accepted** — FR-2 `LabRawRecordDao` 메서드 추가로 Fake가 컴파일되도록 override stub. 기존 assertion 유지 |

경계 위반으로 `QA_FAILED`할 항목 없음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
# BUILD SUCCESSFUL in 12s (timestamp 2026-08-18T09:56Z)

export ANDROID_SERIAL=192.168.68.105:40527
./gradlew :core:data:connectedDebugAndroidTest
# BUILD SUCCESSFUL in 17s — 23 tests, 0 failures (SM-N981N)
```

`verifyModuleDependencies` SUCCESS.  
`verifyJniBindings` PASSED (4 ABIs, `EdgeImpulseNative`).  
`test` — **321 tests, 0 failures**.  
`assembleDebug` SUCCESS.

New unit suites (`:app:testDebugUnitTest`):

| Suite | Tests | Failures |
|---|---|---|
| `TennisDocDatabaseMigrationTest` | 1 | 0 |
| `VideoPreferencesRepositoryTest` | 2 | 0 |
| `VideoFileManagerTest` | 8 | 0 |
| `VideoSettingsUiTest` | 4 | 0 |

`:core:data:connectedDebugAndroidTest` (SM-N981N, timestamp `2026-08-18T09:56Z`) — **23 tests, 0 failures**, including `LabRawRecordDaoTest.databaseVersionIs10`, `Migration9To10Test.migrate9to10_addsVideoPathAndPreservesExistingRows`, `videoPathDefaultsNullAndUpdateVideoPathPreservesPoseJson`, `getRecordsWithVideoAscOrdersOldestFirstAndSkipsNullPaths`.

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 `MIGRATION_9_10` + 기존 데이터 보존 | PASS | Unit: `TennisDocDatabaseMigrationTest.migrate9to10_addsNullableVideoPathAndPreservesLabRawRecords` — `videoPath` 컬럼 추가(`notnull=0`), `legacy-session` 행의 session/drill/json/offset 보존, `videoPath` NULL. Device: `Migration9To10Test.migrate9to10_addsVideoPathAndPreservesExistingRows` (`s-9to10` SERVE 행 보존). `LabRawRecordDaoTest.databaseVersionIs10` → version=10 |
| AC-2 DataStore 기본값·저장/복원 | PASS | `a_defaultsAreAutoSaveEnabledAndCount50WhenUnset`: unset → `true` / `COUNT_50`. `b_persistsAutoSaveAndRetentionRoundTrip`: `false`/`COUNT_20` → `true`/`COUNT_50` → `UNLIMITED` 왕복 |
| AC-3 `enforceRetentionPolicy(50)` FIFO | PASS | `enforceRetentionPolicy50DeletesOldestOverflowAndKeepsPoseJson`: 51개 클립 중 timestamp 최소 파일 삭제, 해당 행 `videoPath=null`, `visionPosesJson`/`imuRawJson` 유지, 나머지 50개 경로 유지. Device DAO: `getRecordsWithVideoAsc` 오래된 순, null 경로 제외 |
| AC-4 `clearAllVideos()` 디스크 삭제 | PASS | `clearAllVideosDeletesFilesAndNullsAllPaths`: 3개 파일 삭제, 모든 `videoPath` null, pose JSON 유지. `deleteVideoFileReturnsFalseWhenMissingAndClearsDbPath`: 없는 파일 → `false`, DB만 갱신 |
| AC-5 Settings 카드·토글·드롭다운 | PASS | `cardRendersToggleRetentionDropdownAndClearButton`: `📹 스윙 영상 & 저장소 설정`, 자동 저장 스위치 ON, `최근 50개 (권장)`, 사용량/`🗑️ 비디오 캐시 전체 삭제`. `toggleAutoSaveUpdatesImmediately`: OFF 즉시 prefs 반영. `retentionDropdownChangeAppliesImmediatelyAndEnforcesPolicy`: `COUNT_20` + `enforceRetentionPolicy(20)`. `clearCacheDialogConfirmDeletesAndShowsToast`: `스윙 영상 캐시가 모두 삭제되었습니다.` |
| AC-6 선언 명령 | PASS | `./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug` BUILD SUCCESSFUL, unit 321/0 |

### Notes (not AC failures)

- `:core:data` 단위 테스트 의존성이 JUnit만 있어 DataStore/Room/파일 I/O 단위 테스트는 `:app` Robolectric 하니스에 배치 (`AiCoachPreferencesRepositoryTest`와 동일 패턴).
- `VideoFileManager.enforceRetentionPolicy` 단위 증거는 Fake DAO + 실제 `VideoFileManagerImpl`. 실 SQLite 경로/정렬은 기기 `LabRawRecordDaoTest`.
- Settings UI는 실기기 Human follow-up 대상. 디버그 APK를 `192.168.68.105:40527` (SM-N981N)에 설치.

## Verdict

**QA_PASSED** (`retry_count` 유지 0). Room v10 마이그레이션, 비디오 환경설정, FIFO 스토리지 정리, 설정 화면 토글/드롭다운이 단위 테스트와 실기기 SQLite에서 확인됨.
