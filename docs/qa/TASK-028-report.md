# TASK-028 QA Report — `:feature:lab` 내비게이션 통합

**Date:** 2026-08-14T05:50:00Z  
**Target:** `TennisDocAI`  
**Spec:** `docs/specs/TASK-028-lab-navigation-integration.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

| Path | Role | Verdict |
|---|---|---|
| `AppRoutes.kt` (`LAB = "lab"`) | production | OK (FR-1) |
| `AppNavHost.kt` (Lab 탭, `showBottomBar`, `composable(LAB)` + `LabScreen`) | production | OK (FR-2·FR-3·FR-4) |
| `app/build.gradle.kts` `implementation(project(":feature:lab"))` | production/config | OK (NFR 6.1) |
| `AppRoutesContractTest.kt` | test | **Accepted** — FR-5 / AC-4가 본 테스트에 `AppRoutes.LAB` 검증 추가를 명시. 단정 완화 없음 (`"lab"` 일치) |
| Prior unrelated working-tree gap-fill tests | outside TASK-028 | OK |
| `docs/specs/**` | PM | untouched |

No boundary violation requiring `QA_FAILED`.

### Commands Executed

```bash
cd TennisDocAI
./gradlew :app:test verifyModuleDependencies assembleDebug
# BUILD SUCCESSFUL
```

`:app:testDebugUnitTest` — **27** tests, **0** failures, **0** errors.

| Suite | Tests | Failures |
|---|---|---|
| `LabNavigationSmokeTest` | 2 | 0 |
| `AppRoutesContractTest` | 3 | 0 |
| `AppRoutesMatchDeactivationTest` | 3 | 0 |
| `HistoryNavigationSmokeTest` | 3 | 0 |
| session / IMU / example | 16 | 0 |

`verifyModuleDependencies` SUCCESS. `assembleDebug` SUCCESS.

### Acceptance Criteria

| # | Result | Evidence |
|---|---|---|
| AC-1 | PASS | `AppRoutesContractTest` `assertEquals("lab", AppRoutes.LAB)` |
| AC-2 | PASS | `LabNavigationSmokeTest.labTabClickNavigatesToLabRoute` — History 출발 → Lab 탭 클릭 → `"Lab destination"` 표시 (`AppRoutes.LAB` navigate + saveState/restoreState 패턴). 전체 `AppNavHost`+Hilt는 JVM에서 미합성 |
| AC-3 | PASS | `LabNavigationSmokeTest.labScreenLoadsPermissionPromptWhenCameraDenied` — `LabScreen()` 로드, `"카메라 권한이 필요합니다."` / `"권한 허용"` 표시. `assembleDebug`가 `composable(AppRoutes.LAB) { LabScreen(...) }` 컴파일 |
| AC-4 | PASS | `AppRoutesContractTest` 3/0 (`lab history and settings routes are stable` 포함) |
| AC-5 | PASS | `:app:test` 27/0 + `verifyModuleDependencies` + `assembleDebug` SUCCESS |

### Notes (not this-cycle failures)

- `AppNavHost` `startDestination`이 `HISTORY`에서 `LAB`으로 바뀌었다. TASK-028 spec은 시작 라우트를 강제하지 않음.
- Match 비활성화(D-2)와 History 스모크는 FR-5 회귀 확인으로 통과.

## Verdict

**QA_PASSED** — `AppRoutes.LAB` 계약, Lab 탭 이동, `LabScreen` JVM 로드, 모듈 그래프·앱 빌드가 실행 증거와 함께 통과함.

---

## Run 2 (device connectedAndroidTest — supplemental)

**Date:** 2026-08-14T10:10:27Z  
**Device:** SM-N981N  
**Result:** **PASS** (supplemental; original `QA_PASSED` unchanged)

`AppNavigationInstrumentedTest.bottomBar_navigatesLabHistorySettings` PASS: 실기기 Hilt `MainActivity`에서 Lab ↔ History ↔ Settings 하단바 이동 후 Lab `PreviewView` 복귀.
