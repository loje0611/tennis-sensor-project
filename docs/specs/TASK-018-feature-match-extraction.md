# TASK-018 — `:feature:match` 이관 및 v1 내비게이션 비활성화(보존)

| 항목 | 값 |
|---|---|
| Task ID | TASK-018 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-010, TASK-011, TASK-012, TASK-015, TASK-017 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §4, §8.4 · [`docs/PRODUCT_DIRECTION.md`](../PRODUCT_DIRECTION.md) D-2, D-9.1 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-08 | PM | 최초 작성 |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 목적

Match 모드 화면(`PracticeScreen`)을 `:feature:match` 모듈로 이관하고, **v1 내비게이션에서 제외**한다. D-2에 따라 **삭제가 아니라 비활성화**이며, 기능은 모듈에 온전히 보존되어 Phase 5에서 라우트 재등록만으로 복귀할 수 있어야 한다.

본 task 완료로 **A그룹(TASK-009~018, 멀티모듈 분리)이 종료**된다.

### 1.2 명세 작성 중 발견한 사항 — 기계적 이동으로는 끝나지 않는다

계획서(§4)는 본 task를 "이관 + 라우트 제거"로 기술했으나, 실제 코드를 조사한 결과 **두 가지 문제가 추가로 존재한다.** 이를 모르고 라우트만 지우면 빌드는 통과하면서 기능이 조용히 죽는다.

| # | 발견 | 영향 |
|---|---|---|
| **1** | `PracticeScreen`이 `MainViewModel`(`:app`)을 파라미터 타입으로 받고, `MainViewModel`은 `service/SwingAnalysisForegroundService`·`session/SwingAnalysisSessionState`에 의존한다. 이 둘은 **D-9.1에서 `:app` 잔류가 확정**되어 함께 옮길 수 없다 | 의존성 역전 없이는 모듈 이관 자체가 불가능 |
| **2** | **디버그 모드를 켜는 유일한 경로가 `PracticeScreen`의 10회 탭**(`MainViewModel.onDebugActivationAreaTap`)이다. `SwingAnalysisSessionState._debugModeEnabled`는 `MutableStateFlow(false)`로 **영속화되지 않아 프로세스마다 초기화**된다 | 라우트만 제거하면 디버그 모드를 **영원히 켤 수 없게 되고**, 그 결과 Settings의 **Engineering Mode 진입점**(`isDebugMode`로 가려짐)과 History의 **Mock 데이터 생성 FAB**이 동반 사망한다. 컴파일·테스트로는 전혀 드러나지 않는다 |

발견 #2는 §3의 FR-6이, #1은 FR-3이 다룬다.

### 1.3 비활성화해도 앱이 무력해지지 않는 근거

`SettingsViewModel`이 이미 `SwingAnalysisForegroundService.start()`·`requestStop()`·`requestSendBleCommand("CAL")`을 호출한다. 따라서 Practice 화면이 내비게이션에서 빠져도 **BLE 연결·해제·캘리브레이션 경로는 Settings에 남아 있다.** Lab 모드(C그룹)가 도착하기 전까지의 공백은 이 경로로 메워진다.

### 1.4 제외 (건드리지 말 것)

- **`service/SwingAnalysisForegroundService` · `session/SwingAnalysisSessionState`의 모듈 이동** — D-9.1에서 Phase 3까지 판단 유보. `:app`에 잔류시킨다. 본 task는 이들에 대한 **참조 방향만** 바꾼다.
- **`ui/settings/*`의 모듈 이동** — D-9.1에서 `:app` 잔류 확정.
- **Match 기능의 삭제·축소** — D-2는 보존을 요구한다. `PracticeScreen`의 UI 코드와 동작을 제거하거나 단순화하지 않는다.
- **Lab 모드·`:feature:lab`·`:core:vision`의 구현** — B/C그룹 범위. 본 task에서 이 모듈들에 소스를 추가하지 않는다.
- **TASK-017 잔여 부채**(`SessionDetailScreen.kt`의 사문화된 `hiltViewModel` import와 `:feature:history`의 `hilt-navigation-compose` 의존성) — PHASE2_PLAN §8.2의 ProGuard 부채와 함께 **A그룹 완료 후 일괄 정리** 대상이다. 본 task에서 건드리지 않는다.
- **`SwingHistoryRepository.CSV_TIMESTAMP_FORMAT` 가시성**, **ProGuard 규칙 중복·사문화 3줄** — 동일하게 일괄 정리 대상.
- **Room 스키마, DB 버전, `libswingsense_ei.so` 이름, `swingsense_export.csv` 파일명, `Theme.SwingSenseAI` 등 개명 잔재.**
- **`androidTest`(계측 테스트) 실행** — 실기기가 없으므로 컴파일만 만족하면 된다.

---

## 2. 정의 및 참조 (Definitions & References)

| 용어 | 의미 |
|---|---|
| **이관 대상** | `app/src/main/java/io/github/loje0611/tennisdoc/ui/practice/PracticeScreen.kt` (약 700행, `PracticeScreen`·`CyberpunkBackground` 외 파일 내부 전용 private 헬퍼 다수)와 `app/src/main/java/io/github/loje0611/tennisdoc/MainViewModel.kt`, 그리고 리소스 `app/src/main/res/drawable-nodpi/ic_neon_racket.png` |
| **대상 패키지** | `io.github.loje0611.tennisdoc.feature.match` (모듈 namespace와 동일) |
| **포트(port)** | Match 화면이 필요로 하는 세션 상태 조회와 명령 실행을 추상화한 인터페이스. `:feature:match`가 선언하고 `:app`이 구현한다 |

**참조 사실 (조사로 확인됨 — 구현 시 재확인 불필요)**

- `:feature:match`는 TASK-009에서 이미 `settings.gradle.kts`에 `include`되어 있고 `feature/match/build.gradle.kts`가 존재하며 **소스가 0개**다.
- 루트 `build.gradle.kts`의 허용 맵에 `":feature:match" to setOf(":core:model", ":core:ui", ":core:sensor", ":core:data", ":core:analysis")`가 **이미 선언되어 있어 맵 수정이 불필요하다.**
- `PracticeScreen`이 참조하는 `io.github.loje0611.tennisdoc.*` 심볼은 정확히 다음 7개다: `MainViewModel`(`:app`), `R.drawable.ic_neon_racket`(`:app`), `core.sensor.BleConnectionState`, `core.model.SwingClassificationKeys`, `core.ui.SwingLabelFormatter`, `core.ui.accentColorForCategory`, `core.ui.theme.MichromaFont`·`SwingTheme`. **즉 허용 집합 밖 결합은 `MainViewModel`과 `R` 두 건뿐이다.**
- `CyberpunkBackground`는 `public`이지만 `PracticeScreen.kt` 내부에서만 사용된다. `:app`의 다른 화면이 쓰지 않으므로 TASK-016 FR-3(`accentColorForCategory`)과 같은 역참조 문제는 없다.
- `MainViewModel`은 `AppNavHost`와 `PracticeScreen` 두 곳에서만 참조된다.
- `PracticeScreen`이 `viewModel`에서 사용하는 멤버는 정확히 9개다 — 상태 5개(`connectionState`, `detectedSwingLabel`, `swingCount`, `sessionDurationSeconds`, `isDebugModeEnabled`)와 동작 4개(`scanAndConnect`, `disconnect`, `onDebugActivationAreaTap`, `simulateSwing`). `sendBleCommand`는 `PracticeScreen`이 쓰지 않는다.
- `:core:model`은 `tennisdoc.jvm.library`를 쓰는 **순수 JVM 모듈이며 coroutines 의존성이 없다.**
- 라이브러리 모듈에서의 Hilt는 TASK-017에서 실증되었다(PHASE2_PLAN §8.4).

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1. `:feature:match` 빌드 스크립트 구성

`feature/match/build.gradle.kts`를 다음 요건대로 작성한다.

- `tennisdoc.android.library.compose` 관례 플러그인을 적용한다. `compileSdk`·`minSdk`·Java 버전·Compose 설정을 모듈에서 재선언하지 않는다.
- `namespace`는 `io.github.loje0611.tennisdoc.feature.match`를 유지한다.
- `@HiltViewModel`을 포함하므로 Hilt Gradle 플러그인과 KSP 플러그인을 적용하고 `hilt-android`(implementation)·`hilt-compiler`(ksp)를 선언한다.
- 프로젝트 의존성은 **실제로 참조하는 것만** 선언한다. §2의 조사 결과 `:core:model`·`:core:ui`·`:core:sensor` 세 개면 충분하다. **`:core:data`·`:core:analysis`는 허용 집합에 있더라도 참조가 없으면 선언하지 않는다** — 죽은 선언은 이후 어느 의존성이 살아 있는지 판단을 흐린다.
- 외부 라이브러리는 버전 카탈로그의 **기존 별칭만** 사용한다(Compose foundation, material-icons-extended, lifecycle-runtime-compose, accompanist-permissions, coroutines 등 이관 코드가 실제로 요구하는 것). 카탈로그에 새 좌표나 새 버전을 추가하지 않는다.
- `testImplementation(libs.junit)`을 선언한다.

### FR-2. 소스와 리소스 이관

- `PracticeScreen.kt`를 `feature/match/src/main/java/io/github/loje0611/tennisdoc/feature/match/`로 옮기고 `package`를 `io.github.loje0611.tennisdoc.feature.match`로 변경한다.
- `MainViewModel.kt`를 같은 위치로 옮기되, **클래스명을 `MatchViewModel`로 변경한다.** `:app`의 주 ViewModel이 아니게 되므로 `Main`이라는 이름은 소속과 역할을 잘못 알린다. 참조 지점은 `PracticeScreen` 하나로 줄어들어(FR-5가 `AppNavHost`의 참조를 제거하므로) 개명 위험이 낮다.
- `app/src/main/res/drawable-nodpi/ic_neon_racket.png`를 `feature/match/src/main/res/drawable-nodpi/`로 옮긴다. `PracticeScreen`의 FQCN 참조 `io.github.loje0611.tennisdoc.R.drawable.ic_neon_racket`은 모듈 자신의 `R`을 가리키도록 갱신한다. **`:app`에는 이 리소스가 남지 않아야 한다.**
- `PracticeScreen`의 Composable 함수명·private 헬퍼명·UI 코드는 변경하지 않는다. 파일 변경은 ⑴ `package` ⑵ `import`/`R` 참조 ⑶ FR-3이 요구하는 ViewModel 타입·개명 ⑷ FR-4의 시그니처 조정에 한정한다.
- 이관 후 `app/.../ui/practice/` 디렉터리와 `app/.../MainViewModel.kt`는 남지 않아야 한다.

### FR-3. 세션 상태·서비스 명령에 대한 의존성 역전

`MatchViewModel`이 `:app`의 `SwingAnalysisSessionState`·`SwingAnalysisForegroundService`를 직접 참조하지 않도록 **포트 인터페이스**를 도입한다.

- **선언 위치는 `:feature:match`** 로 한다. `:core:model`에 두지 않는다 — 이 모듈은 순수 JVM이고 coroutines 의존성이 없는데 포트는 `StateFlow`를 노출해야 하므로, `:core:model`에 넣으면 **공용 도메인 타입 모듈에 비동기 런타임 의존성을 끌어들이게** 된다. 또한 이 계약은 도메인 개념이 아니라 Match 화면 전용의 구동 계약이다.
- **포트가 노출해야 할 것** — 읽기: BLE 연결 상태, 감지된 스윙 레이블, 스윙 개수, 세션 경과 시간, 디버그 모드 여부. 명령: 스캔·연결 시작, 연결 해제, 디버그 활성화 제스처 통지, 스윙 시뮬레이션 요청. **`PracticeScreen`이 현재 사용하는 9개 멤버의 의미를 그대로 보존한다.**
- **`:app`이 구현**하고 Hilt로 바인딩한다. 구현은 `SwingAnalysisSessionState`(상태)와 `SwingAnalysisForegroundService`(명령)에 **위임만** 한다. 두 대상의 로직을 수정하지 않는다.
- `MatchViewModel`은 포트를 생성자 주입으로 받고 `@HiltViewModel`을 유지한다. `@ApplicationContext` 주입은 더 이상 필요하지 않다.
- **동작 의미를 보존한다.** 특히 `simulateSwing`은 파이프라인 실행 여부에 따라 서비스 요청과 상태 직접 갱신으로 갈라지고, 디버그 모드가 꺼져 있으면 아무 일도 하지 않는다. 이 분기가 사라지면 안 된다.

> **왜 `:feature:match`가 선언하고 `:app`이 구현하는가**: 하위 모듈은 `:app`을 참조할 수 없으므로 방향을 뒤집는 것 외에 선택지가 없습니다. TASK-016 FR-4가 `CoachingEngine` 결합을 끊은 것과 동일한 패턴이며, 그때 검증된 방식입니다.

### FR-4. `PracticeScreen`의 진입 계약

`PracticeScreen`은 `MatchViewModel`을 파라미터로 받는다(TASK-017에서 `SessionDetailScreen`에 적용한 방식과 동일). 화면 내부에서 `hiltViewModel()`을 호출하지 않는다.

`contentPadding: PaddingValues`의 기본값은 유지해도 된다. **`viewModel` 파라미터에는 기본값을 두지 않는다.**

### FR-5. v1 내비게이션에서 Match 제외

`AppNavHost.kt`에서 다음을 제거한다.

1. 하단 내비게이션 바의 **"Live" 항목**.
2. `AppRoutes.PRACTICE` 라우트의 `composable { ... }` 등록과 `PracticeScreen`·`MatchViewModel` import.
3. `AppRoutes.PRACTICE` 상수. 어디서도 쓰이지 않는 상수를 남기는 것은 §1.4가 정리 대상으로 지목한 사문화 선언과 같은 부류다.

`startDestination`은 현재 `AppRoutes.PRACTICE`이므로 **`AppRoutes.HISTORY`로 변경한다.** 변경하지 않으면 존재하지 않는 라우트를 시작 지점으로 지정해 **앱이 실행 즉시 크래시한다.**

하단 바에는 History·Settings 두 항목이 남는다. 남는 항목들의 아이콘·색상·선택 동작을 변경하지 않는다.

### FR-6. 디버그 모드 활성화 경로의 보존 (§1.2 발견 #2)

FR-5로 `PracticeScreen`이 도달 불가능해지면 **디버그 모드를 켜는 유일한 수단이 사라진다.** 이를 막기 위해 `:app`의 **Settings 화면에 동등한 활성화 제스처를 마련한다.**

- **동작 규정**: 디버그 모드가 꺼진 상태에서 지정된 영역을 **연속 10회 탭하면 디버그 모드가 켜진다.** 이미 켜져 있으면 추가 탭은 아무 효과가 없다. 임계값 10회는 기존 `MainViewModel.onDebugActivationAreaTap`의 값을 그대로 따른다.
- **탭 대상 영역**은 구현자가 정하되, **Settings 화면에서 별도 조건 없이 보이는 영역**이어야 한다. 디버그 모드가 켜져야만 보이는 요소(Engineering Mode 진입점 등)를 대상으로 삼으면 **자기 자신이 전제 조건이 되어 영원히 켤 수 없다.**
- 켜진 뒤에는 기존과 동일하게 Settings의 Engineering Mode 진입점과 History의 Mock 데이터 FAB이 나타난다.
- **탭 횟수 임계값(10)이 두 곳에 중복 정의되지 않게 한다.** Match 화면의 활성화 경로(FR-3의 포트)와 Settings의 새 경로가 **같은 `:app` 측 구현을 공유**해야 한다. 임계값이 두 벌이면 한쪽만 바뀌어 조용히 어긋난다.

> **왜 이것이 본 task의 범위인가**: 라우트 제거가 원인이고 기능 손실이 결과이므로, 같은 task에서 처리하지 않으면 **어느 시점부터 디버그 모드가 죽었는지 추적할 수 없는 상태**로 A그룹이 종료됩니다. 게다가 History의 Mock FAB은 에이전트 파이프라인이 데이터를 만들 때 쓰는 수단입니다.

### FR-7. 보존이 컴파일로 검증되는 상태 유지

`:app`은 화면을 호출하지 않게 되지만 **`:feature:match`에 대한 의존성을 유지한다.** FR-3의 포트 구현과 Hilt 바인딩이 `:app`에 있으므로 이 의존성은 실제로 사용된다.

그 결과 `MatchViewModel`의 Hilt 바인딩이 애플리케이션 컴포넌트에 계속 집계되고, **포트 바인딩이 빠지면 `:app` 빌드가 실패한다.** 즉 "보존"이 선언이 아니라 **빌드로 강제되는 성질**이 된다. 이 성질을 깨뜨리는 구성(예: 포트 구현을 지우고 모듈 의존성을 끊는 것)은 허용하지 않는다.

### FR-8. 문서 갱신

`TennisDocAI/AI_README.md`의 모듈 구조 설명에 `:feature:match`가 Match 화면을 보유하며 **v1 내비게이션에서는 비활성(보존)** 임을 반영한다. `docs/` 하위 문서는 수정하지 않는다(PM 소유 경로 — PHASE2_PLAN §8.1).

---

## 4. 인터페이스 및 데이터 구조

### 4.1 이관 후 의존 방향

```
:app ──> :feature:match ──> :core:{model, ui, sensor}
  │         (포트 선언)
  └── 포트 구현 ──> session/SwingAnalysisSessionState   (:app 잔류, D-9.1)
                └─> service/SwingAnalysisForegroundService (:app 잔류, D-9.1)
```

`:feature:match`는 `:app`을 참조하지 않는다. 역방향 참조는 포트 인터페이스를 통해서만 성립한다.

### 4.2 공개 API

```kotlin
package io.github.loje0611.tennisdoc.feature.match

@Composable
fun PracticeScreen(
    viewModel: MatchViewModel,                                  // 기본값 없음
    contentPadding: PaddingValues = PaddingValues(0.dp),
)

@HiltViewModel
class MatchViewModel @Inject constructor(/* 포트 */) : ViewModel() {
    // 상태 5종: connectionState, detectedSwingLabel, swingCount,
    //           sessionDurationSeconds, isDebugModeEnabled
    // 동작 4종: scanAndConnect, disconnect, onDebugActivationAreaTap, simulateSwing
}
```

`connectionState`의 원소 타입은 `core.sensor.BleConnectionState`를 유지한다.

---

## 5. UI/UX 요구사항

| 대상 | 요구 |
|---|---|
| **Match 화면 자체** | 시각적 결과물이 **변하지 않는다.** 색상·타이포그래피·간격·문구·애니메이션·레이싯 아이콘 표시를 그대로 유지한다. 이관이지 리디자인이 아니다 |
| **하단 내비게이션 바** | "Live" 항목이 사라지고 **History·Settings 두 항목**이 남는다. 남는 항목의 아이콘·선택 색상·굵기 변화 동작은 현재와 동일 |
| **앱 시작 화면** | History 화면 |
| **Settings 화면** | 디버그 활성화 제스처가 추가되나, **디버그 모드가 꺼진 평상시에는 시각적 변화가 없어야 한다.** 새 버튼이나 안내 문구를 노출하지 않는다(숨은 제스처의 성격을 유지) |
| **Engineering Mode · History Mock FAB** | 디버그 모드가 켜진 뒤의 표시·동작이 현재와 동일 |

---

## 6. 비기능 요구사항

- 빌드는 프로젝트 루트의 `./gradlew` wrapper만 사용한다.
- 라이브러리 버전은 `gradle/libs.versions.toml`을 통해서만 참조하고, **새 좌표·새 버전을 추가하지 않는다.**
- minSdk 24 / compileSdk 36 / Java 11 — 관례 플러그인이 제공하는 값을 따른다.
- 기존 빌드에 없던 새로운 **에러**를 도입하지 않는다.
- CameraX·MediaPipe 등 B/C그룹 의존성을 미리 도입하지 않는다.

---

## 7. 오류 처리 및 엣지 케이스

| 상황 | 요구 동작 |
|---|---|
| 앱 최초 실행 | History 화면이 뜬다. 존재하지 않는 라우트를 참조해 크래시하지 않는다 |
| 디버그 모드 꺼짐 | Settings에 Engineering Mode 진입점이 보이지 않고, History에 Mock FAB이 보이지 않는다(현재와 동일) |
| Settings에서 10회 미만 탭 | 디버그 모드가 켜지지 않는다 |
| 디버그 모드가 이미 켜진 상태에서 추가 탭 | 아무 일도 일어나지 않는다(토글되어 꺼지지 않는다) |
| `simulateSwing` 호출 시 디버그 모드 꺼짐 | 아무 일도 하지 않는다(현재 동작 보존) |
| 포트 바인딩 누락 | **컴파일 시점에 실패해야 한다.** 런타임에만 드러나는 구성은 허용하지 않는다(FR-7) |

---

## 8. 인수 조건 (Acceptance Criteria)

> 모든 명령은 `TennisDocAI/`에서 실행한다.

- [ ] **AC-1** `./gradlew assembleDebug` 성공.
- [ ] **AC-2** `./gradlew test` 성공, 실패 0건. 총 테스트 수가 직전 기준선 **60건 미만이 아니다.**
- [ ] **AC-3** `./gradlew verifyModuleDependencies verifyJniBindings` 성공.
- [ ] **AC-4** **(이관 완료)** `feature/match/src/main/` 아래에 `PracticeScreen.kt`와 `MatchViewModel.kt`가 존재하고 `package`가 `io.github.loje0611.tennisdoc.feature.match`이다. `app/.../ui/practice/`와 `app/.../MainViewModel.kt`가 **존재하지 않으며**, `MainViewModel`이라는 식별자가 저장소 전체에서 **0건**이다.
- [ ] **AC-5** **(리소스 동행)** `ic_neon_racket.png`가 `feature/match/src/main/res/` 아래에 있고 `app/src/main/res/` 아래에는 **없다.** `PracticeScreen`이 참조하는 `R`이 `:app`의 것이 아니다(`io.github.loje0611.tennisdoc.R` 문자열이 `feature/match/src/` 전체에서 0건).
- [ ] **AC-6** **(핵심 — 모듈 독립성)** `feature/match/build.gradle.kts`가 선언한 프로젝트 의존성이 `:core:model`·`:core:ui`·`:core:sensor` **3개뿐이다.** `feature/match/src/` 전체에서 `io.github.loje0611.tennisdoc.` 로 시작하는 참조가 `core.model`·`core.ui`·`core.sensor`·`feature.match` 접두사에만 해당한다. `session.`·`service.`·`navigation.`·`core.data.`·`core.analysis.` 참조가 **0건**이다. `import`뿐 아니라 **FQCN 직접 호출**도 검사한다(`PracticeScreen`은 실제로 `R`을 FQCN으로 참조하고 있었다).
- [ ] **AC-7** **(모듈 단독 빌드)** `./gradlew :feature:match:assembleDebug`가 **단독으로 성공한다.**
- [ ] **AC-8** **(Hilt 생성물)** `feature/match/build/generated/` 하위에 `MatchViewModel` 대응 Hilt 생성 클래스(`MatchViewModel_Factory`, `MatchViewModel_HiltModules` 등)가 존재한다.
- [ ] **AC-9** **(라우트 부재)** `AppRoutes`에 `PRACTICE` 상수가 없고, `AppNavHost`에 `PracticeScreen` 참조와 "Live" 내비게이션 항목이 없다. `NavHost`의 `startDestination`이 `AppRoutes.HISTORY`이다. 저장소 전체에서 `"practice"` 라우트 문자열이 내비게이션 목적으로 남아 있지 않다.
- [ ] **AC-10** **(변이 검증 — 보존이 빌드로 강제됨)** `:app`에서 포트 구현의 Hilt 바인딩(`@Binds`/`@Provides`)을 **일시적으로** 제거하면 `:app` 빌드가 **실패**해야 한다. 이는 `:feature:match`가 죽은 모듈이 아니라 여전히 검증되는 자산임을 보인다. 확인 후 반드시 원복한다.
- [ ] **AC-11** **(변이 검증 — 의존성 규칙)** `feature/match/build.gradle.kts`에 `implementation(project(":core:data"))`를 **일시적으로** 추가해도 `verifyModuleDependencies`는 통과한다(허용 집합 내). 대신 **`implementation(project(":feature:history"))`** 를 일시 추가하면 **실패**해야 한다. 확인 후 반드시 원복한다.
- [ ] **AC-12** **(디버그 활성화 경로 — 코드 확인)** Settings 화면의 10회 탭 활성화 경로를 코드로 추적해 QA 리포트에 근거를 남긴다. ⑴ 탭 대상이 **디버그 모드와 무관하게 항상 표시되는 영역**임 ⑵ 10회에서 `setDebugMode(true)`에 도달함 ⑶ 이미 켜진 상태에서 꺼지지 않음 ⑷ 임계값 10이 **한 곳에만 정의**되어 있고 Match 경로와 공유됨.
- [ ] **AC-13** **(동작 보존 — 코드 확인)** `MatchViewModel`의 4개 동작이 포트를 통해 기존과 동일한 대상에 도달함을 코드로 확인한다. 특히 `simulateSwing`의 ⑴ 디버그 모드 꺼짐 → 무동작 ⑵ 파이프라인 실행 중 → 서비스 요청 ⑶ 그 외 → 상태 직접 갱신 **3분기가 보존**되어야 한다.
- [ ] **AC-14** `settings.gradle.kts`의 `include` 목록과 루트 `build.gradle.kts`의 허용 맵에 변경이 없다.
- [ ] **AC-15** `gradle/libs.versions.toml`에 **새 라이브러리 좌표나 새 버전이 추가되지 않았다.**
- [ ] **AC-16** Room 스키마(`**/schemas/**`)에 변경이 없다. `:feature:history`·`:core:*` 모듈의 소스에 변경이 없다(본 task는 이 모듈들을 건드리지 않는다).
- [ ] **AC-17** 변경 경로가 `TennisDocAI/` 내부에 한정된다. `docs/` 하위에 변경이 없다.

---

## 9. 테스트 지침 (Testing Instructions)

`TennisDocAI/AI_README.md`와 일치하는 명령을 사용한다. 모두 `TennisDocAI/`에서 실행한다.

```bash
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
./gradlew :feature:match:assembleDebug          # AC-7
```

계측 테스트(`./gradlew connectedAndroidTest`)는 실기기가 필요하므로 **실행하지 않아도 된다.** 미실행은 "검증 불가"가 아니다.

### 9.1 검증 시 주의사항

- **AC-9와 AC-12를 반드시 함께 보십시오.** 라우트 제거(AC-9)만 확인하고 통과시키면, 이 명세가 존재하는 이유인 **디버그 모드 사망**을 그대로 승인하게 됩니다. 빌드도 테스트도 이 손실을 잡지 못합니다.
- **`startDestination` 누락은 빌드로 드러나지 않습니다.** 존재하지 않는 라우트를 시작 지점으로 두면 **실행 즉시 크래시**하지만 컴파일은 통과합니다. `AppNavHost`의 `startDestination` 인자를 눈으로 확인하십시오.
- **`test`는 테스트가 없는 모듈에서도 성공합니다.** `:feature:match`에는 단위 테스트가 없을 수 있으므로 이 모듈의 `test` 성공을 근거로 삼지 마십시오. 실질 근거는 AC-6·AC-7·AC-8·AC-10입니다.
- **AC-10이 "보존"의 실질입니다.** 모듈이 컴파일만 되고 앱 그래프에서 떨어져 나가면, 이후 어떤 변경이 Match를 깨뜨려도 아무도 모릅니다. 바인딩 제거가 실제로 빌드를 깨는지 확인하십시오.
- 변이(AC-10·AC-11)는 반드시 **원복**하고, 원복 후 AC-1~AC-3 재통과를 확인하십시오.
- `git mv`로 파일을 옮기면 이동임이 드러나 검증이 쉬워집니다(강제 사항 아님).

---

## 10. 완료 정의

AC-1 ~ AC-17 전부 충족. 특히 **AC-6**(모듈 독립성) · **AC-9**(라우트 부재) · **AC-10**(보존의 빌드 강제) · **AC-12**(디버그 활성화 경로 보존)가 충족되지 않으면 다른 조건이 모두 통과해도 완료가 아니다.

본 task가 `DONE`이 되면 **A그룹(TASK-009~018)이 종료**되며, 이후는 PHASE2_PLAN §5의 B그룹(TASK-019~023, `:core:vision` 비전 알고리즘 포팅)으로 넘어간다.
