# A/B그룹 추가 테스트 Gap-Fill 리포트

**Date:** 2026-08-11T10:19:14Z  
**Scope:** A·B그룹 QA 공백 보강 (실기기/계측·EI 추론 정확도는 A그룹 리뷰 결정에 따라 **제외**)  
**Command:** `./gradlew test` → **BUILD SUCCESSFUL**, **103** tests, **0** failures

---

## 요약

| # | 항목 | 결과 | 신규/보강 테스트 |
|---|---|---|---|
| 1 | `:feature:match` ViewModel·simulateSwing 분기 | **PASS** | `MatchViewModelTest`(5), `SimulateSwingActionTest`(3) |
| 2 | Settings/Match 공유 디버그 10회 탭 | **PASS** | `DebugActivationTapTest`(5) |
| 3 | History·내비 계약 스모크 | **PASS** | `AppRoutesContractTest`(3), `SessionDetailViewModelTest` +2 |
| 4 | B그룹 비전 파이프라인 E2E | **PASS** | `VisionPipelineE2ETest`(2) |
| 5 | B그룹 경계·실패모드 | **PASS** | `VisionEdgeCasesTest`(7) |
| 6 | 세션 상태 순수 로직 | **PASS** | `SwingAnalysisSessionStateTest`(5) |
| 7 | Compose UI 스모크 | **보류** | A리뷰에서 UI 계측을 미룬 방침에 맞춰 JVM만 보강 |

---

## #1 `:feature:match` 단위 테스트

**목적:** MatchViewModel 위임 + `simulateSwing` 3분기(디버그 off / 파이프라인 on / idle).

**구현:**
- `FakeMatchSessionPort` + `MatchViewModelTest`
- 순수 정책 함수 `resolveSimulateSwingAction` / `SimulateSwingAction` 추출
- `MatchSessionPortImpl`이 동일 정책을 사용하도록 연결

**증거:** `:feature:match:test` — 8건 PASS.

---

## #2 디버그 활성화 제스처

**목적:** 임계값 10, 9회 미활성, 10회 활성, 이미 on이면 무동작, 공유 상수.

**구현:**
- `SwingAnalysisSessionState.DEBUG_ACTIVATION_TAP_THRESHOLD = 10`
- `setDebugMode(false)` / `resetSessionUiState()` 시 탭 카운터 리셋 (테스트 격리·동작 일관성)
- `DebugActivationTapTest`

**증거:** 5건 PASS.

---

## #3 History / 내비 계약

**목적:** PRACTICE 라우트 부재(D-2), `sessionDetail` 포맷, 세션 상세 `sessionId` 부재·미존재 시 notFound.

**구현:**
- `AppRoutesContractTest`
- `SessionDetailViewModelTest`에 missing key / unknown id 케이스 추가

**증거:** 내비 3 + SessionDetail 5(기존 3+신규 2) PASS.

---

## #4 B그룹 파이프라인 E2E

**목적:** PoseFrame → angle → impact → path → kinetic → diagnosis 연쇄.

**구현:** `VisionPipelineE2ETest`
- 합성 스윙 궤적 1건 → 피드백 맵 크기/키 일치
- 빈 입력 → 안전한 빈 결과

**증거:** 2건 PASS.

---

## #5 B그룹 경계 케이스

**목적:** 골든 픽스처(케이스 4~6)를 보완하는 NaN/단프레임/Unknown/null chain.

**구현:** `VisionEdgeCasesTest` 7건.

**증거:** 7건 PASS.

---

## #6 세션 상태 로직

**목적:** BLE 연결 전이, 스윙 카운트/정규화 키, duration, pipeline 플래그, reset.

**구현:** `SwingAnalysisSessionStateTest` 5건.

**증거:** 5건 PASS.

---

## #7 Compose UI (보류)

실기기/계측과 같은 이유로 **이번 gap-fill에서는 Compose UI 테스트 의존성을 도입하지 않음.**  
화면 회귀는 컴파일·내비 계약·ViewModel Fake 테스트로 대체.

---

## 부수 수정

| 변경 | 사유 |
|---|---|
| `CoachingCommentGeneratorImplTest` import → `core.analysis.impl` | 이관 후 깨진 기존 `:app` 테스트 복구 (스위트 컴파일 차단) |
| `resolveSimulateSwingAction` 추출 | #1 분기 검증을 소스 재읽기 없이 가능하게 함 |
| 디버그 탭 카운터 리셋 | #2 테스트 격리 및 disable 시 잔여 카운트 제거 |

---

## 전체 스위트

```bash
cd TennisDocAI
./gradlew test
# BUILD SUCCESSFUL — 103 tests, 0 failures (2026-08-11T10:19:14Z)
```

이전 B그룹 완료 시점 리포트 기준선(약 90건) 대비 JVM 단위 테스트가 보강됨.  
실기기 `connectedAndroidTest`·Edge Impulse 추론 정확도는 **의도적으로 범위 밖**.
