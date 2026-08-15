# TASK-030 QA Report — 세션 라이프사이클 UX 개편

**Date:** 2026-08-14T11:12:36Z  
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

## Verdict (Run 1)

**QA_PASSED** — 명시적 `startSession`/`finishSession`/`cancelSession`과 BLE 비자동 세션 생성, LAB+FOREHAND_TOPSPIN 임시 행, 비활성 시 이벤트 비영속, 종료 시 타입 보존이 JVM 및 실기기 Room에서 확인됨. **이후 Run 2 실기기 수동 검증에서 번복.**

## Run 2 (spec v1) — 실기기 수동 검증 (SM-N981N)

**Date:** 2026-08-14T11:12:36Z  
**Device:** SM-N981N (Android 13), debug APK `installDebug` 후 사용자 수동 테스트  
**Tester production code:** 수정하지 않음. Developer가 수정할 것.

사용자 재개 지시: TASK-030이 이미 `DONE`이었으나 실기기 결함이 확인되어 판정을 **QA_FAILED**로 되돌림 (`retry_count` 0 → 1).

### Observed Failures

#### FAIL-1 — Lab 관절 오버레이가 몸에 붙지 않음

- **재현:** Lab 탭, 카메라 권한 허용, 사람이 프레임 안에 있음.
- **증거:** 기기 스크린샷 `/sdcard/DCIM/Screenshots/Screenshot_20260814_200510_TennisDoc AI.jpg` (2026-08-14 20:05). FPS 칩 `12.9 FPS | 77ms` — 추론은 동작함.
- **관측:** 초록 관절 점·파란 뼈대가 사람 위에 없고 화면 **우측 하단**으로 밀림. 일부 점이 화면 맨 아래 가장자리에 붙고, 세로 뼈대가 두 사람 사이 빈 공간으로 올라감. 스케일·위치가 몸과 불일치.
- **Developer 수정 방향 (관측 가능한 계약):**
  - Preview 표시 영역과 오버레이 좌표계가 같아야 한다. 현재 Preview는 `FIT_CENTER` + 640×480, 오버레이는 캔버스 전체에 `x * width` / `y * height`를 곱함. Note20(1080×2400)에서 4:3 레터박스와 불일치.
  - `ImageAnalysis` 프레임을 MediaPipe에 넣기 전 카메라 `rotationDegrees`를 반영해야 한다. 보정 없으면 미리보기(정방향)와 랜드마크(버퍼 방향)가 어긋난다.
  - 수정 후 같은 구도에서 관절이 어깨·팔·다리에 붙어야 한다.

#### FAIL-2 — 센서(BLE) 연결 실패 — 권한 미요청

- **재현:** Settings → Sensor Calibration → 시작. 동일 센서는 **이전 빌드 앱에서는 연결됨**.
- **증거 (logcat, pid TennisDocAI, 20:06:49):**
  ```
  E BleManager: 스캔 권한 없음
  E BleManager: java.lang.SecurityException: Need android.permission.BLUETOOTH_SCAN permission
      ... GattService registerScanner
  ```
  약 10초 후 Toast: `응답 없음: 연결 혹은 전송에 실패했습니다.`
- **기기 권한 (dumpsys package):** `CAMERA=granted`, **`BLUETOOTH_SCAN=false`**, **`BLUETOOTH_CONNECT=false`**, **`ACCESS_FINE_LOCATION=false`**, `POST_NOTIFICATIONS=false`.
- **원인:** Calibration 경로는 포그라운드 서비스를 바로 기동해 스캔하지만, 런타임 BLE 권한을 요청하는 UI가 **Settings에 없음**. 요청 코드는 Match `PracticeScreen`에만 있고, v1 하단 내비에 Match가 없어 사용자가 권한을 줄 기회가 없음.
- **Developer 수정 방향:**
  - Settings 캘리브레이션(및 실 BLE 스캔을 시작하는 모든 진입점)에서 Android 12+ `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (+ 필요 시 location) 런타임 권한을 **스캔 전에** 요청·획득할 것.
  - 거부 시 `PermissionDenied`를 사용자에게 보이게 하고, 허용 후 재시도하면 기존과 같이 GATT 연결이 되어야 함.
  - 센서 펌웨어/하드웨어 문제가 아님. 같은 기기·같은 센서로 이전 앱은 연결됨.

### Acceptance Criteria (Run 2)

Run 1 JVM/Room AC-1~AC-7은 그대로 통과. 실기기 수동 검증에서 제품 경로가 깨짐.

| # | Result | Evidence |
|---|---|---|
| AC-1~AC-7 (자동화) | PASS (Run 1 유지) | JVM 26+5 / Room 16, 0 failures |
| Spec §5 / FR-1 실 BLE 연결 | **FAIL** | FAIL-2: 캘리브레이션이 권한 없이 스캔 → SecurityException. 실기기에서 BLE 연결 자체를 완료할 수 없음 |
| Lab 포즈 오버레이 (수동 항목 5) | **FAIL** | FAIL-1: 스크린샷상 스켈레톤이 몸과 불일치 |

### Developer 수정 범위 (하지 말 것 / 할 것)

- Tester는 프로덕션 코드를 수정하지 않음.
- 고칠 곳: Lab 오버레이 좌표·회전 정렬, Settings(또는 공통 BLE 진입) 런타임 권한 요청.
- 수정 후 debug APK를 실기기에 설치하고, (1) Lab에서 관절이 몸에 붙는지 (2) Settings 캘리브레이션이 권한 다이얼로그 후 센서에 연결되는지 확인할 것.

## Verdict (Run 2)

**QA_FAILED** — 자동화 AC는 통과했으나 실기기에서 Lab 오버레이 오정렬과 BLE 스캔 권한 미요청으로 센서 연결이 실패함. Developer 수정 후 재검증.

## Run 3 (spec v1) — FAIL-1 / FAIL-2 재검증

**Date:** 2026-08-14T11:21:47Z  
**Device:** SM-N981N

### Boundary Check

Developer 생산 코드만 수정. 테스트 파일은 Tester가 추가함.

| Path | Role | Verdict |
|---|---|---|
| `feature/lab/.../PoseAnalysisAnalyzer.kt` | production | OK — ImageProxy 회전 보정 (FAIL-1) |
| `feature/lab/.../LabScreen.kt` | production | OK — Preview `FILL_CENTER` (FAIL-1) |
| `feature/lab/.../PoseOverlayCanvas.kt` | production | OK — 표시 영역 오프셋 매핑 (FAIL-1) |
| `app/.../SettingsScreen.kt` | production | OK — BLE 런타임 권한 요청 (FAIL-2) |
| `docs/specs/**` | PM | 미변경 |

경계 위반 없음.

### Commands Executed

```bash
cd TennisDocAI
./gradlew :core:data:test :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
# BUILD SUCCESSFUL

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
io.github.loje0611.tennisdoc.settings.SettingsBlePermissionInstrumentedTest,\
io.github.loje0611.tennisdoc.lab.LabCameraPreviewInstrumentedTest
# BUILD SUCCESSFUL — 2 tests, 0 failures (SM-N981N)

./gradlew :app:installDebug
```

`:feature:lab:test` — `PoseAnalysisAnalyzerTest` **5/0** (회전 2건 포함), `PoseLandmarkerWrapperTest` 7/0.  
`:core:data:test` + `:app:testDebugUnitTest` 회귀 0 failures.  
`verifyModuleDependencies` SUCCESS.

### FAIL-1 / FAIL-2

| ID | Result | Evidence |
|---|---|---|
| FAIL-1 회전 | PASS | `analyze_rotatesBitmap90DegreesBeforeLandmarker`: 640×480 + rotation 90 → landmarker에 480×640 전달. `analyze_keepsBitmapSizeWhenRotationIsZero` 640×480 유지 |
| FAIL-1 Preview scale | PASS | `labScreen_withCameraGranted_streamsPreviewAndShowsFpsChip`: PreviewView `FILL_CENTER`, FPS 칩 표시 |
| FAIL-1 몸에 붙음 (시각) | PASS | 사용자 실기기: Lab에서 관절 오버레이가 몸에 붙음 (2026-08-14) |
| FAIL-2 권한 게이트 | PASS | `calibrationDialog_withoutBlePermission_asksForPermissionInsteadOfScanning`: Settings → Sensor Calibration → 「블루투스 및 위치 권한이 필요합니다.」+「권한 허용」, 「시작」 없음 (권한 없을 때 스캔 진입 안 함) |
| FAIL-2 실센서 GATT | PASS | 사용자 실기기: Settings 캘리브레이션이 권한 허용 후 센서에 연결됨 |
| AC-1~AC-7 | PASS (Run 1 유지) | JVM/Room 0 failures |

### Human follow-up (2026-08-14, SM-N981N)

| 항목 | Result | 관측 |
|---|---|---|
| A. 캘리브레이션만 수행 후 History | PASS | 새 세션이 생성되지 않음 |
| B. Mock BLE 스윙 (세션 미시작) 후 History | PASS | 새 세션이 생성되지 않음 |
| C. Lab/Settings 측정 시작 버튼 | N/A | 버튼 없음. Spec §5 UX는 이번 구현 범위에 없음 (Match 탭도 v1 내비 비활성) |

시작/종료 UI가 없어 빈 세션 discard·측정 중 BLE 끊김·LAB 세션이 History에 확정되는 경로는 아직 실기기에서 불가.

## Verdict

**QA_PASSED** — FAIL-1/FAIL-2 자동·수동 모두 통과. BLE 연결만으로는 History 세션이 생기지 않음. 명시적 세션 시작 버튼은 후속 작업.
