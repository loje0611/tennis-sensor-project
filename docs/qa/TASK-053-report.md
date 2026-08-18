# TASK-053 QA Report — 비디오 + 스윙 궤적 리플레이 UI 개편

**Date:** 2026-08-18T13:29:24Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-053-video-swing-trail-replay-ui-overhaul.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected uncommitted Developer tree at tester wake. Leftover: `.cursor/`, `spike-mediapipe-benchmark/gradle/gradle-daemon-jvm.properties`.

| Path | Role | Verdict |
|---|---|---|
| `ReplayModels.kt`, `LabReplayViewModel.kt`, `LabReplayScreen.kt` | production | OK — FR-2 / FR-6 |
| `SwingVideoPlayer.kt`, `SwingTrailOverlay.kt`, `SwingAnalysisSummaryCard.kt` | production (untracked) | OK — FR-3 / FR-4 / FR-5 |
| `LabUiState.kt`, `LabViewModel.kt`, `SessionCompletionDialog.kt` | production | OK — FR-7 |
| `LabSessionDetailModels.kt`, `SessionDetailScreen.kt`, `SessionDetailViewModel.kt` | production | OK — FR-7 |
| `feature/lab/build.gradle.kts`, `gradle/libs.versions.toml` | production | OK — FR-1. Not asserted by reading file text; resolve proven via classpath + `assembleDebug` |
| test sources | — | Developer did not touch tests |

No boundary violation.

### Commands Executed

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
# BUILD SUCCESSFUL in 18s — unit 349 tests, 0 failures
```

`verifyModuleDependencies` SUCCESS.  
`verifyJniBindings` PASSED (4 ABIs).  
`test` — **349 tests, 0 failures**.  
`assembleDebug` SUCCESS (`SwingVideoPlayer` compiled against Media3).

TASK-053 suites:

| Suite | Tests | Failures |
|---|---|---|
| `ReplayModelsTest` | 5 | 0 |
| `LabReplayViewModelTest` | 8 | 0 |
| `SwingTrailOverlayTest` | 3 | 0 |
| `LabReplayVideoIntegrationTest` | 5 | 0 |
| `HistoryReplayVisibilityTest` | 2 | 0 |
| `LabSessionCompletionAiReportUiTest` | 4 | 0 |
| `LabReplayNavigationUiTest` | 1 | 0 |
| `SessionDetailNavigationUiTest` | 2 | 0 |

### Acceptance Criteria (v1)

| # | Result | Evidence |
|---|---|---|
| AC-1 Media3 exoplayer/ui resolve on `:feature:lab` | PASS | `media3ExoplayerAndPlayerViewResolveOnClasspath`: `Class.forName("androidx.media3.exoplayer.ExoPlayer")` / `androidx.media3.ui.PlayerView`. `assembleDebug` compiled `SwingVideoPlayer`. `verifyModuleDependencies` SUCCESS (Media3 confined to `:feature:lab`) |
| AC-2 IMU/skeleton/kinetic **calls removed**; video+trail+analysis rendered | PASS | `hasVideoTrue_rendersVideoTrailAnalysisAndOmitsImuSkeletonCards`: shows `🎾 스윙 궤적 분석`, `🎯 임팩트 점프`; 0 nodes for `IMU 동기 파형 & 운동체인 피크`, `포즈 데이터 대기 중...`, `5단계 운동 체인 & 인과 진단`. `SwingTrailOverlayTest.impactBadgeShownWhenIsImpactAndTrailPointsPresent`: `IMPACT!` |
| AC-3 `hasVideo==true` ExoPlayer + trail overlay | PASS (JVM UI branch) | ViewModel `hasVideo=true`, `videoPath` = temp file, `swingTrailPoints` non-empty. Screen is not empty-state (`리플레이 데이터가 없습니다` = 0). Trail overlay IMPACT badge. Frame-accurate MP4 decode is device Human follow-up |
| AC-4 `hasVideo==false` empty copy | PASS | `hasVideoFalse_showsEmptyReplayMessageAndHidesAnalysisChrome`: `리플레이 데이터가 없습니다`. `missingVideoPath_fallsBackToEmptyStateEvenWhenSwingIsLoaded` |
| AC-5 History badge + navigate only when file exists | PASS | `videoBadgeShownOnlyForExistingFileAndOnlyThatCardNavigates`: one `🎬 영상 보기`; 스윙 #1 click → session `sess-hist-video` / record `101` |
| AC-6 no-video card does not navigate | PASS | same test: 스윙 #2 click → navigateCount stays 0. `missingFileAtRecordedPath_hidesVideoBadgeAndDoesNotNavigate` |
| AC-7 SessionCompletionDialog hides replay without video | PASS | `replayButtonHiddenWhenSummaryHasNoVideo`: 0 nodes `🎬 리플레이 보기`; dismiss `닫기 / 새 훈련` remains. Existing tests still see the button when default `hasVideo=true` |
| AC-8 Korean face labels | PASS | `racketFaceStateLabel_mapsSquareOpenClosedToKoreanCopy` + `analysisCardRendersKoreanFaceLabelsForSquareOpenAndClosed`: `🟢 정타 (스퀘어)`, `🟠 페이스 열림 (공이 뜨는 원인)`, `🔵 페이스 닫힘 (네트에 걸리는 원인)` |
| AC-9 declared Gradle command | PASS | `./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug` BUILD SUCCESSFUL, unit 349/0 |

### Notes (not AC failures)

- Existing navigation tests were updated to plant a real temp MP4 so TASK-053's `hasVideo` gate still exercises replay routing (`LabReplayNavigationUiTest` now asserts title `스윙 비디오 리플레이`).
- Robolectric does not prove MediaCodec-backed playback; Human follow-up on SM-N981N: Lab 세션에서 클립이 있는 스윙의 `🎬 영상 보기` → 영상+궤적, 없는 스윙은 뱃지 숨김.
- Debug APK install attempted after handoff: `adb connect 192.168.68.105:43683` → `No route to host`; `:app:installDebug` → `No connected devices!`. Reconnect wireless ADB (port changes each pairing) and install when the phone is reachable.
