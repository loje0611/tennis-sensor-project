# TASK-052 QA Report — CameraX 2초 스윙 클립 자동 녹화

**Date:** 2026-08-18T10:13:35Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-052-camerax-swing-clip-video-recording.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake. Leftover: `.cursor/`, `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties`.

| Path | Role | Verdict |
|---|---|---|
| `SwingVideoFrame.kt`, `SwingVideoBuffer.kt`, `SwingVideoEncoder.kt`, `SwingVideoEncoderImpl.kt` | production | OK — FR-1 / FR-2 |
| `PoseAnalysisAnalyzer.kt` `onFrameAvailable`, `LabFusionPipeline.kt`, `LabModule.kt`, `LabScreen.kt` | production | OK — FR-3 / FR-4 |
| `SwingVideoBufferTest.kt` (Developer 신규) | test | **Accepted then rewritten** — spec §1.2 클래스명. 기존 1개 assertion을 3초 윈도우·스냅샷 독립성·clear로 강화 |
| `SwingVideoEncoderTest.kt` (Developer 신규) | test | **Rewritten** — 원본 `assertTrue(result \|\| !result)`는 항상 통과. 검증이 아님 |
| `LabFusionPipelineVideoTest.kt` (Developer 신규) | test | **Rewritten** — `VideoRetentionOption.LAST_50` / 존재하지 않는 `VideoFileManager` API / `labRawRecordDao=null`이라 `videoPath`를 단정하지 않음. 실제 계약으로 교체 |
| `LabViewModelTest.kt` Fake `feedVideoFrame` | test (Tester) | FR-4 인터페이스 추가로 stub. 기존 assertion 유지 |
| `PoseAnalysisAnalyzerTest.kt` named `onPoseExtracted` + `onFrameAvailable` | test (Tester) | FR-3 마지막 람다 파라미터 추가로 trailing lambda가 깨짐. 이름 인자로 복구, 콜백 증거 추가 |

개발자가 명세에 적힌 테스트 클래스를 만든 것은 §1.2로 수용. 인코더 항진 단정은 예외 범위 밖이라 Tester가 교체했다. 경계만으로 `QA_FAILED`하지 않음.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
# BUILD SUCCESSFUL in 19s — unit 331 tests, 0 failures

ADB=/home/keunu/Android/Sdk/platform-tools/adb
"$ADB" connect 192.168.68.105:40527
# failed: No route to host

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.loje0611.tennisdoc.lab.SwingVideoEncoderInstrumentedTest
# FAILED: DeviceException: No connected devices!
```

`verifyModuleDependencies` SUCCESS.  
`verifyJniBindings` PASSED (4 ABIs).  
`test` — **331 tests, 0 failures**.  
`assembleDebug` SUCCESS.

`:feature:lab` TASK-052 suites (unit):

| Suite | Tests | Failures |
|---|---|---|
| `SwingVideoBufferTest` | 3 | 0 |
| `SwingVideoEncoderTest` | 2 | 0 |
| `LabFusionPipelineVideoTest` | 4 | 0 |
| `PoseAnalysisAnalyzerTest` | 6 | 0 |

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 3초 롤링 버퍼 프루닝 | PASS | `addFramePrunesOlderThanThreeSecondWindow`: stamps 0/500/1500/2500/3500/4500 → 유지 `[1500,2500,3500,4500]`. `snapshotIsAscendingAndIndependentOfLaterAdds`, `clearEmptiesSnapshot` |
| AC-2 유효한 MP4 인코딩 | **not verified** | Unit `emptyFramesReturnsFalseWithoutCreatingOutput`: 빈 리스트 → `false`, 파일 미생성. `encodeFailSafeDoesNotThrow`: Robolectric에서 예외 없이 끝나지만 성공 시 `ftyp` / 실패 시 파일 삭제라 **유효 MP4 생성을 단정하지 않음**. 기기 `SwingVideoEncoderInstrumentedTest.encodeToMp4WritesFtypMp4File`는 adb `No route to host`로 미실행 |
| AC-3 autoSave ON → `videoPath` 저장 | PASS | `autoSaveOnStoresEncodedPathAndEnforcesRetention`: Fake encoder 성공 후 DAO `videoPath == generateVideoFile(...).absolutePath`, 파일 존재 |
| AC-4 autoSave OFF → 인코딩 생략, `videoPath=null` | PASS | `autoSaveOffSkipsEncodeAndPersistsNullVideoPath`: encoder calls=0, `videoPath=null`, retention 0회 |
| AC-5 저장 후 `enforceRetentionPolicy` | PASS | 같은 ON 테스트: `enforceCalls=1`, `lastMaxCount=COUNT_50.maxCount`(50). 인코딩 실패 시 retention 생략 (`encodeFailurePersistsNullPathAndSkipsRetention`) |
| AC-6 선언 명령 | PASS | `./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug` BUILD SUCCESSFUL, unit 331/0 |

### Notes (not AC failures)

- `PoseAnalysisAnalyzer.analyze_invokesOnFrameAvailableWithProcessedBitmapAndTimestamp`: 90° 회전 보정 비트맵(480×640)과 timestamp 123ms 전달. AC 항목은 아님.
- spec §5 신규 UI 없음. 기기 미연결이라 APK 미설치.

## Escalation: 검증 불가

AC-2(`SwingVideoEncoder`가 프레임 리스트를 **유효한 MP4**로 인코딩)는 `MediaCodec`/`MediaMuxer`가 필요한 관측 결과다. Robolectric 단위 테스트는 코덱 부재 시 실패를 fail-safe로 삼을 수 있어, 성공한 MP4 `ftyp` 헤더를 증명하지 못한다.

시도한 기기 명령:

```
adb connect 192.168.68.105:40527
# failed to connect: No route to host
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.loje0611.tennisdoc.lab.SwingVideoEncoderInstrumentedTest
# DeviceException: No connected devices!
```

누락: 무선 디버깅 기기(SM-N981N). 포트가 다시 열리면 동일 instrumented 테스트를 실행해 AC-2를 닫을 수 있다. `retry_count`는 개발 실패가 아니므로 유지(0).

## Verdict

**BLOCKED** (`retry_count` 유지 0). 버퍼·파이프라인 ON/OFF·보관 정책은 단위 테스트로 확인했으나, 실제 H.264 MP4 생성(AC-2)은 기기 없이 실행 증거를 만들지 못했다.

## Run 2 (spec v1) — 기기 AC-2 재검증

사용자 지시로 무선 포트 `192.168.68.105:43683` (SM-N981N)에 재연결. Case D 환경 복구.

```bash
adb connect 192.168.68.105:43683
# connected, SM-N981N

export ANDROID_SERIAL=192.168.68.105:43683
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.loje0611.tennisdoc.lab.SwingVideoEncoderInstrumentedTest
# BUILD SUCCESSFUL in 25s — Starting 1 tests on SM-N981N - 13, Finished 1 tests
```

`SwingVideoEncoderInstrumentedTest.encodeToMp4WritesFtypMp4File` — **1/0**. 8프레임 480×640 비트맵을 `SwingVideoEncoderImpl.encodeToMp4`로 인코딩, 성공, 파일 크기 > 32B, 헤더 `ftyp`.

| # | Result | Evidence |
|---|---|---|
| AC-2 유효한 MP4 인코딩 | PASS | 기기 `encodeToMp4WritesFtypMp4File` 1/0 |
| AC-1, AC-3, AC-4, AC-5, AC-6 | PASS | Run 1 단위 증거 유지 |

## Verdict

**QA_PASSED** (`retry_count` 유지 0). Case D 기기 부재가 해소되었고 AC-2 H.264 MP4 `ftyp`가 SM-N981N에서 확인됨.

