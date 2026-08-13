# TASK-027 QA Report — `:feature:lab` CameraX 프레임 파이프라인 및 Preview/Pose Overlay

**Date:** 2026-08-13T06:43:23Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-027-camerax-frame-pipeline.md` (v1)  
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
