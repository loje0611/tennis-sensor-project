# TASK-027 QA Report — `:feature:lab` CameraX 프레임 파이프라인 및 Preview/Pose Overlay

**Date:** 2026-08-14T09:49:48Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-027-camerax-frame-pipeline.md` (v2)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

| Path | Role | Verdict |
|---|---|---|
| `TennisDocAI/gradle/libs.versions.toml` (`camerax = 1.4.1`, camera-core/camera2/lifecycle/view) | production/config | OK (FR-1) |
| `TennisDocAI/feature/lab/build.gradle.kts` CameraX + accompanist-permissions | production/config | OK (FR-1 / FR-5) |
| `feature/lab/src/main/.../pipeline/PoseAnalysisAnalyzer.kt` | production | OK (FR-2) |
| `feature/lab/src/main/.../ui/LabScreen.kt`, `PoseOverlayCanvas.kt` | production | OK (FR-4 / FR-5) |
| `feature/lab/src/test/.../pipeline/PoseAnalysisAnalyzerTest.kt` | test | **Accepted** — AC-4가 Mock `ImageProxy`로 `close()`·콜백 단위 테스트를 명시. Tester가 bitmap 실패 시 wrapper 미호출·`processImage` 예외 시 `close()` 케이스를 보강 |
| Prior unrelated working-tree gap-fill tests | outside TASK-027 | OK |
| `docs/specs/**` | PM | untouched |

No boundary violation requiring `QA_FAILED`.

### Commands Executed

```bash
cd TennisDocAI
./gradlew :feature:lab:test verifyModuleDependencies :feature:lab:assembleDebug
# BUILD SUCCESSFUL

./gradlew assembleDebug
# BUILD SUCCESSFUL
```

`:feature:lab:testDebugUnitTest`

| Suite | Tests | Failures |
|---|---|---|
| `PoseAnalysisAnalyzerTest` | 3 | 0 |
| `PoseLandmarkerWrapperTest` (TASK-026 회귀) | 7 | 0 |
| **Total** | **10** | **0** |

Analyzer cases: `testAnalyze_callsCloseAndCallback_success`, `testAnalyze_callsClose_whenBitmapFails`, `testAnalyze_callsClose_whenProcessImageThrows`.

`verifyModuleDependencies` SUCCESS.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | CameraX 1.4.1 catalog aliases + `:feature:lab` `implementation`; `:feature:lab:assembleDebug` and app `assembleDebug` SUCCESS |
| AC-2 | PASS | success 경로: wrapper `processImage` 호출, 콜백에 `PoseFrame` 전달, `close()` 호출. timestamp `123456789 / 1e6 = 123`. bitmap 실패: 콜백 `null`, wrapper 미호출, `close()` 호출. `processImage` throw: `close()` 호출 |
| AC-3 | PASS* | `:feature:lab:compileDebugKotlin` / `assembleDebug`가 `LabScreen`( `AndroidView`+`PreviewView` FIT_CENTER, 640×480, `STRATEGY_KEEP_ONLY_LATEST` )과 `PoseOverlayCanvas`(상체·팔·다리 본 연결)를 함께 컴파일. *라이브 프리뷰 픽셀/기기는 미실행 (`:feature:lab`에 compose-ui-test 없음, 카메라 없음) |
| AC-4 | PASS | Mock `ImageProxy` 3건 전부 그린 (위 AC-2) |
| AC-5 | PASS | `:feature:lab:test` 10/0 + `verifyModuleDependencies` SUCCESS |

### Notes (not this-cycle failures)

- 카메라 바인딩 실패 시 spec §7의 UI 에러 카드 대신 `printStackTrace()`만 수행.
- `LabScreen`은 Material3 기본 테마를 쓰며 `:core:ui` 프로젝트 의존이 없다 (spec §5 테마 준수는 후속 과제 가능).
- `LabScreen`은 v1 `AppNavHost`에 아직 연결되어 있지 않다. 본 spec 범위 밖.

## Verdict

**QA_PASSED** — CameraX 의존성·480p/`KEEP_ONLY_LATEST` 설정이 컴파일되고, `PoseAnalysisAnalyzer`는 Mock 프레임에서 콜백과 `imageProxy.close()`를 항상 수행함이 실행으로 확인됨.

## Run 2 (spec v2)

**Date:** 2026-08-14T09:49:48Z  
**Result:** **QA_PASSED**

### Boundary Check

Inspected `git diff --name-only` and `git status --short` (uncommitted tree at tester wake).

| Path | Role | Verdict |
|---|---|---|
| `TennisDocAI/app/src/main/AndroidManifest.xml` | production/manifest | OK — FR-1 / AC-6 authorize `<uses-permission android.permission.CAMERA>` and `<uses-feature android.hardware.camera required=false>`. Diff is only those two declarations. |
| `TennisDocAI/feature/lab/src/main/AndroidManifest.xml` (untracked) | production/manifest | OK — same FR-1 / AC-6 authorization; library-side duplicate of the required permission/feature. |
| `docs/task-board.json`, `docs/turn.json` | agent state | OK |
| `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties` | unrelated untracked | outside TASK-027; pre-existing; not a Developer test edit |
| Test sources under `feature/lab/src/test/**` and `app/src/test/**` | test | Developer did not modify tests this cycle |

No boundary violation. Manifest edits stay within FR-1 / AC-6 (permission + optional camera feature only).

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
# BUILD SUCCESSFUL in 11s

./gradlew :feature:lab:test :feature:lab:assembleDebug --rerun-tasks
# BUILD SUCCESSFUL in 7s
```

`verifyModuleDependencies` SUCCESS.  
`verifyJniBindings` PASSED (4 ABIs, `EdgeImpulseNative`).  
`assembleDebug` / `:app:assembleDebug` / `:feature:lab:assembleDebug` SUCCESS.

`:feature:lab:testDebugUnitTest` (this cycle, `--rerun-tasks`)

| Suite | Tests | Failures |
|---|---|---|
| `PoseAnalysisAnalyzerTest` | 3 | 0 |
| `PoseLandmarkerWrapperTest` (TASK-026 회귀) | 7 | 0 |
| **Total** | **10** | **0** |

Analyzer cases: `testAnalyze_callsCloseAndCallback_success`, `testAnalyze_callsClose_whenBitmapFails`, `testAnalyze_callsClose_whenProcessImageThrows`.

`:app:testDebugUnitTest` (this cycle)

| Suite | Tests | Failures |
|---|---|---|
| `CameraManifestDeclarationTest` | 2 | 0 |

Cases: `cameraPermissionIsRequested`, `cameraHardwareFeatureIsOptional`.  
Full `test` across modules: **114 tests, 0 failures, 0 errors**.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | CameraX 1.4.1 catalog aliases remain; `:feature:lab:assembleDebug` SUCCESS (this cycle `--rerun-tasks`) |
| AC-2 | PASS | `PoseAnalysisAnalyzerTest` 3/0. success: wrapper `processImage` + callback `PoseFrame` + `close()`. bitmap 실패: callback `null`, wrapper 미호출, `close()`. `processImage` throw: `close()` |
| AC-3 | PASS* | `:feature:lab:compileDebugKotlin` / `assembleDebug` compiled `LabScreen` (`AndroidView`+`PreviewView` FIT_CENTER, 640×480, `STRATEGY_KEEP_ONLY_LATEST`) with `PoseOverlayCanvas`. *라이브 카메라 픽셀은 미실행 (기기 없음; `:feature:lab`에 compose-ui-test 없음) |
| AC-4 | PASS | Mock `ImageProxy` 3건 전부 그린 (위 AC-2, this-cycle XML timestamp `2026-08-14T09:49:55Z`) |
| AC-5 | PASS | `:feature:lab:test` 10/0 + `verifyModuleDependencies` SUCCESS; full `test` 114/0 |
| AC-6 | PASS | `CameraManifestDeclarationTest`: `PackageManager.getPackageInfo(GET_PERMISSIONS)`에 `CAMERA` 포함; `GET_CONFIGURATIONS`의 `FEATURE_CAMERA`가 `FLAG_REQUIRED` 없음 (`required=false`). OS 권한 팝업 자체는 기기 없어 단위 테스트로 대체 (`AI_README` 기기 부재 규칙) |
| AC-7 | PASS | `./gradlew assembleDebug` (`:app:assembleDebug`) SUCCESS |

### Notes (not this-cycle failures)

- 카메라 바인딩 실패 시 spec §7의 UI 에러 카드 대신 `printStackTrace()`만 수행 (v1과 동일).
- 라이브 Preview/권한 다이얼로그 픽셀은 에뮬레이터·실기기 없이 관측하지 않음.

## Verdict (Run 2)

**QA_PASSED** — spec v2 매니페스트 권한/피처가 머지된 앱 아티팩트의 `PackageManager`로 확인되고, Analyzer `close()`/콜백·모듈 테스트·`:app:assembleDebug`가 이 사이클에서 실행되어 통과함.

---

## Run 3 (device connectedAndroidTest — supplemental)

**Date:** 2026-08-14T10:10:27Z  
**Device:** SM-N981N (Galaxy Note20, Android 13, wireless adb `192.168.68.100:44775`)  
**Result:** **PASS** (supplemental; original `QA_PASSED` unchanged)

### Commands Executed

```bash
cd TennisDocAI
./gradlew :app:connectedDebugAndroidTest
# BUILD SUCCESSFUL in 31s — 7 tests, 0 failures
```

| Test | Result |
|---|---|
| `LabCameraPreviewInstrumentedTest.labScreen_withCameraGranted_streamsPreviewAndShowsFpsChip` | PASS — `PreviewView.StreamState.STREAMING` + FPS 칩 표시 |
| `LabCameraPermissionInstrumentedTest.labScreen_withoutCamera_showsRationaleAndSystemPermissionDialog` | PASS — 안내 문구/`권한 허용` → OS 권한 다이얼로그 Allow → 프리뷰 STREAMING |

AC-3 라이브 프리뷰와 AC-6 OS 권한 팝업을 실기기에서 실행으로 확인함.
