# TASK-026 QA Report — `:feature:lab` MediaPipe Pose Landmarker SDK 연동

**Date:** 2026-08-13T05:24:11Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-026-mediapipe-pose-landmarker-integration.md` (v1)  
**Result:** **QA_FAILED** (`retry_count` 0 → 1)

## Run 1 (spec v1)

### Boundary Check

| Path | Role | Verdict |
|---|---|---|
| `TennisDocAI/feature/lab/build.gradle.kts` | production/config | OK (FR-1) |
| `TennisDocAI/gradle/libs.versions.toml` | production/config | OK (FR-1, `mediapipeTasksVision = 0.10.14`) |
| `TennisDocAI/feature/lab/src/main/**` (wrapper, Fake, asset) | production | OK |
| `TennisDocAI/feature/lab/src/test/**/PoseLandmarkerWrapperTest.kt` | test | **Accepted** — spec §1.2 “안전망 테스트 추가” + AC-4/AC-6이 테스트 존재·통과를 요구. 단정 완화는 없음. Tester가 관측 가능 케이스를 보강함. |
| Prior unrelated working-tree gap-fill (`app` nav smoke, history contract, analysis fallback, …) | outside TASK-026 | OK — Developer 이번 범위 아님 |
| `docs/specs/**` | PM | untouched |

No boundary violation requiring `QA_FAILED`.

### Commands Executed

```bash
ls -la TennisDocAI/feature/lab/src/main/assets/pose_landmarker_lite.task
# -rw-r--r-- 5777746 Aug 13 14:15 pose_landmarker_lite.task

cd TennisDocAI
./gradlew :feature:lab:test verifyModuleDependencies :feature:lab:assembleDebug
```

- `:feature:lab:assembleDebug` **SUCCESS**
- `verifyModuleDependencies` **SUCCESS**
- `:feature:lab:testDebugUnitTest` **FAILED** — 5 tests, **1 failure**, 0 skipped

```
PoseLandmarkerWrapperTest > realWrapperInitFailureIsIllegalStateExceptionNotLinkError FAILED
    java.lang.AssertionError: UnsatisfiedLinkError escaped wrapper init; FR-2 / JVM EH require IllegalStateException
    at PoseLandmarkerWrapperTest.kt:88

5 tests completed, 1 failed
BUILD FAILED in 7s
```

Passed in the same class: `testInitializationAndClose`, `processImageAfterCloseReturnsNull`, `testProcessImage_success`, `testProcessImage_recycledBitmap`.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `:feature:lab:assembleDebug` SUCCESS; catalog has `mediapipe-tasks-vision` 0.10.14; `feature/lab/build.gradle.kts` implements it + `:core:vision` |
| AC-2 | PASS | `pose_landmarker_lite.task` present at `feature/lab/src/main/assets/` (5 777 746 bytes); also merged into `build/intermediates/assets/debug/` |
| AC-3 | **FAIL (not verified)** | `MediaPipePoseLandmarkerWrapper.processImage` 매핑은 JVM에서 실행되지 않음. 33개 좌표 단정은 `FakePoseLandmarkerWrapper`의 `x = index` 하드코딩만 통과. 실제 MediaPipe → `PoseFrame` 변환의 실행 증거 없음. |
| AC-4 | **FAIL (partial)** | Fake 경로에서 리스트 길이 33·인덱스 0..32는 통과. 생산 매퍼(가시성 `orElse(1.0f)`, 빈 pose, x/y/z 전달)는 미실행. |
| AC-5 | PASS (Fake only) | Fake `close()` 다중 호출 예외 없음, close 후 `processImage` → `null`. 네이티브 `PoseLandmarker.close()`는 미실행. |
| AC-6 | **FAIL** | `:feature:lab:test` 1 failure (아래 Failure Detail). `verifyModuleDependencies`는 통과. |

### Failure Detail (Developer action)

1. **`UnsatisfiedLinkError`가 init를 빠져나옴 (FR-2 / §7 JVM EH)**  
   `MediaPipePoseLandmarkerWrapper`는 `catch (e: Exception)`만 사용한다. `.so` 미적재는 `UnsatisfiedLinkError`(`Error`)이라 잡히지 않는다.  
   **수정:** init에서 `Throwable`(또는 `UnsatisfiedLinkError`+`Exception`)을 `IllegalStateException`으로 감싼다.  
   재현: `PoseLandmarkerWrapperTest.realWrapperInitFailureIsIllegalStateExceptionNotLinkError`.

2. **AC-3/AC-4가 Fake만 검증함**  
   골든 매핑을 Fake가 `List(33) { PoseLandmark(it.toFloat(), 0f, 0f, 1f) }`로 만들어 주면 테스트는 구현이 바뀌어도 통과한다.  
   **수정:** MediaPipe 랜드마크 → `PoseLandmark`/`PoseFrame` 변환을 네이티브 없이 호출 가능한 함수로 추출하고, x/y/z/visibility 기본값·33개 1:1 인덱스·빈 pose(`PoseFrame(emptyList())` 또는 `null`)를 그 함수에 대해 단정한다.

### Notes (not this-cycle failures)

- Spec §2.1의 `Landmark3D` / `PoseFrame(frameIndex, timestampMs, …)` 는 TASK-021 실제 계약(`PoseLandmark` + `PoseFrame(landmarks)`)과 불일치. 구현이 TASK-021을 따른 것은 맞다. `processImage`의 `frameIndex`/`timestampMs`는 현재 버려진다.
- `presence` 필드는 `PoseLandmark`에 없어 매핑 불가. spec FR-4 서술 오류이며 TASK-021이 SSOT.

## Verdict

**QA_FAILED** — 모듈 빌드·에셋·의존성 규칙은 통과했으나, JVM에서 실래퍼 init가 `UnsatisfiedLinkError`로 터지고 MediaPipe→`PoseFrame` 매핑은 실행 증거가 없다.

---

## Run 2 (spec v1)

**Date:** 2026-08-13T06:29:01Z  
**Result:** **QA_PASSED**

### Boundary Check

| Path | Verdict |
|---|---|
| `MediaPipePoseLandmarkerWrapper.kt` (`catch (Throwable)`, `mapLandmarksToPoseFrame`) | OK — Run 1 수정 요청 범위 |
| `PoseLandmarkerWrapperTest.kt` 매핑 테스트 추가 | **Accepted** — AC-3/AC-4 실행 증거를 위한 테스트. Tester가 y/z·visibility 기본값 단정을 보강 |
| `TempTest.kt` (스크래치) | Developer 잔여물. Tester가 `src/test`에서 삭제 (커밋 오염 방지). 생산 코드 수정 아님 |
| `libs.versions.toml` / `feature/lab/build.gradle.kts` / asset | 이전 사이클과 동일, OK |
| Prior unrelated gap-fill untracked tests | TASK-026 범위 밖 |

No boundary violation requiring `QA_FAILED`.

### Commands Executed

```bash
ls -la TennisDocAI/feature/lab/src/main/assets/pose_landmarker_lite.task
# -rw-r--r-- 5777746 pose_landmarker_lite.task

cd TennisDocAI
./gradlew :feature:lab:test verifyModuleDependencies :feature:lab:assembleDebug
# BUILD SUCCESSFUL — PoseLandmarkerWrapperTest 7 tests, 0 failures

./gradlew verifyJniBindings assembleDebug
# verifyJniBindings PASSED: 4 ABIs
# BUILD SUCCESSFUL
```

Test XML (`testDebugUnitTest`): **7** tests, **0** failures, **0** errors:
`processImageAfterCloseReturnsNull`, `mapLandmarksDefaultsVisibilityWhenAbsent`, `testInitializationAndClose`, `realWrapperInitFailureIsIllegalStateExceptionNotLinkError`, `testMapLandmarksToPoseFrame`, `testProcessImage_recycledBitmap`, `testProcessImage_success`.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `:feature:lab:assembleDebug` + app `assembleDebug` SUCCESS |
| AC-2 | PASS | `pose_landmarker_lite.task` 5 777 746 bytes at `feature/lab/src/main/assets/` |
| AC-3 | PASS | `testMapLandmarksToPoseFrame` — 빈 pose → 빈 `PoseFrame`; 33개 `NormalizedLandmark` → `PoseFrame.landmarks` 크기 33, x/y/z 전달. `processImage`가 `mapLandmarksToPoseFrame(result.landmarks())`를 호출 |
| AC-4 | PASS | 인덱스 0..32 및 x/y/z/visibility 단정 통과. `mapLandmarksDefaultsVisibilityWhenAbsent` — visibility 없으면 `1.0f` |
| AC-5 | PASS | Fake `close()` 다중 호출·close 후 `processImage` null. 실래퍼 init는 `IllegalStateException`으로 감싸짐 (`realWrapperInitFailureIsIllegalStateExceptionNotLinkError` PASS) |
| AC-6 | PASS | `:feature:lab:test` 7/0 + `verifyModuleDependencies` SUCCESS |

### Verdict

**QA_PASSED** — Run 1 실패 두 건(UnsatisfiedLinkError 래핑, 생산 매퍼 단위 테스트)이 실행 증거와 함께 해소됨.

---

## Run 3 (device connectedAndroidTest — supplemental)

**Date:** 2026-08-14T10:10:27Z  
**Device:** SM-N981N  
**Result:** **PASS** (supplemental; original `QA_PASSED` unchanged)

`MediaPipePoseLandmarkerInstrumentedTest.realWrapper_initializesNativeSdkAndProcessesBitmap` **PASS** (0.356s): 실기기에서 `MediaPipePoseLandmarkerWrapper` 네이티브 init, 640×480 비트맵 `processImage`가 `PoseFrame` 반환, recycled bitmap → `null`, `close()` idempotent.

