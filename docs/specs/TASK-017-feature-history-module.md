# TASK-017 — `:feature:history` 모듈 신설 및 이력 화면 이관

| 항목 | 값 |
|---|---|
| Task ID | TASK-017 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-016 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §4, §8.2, §8.3 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-08 | PM | 최초 작성 |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 목적

이력(History) 기능의 화면·ViewModel 소스를 `:app`에서 **`:feature:history` 모듈로 물리적으로 이관**하고, 해당 모듈이 `{:core:model, :core:ui, :core:data}` 만으로 독립 컴파일되도록 만든다. A그룹(멀티모듈 분리)의 `:feature:*` 이관 3건 중 첫 번째다.

TASK-016은 **결합 해소만** 수행했고 파일 위치는 `:app`에 그대로 두었다. 본 task가 그 후속으로, 실제 모듈 이관과 **라이브러리 모듈에 대한 Hilt 도입**을 담당한다.

### 1.2 이 task가 다루는 미검증 인프라

이 저장소에는 **라이브러리 모듈(`com.android.library`)에 Hilt를 적용한 전례가 없다.** 현재 Hilt 플러그인·컴파일러는 `:app`에만 적용되어 있고, `:core:*` 모듈 어디에도 Hilt 의존성이 없다. 이관 대상에는 `@HiltViewModel` 두 개와 `hiltViewModel()` 호출이 포함되므로, 본 task는 **라이브러리 모듈에서 Hilt가 동작함을 실증하는 것**이 핵심 리스크다.

### 1.3 경계

| | 내용 |
|---|---|
| **포함** | `:feature:history` 빌드 스크립트 작성, 이력 화면 6개 파일 이관 및 패키지 변경, 라이브러리 Hilt 설정, `:app` 호출부 갱신, `HistoryScreen`의 `debugModeEnabled` 기본값 제거, `AI_README.md` 모듈 구조 갱신 |
| **제외** | 아래 §1.4 |

### 1.4 제외 (건드리지 말 것)

- **`:feature:match` · `:feature:lab` · `:core:vision` 이관** — TASK-018 이후의 범위. 본 task에서 이 모듈들에 소스를 추가하지 않는다.
- **화면의 시각적 디자인·레이아웃·문구 변경** — 이관이지 리디자인이 아니다. Composable 본문의 UI 코드는 `import`/패키지 변경과 §3의 FR이 명시한 시그니처 변경 외에 수정하지 않는다.
- **`SwingHistoryRepository`의 위치와 CSV 로직** — TASK-016에서 `:core:data`로 확정. 재이동하지 않는다.
- **`SwingHistoryRepository.CSV_TIMESTAMP_FORMAT`의 가시성 축소**(PHASE2_PLAN §8.2 부채) — `:core:data`의 문제이며 본 task의 모듈 경계와 무관하다. 부채로 남긴다.
- **ProGuard 규칙 중복·사문화 3줄 정리**(PHASE2_PLAN §8.2 부채) — 별도 정리 대상.
- **`service/` · `session/`의 모듈 소속** — D-9.1에서 Phase 3까지 유보. `:app`에 잔류.
- **Room 스키마(`app/schemas/**`, `core/data/schemas/**`) 및 DB 버전.**
- **`Theme.SwingSenseAI` 등 개명 잔재, `swingsense_export.csv` 파일명, `libswingsense_ei.so` 이름.**
- **`androidTest`(계측 테스트) 실행** — 실기기가 없으므로 컴파일만 만족하면 된다.

---

## 2. 정의 및 참조 (Definitions & References)

| 용어 | 의미 |
|---|---|
| **이관 대상 6개 파일** | `app/src/main/java/io/github/loje0611/tennisdoc/ui/history/` 아래의 `HistoryScreen.kt`, `HistoryViewModel.kt`, `SessionDetailScreen.kt`, `SessionDetailViewModel.kt`, `HexagonalRadarChart.kt`, `DeltaSummaryChips.kt` |
| **대상 패키지** | `io.github.loje0611.tennisdoc.feature.history` (모듈 namespace와 동일) |
| **`verifyModuleDependencies`** | 루트 `build.gradle.kts`에 등록된 검증 태스크. 모듈별 허용 의존성 맵과 실제 `ProjectDependency`를 대조해 위반 시 빌드를 실패시킨다 |
| **`verifyJniBindings`** | 4개 ABI의 `libswingsense_ei.so`에 `JNI_OnLoad`와 기대 클래스 디스크립터가 존재하는지 검사하는 태스크 |
| **관례 플러그인** | `build-logic/convention/`의 `tennisdoc.android.library`(compileSdk 36 · minSdk 24 · Java 11)와 `tennisdoc.android.library.compose`(그 위에 Compose + BOM · ui · tooling-preview · material3 추가) |

**참조 파일**

- 모듈 목록: `TennisDocAI/settings.gradle.kts` — `:feature:history`는 **TASK-009에서 이미 `include`되어 있고** `feature/history/build.gradle.kts`가 존재하며 소스가 0개인 빈 모듈이다.
- 허용 의존성 맵: `TennisDocAI/build.gradle.kts` — `":feature:history" to setOf(":core:model", ":core:ui", ":core:data")`가 **이미 선언되어 있어 맵 수정이 불필요하다.**
- 호출부: `app/src/main/java/io/github/loje0611/tennisdoc/navigation/AppNavHost.kt` (`AppRoutes.HISTORY`, `AppRoutes.SESSION_DETAIL` 라우트)
- DI 바인딩: `app/src/main/java/io/github/loje0611/tennisdoc/di/AppModule.kt` (`SwingHistoryRepository` 제공, `CoachingCommentGenerator` 바인딩)
- 버전 카탈로그: `TennisDocAI/gradle/libs.versions.toml` — `hilt-android`, `hilt-compiler`, `hilt-navigation-compose`, `androidx-compose-foundation`, `androidx-compose-material-icons-extended`, `androidx-lifecycle-runtime-compose`, `androidx-lifecycle-viewmodel-compose`, `kotlinx-coroutines-android`, `junit`, `hilt-android-plugin`, `google-ksp` 별칭이 **모두 이미 존재한다.** 새 라이브러리 좌표를 추가할 필요가 없다.

**이관 대상이 참조하는 외부 심볼(TASK-016 완료 시점 기준)**

| 심볼 | 소속 모듈 |
|---|---|
| `core.model.SwingMetrics`, `core.model.CoachingCommentGenerator` | `:core:model` |
| `core.ui.theme.SwingTheme`, `core.ui.theme.MichromaFont`, `core.ui.formatDurationMillis`, `core.ui`의 카테고리 색상 헬퍼 | `:core:ui` |
| `core.data.db.entity.SwingSessionEntity`, `SwingEventEntity`, `SessionSwingCountEntity`, `core.data.repository.SwingHistoryRepository`, `SessionDetailData` | `:core:data` |

`:app` 고유 심볼(`navigation.*`, `session.*`, `R`) 참조는 TASK-016의 AC-4로 이미 0건임이 검증되었다.

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1. `:feature:history` 빌드 스크립트 구성

`feature/history/build.gradle.kts`를 다음 요건을 만족하도록 작성한다.

- `tennisdoc.android.library.compose` 관례 플러그인을 적용한다. compileSdk·minSdk·Java 버전·Compose 설정을 **개별 모듈에서 재선언하지 않는다** — 관례 플러그인이 이미 제공한다.
- `namespace`는 `io.github.loje0611.tennisdoc.feature.history`를 유지한다.
- Hilt를 사용하기 위해 Hilt Gradle 플러그인과 KSP 플러그인을 적용하고, `hilt-android`(implementation) 및 `hilt-compiler`(ksp)를 선언한다. 버전은 **버전 카탈로그의 기존 별칭을 통해서만** 참조하고, 카탈로그에 버전을 새로 추가하거나 하드코딩하지 않는다.
- 프로젝트 의존성은 `:core:model`, `:core:ui`, `:core:data` **세 개로 한정한다.** `:core:analysis`, `:core:sensor`, `:core:vision`, `:app`, 다른 `:feature:*`에 대한 의존성을 선언하지 않는다.
- 이관된 소스가 컴파일되는 데 필요한 외부 라이브러리(Compose foundation, material-icons-extended, lifecycle-runtime-compose, lifecycle-viewmodel-compose, hilt-navigation-compose, coroutines 등)를 카탈로그 별칭으로 선언한다. **실제로 필요한 것만 선언한다** — 사용하지 않는 의존성을 관례적으로 복사해 넣지 않는다.
- `testImplementation(libs.junit)`을 선언한다.

> **왜 관례 플러그인을 쓰는가**: `compileSdk`·`minSdk`를 모듈마다 적으면 SDK 상향 시 갱신 누락이 발생하고, 그 결과가 빌드 실패가 아니라 **모듈별 동작 차이**로 나타나 추적이 어렵습니다. TASK-010~015의 5개 모듈이 모두 관례 플러그인을 쓰고 있으므로 일관성을 지킵니다.

### FR-2. 소스 파일 6개 이관 및 패키지 변경

§2에 열거한 6개 파일을 `feature/history/src/main/java/io/github/loje0611/tennisdoc/feature/history/` 아래로 옮기고, 각 파일의 `package` 선언을 `io.github.loje0611.tennisdoc.feature.history`로 변경한다.

- **`:app` 아래 `ui/history/` 디렉터리는 남지 않아야 한다** (빈 디렉터리 포함).
- 파일명·클래스명·Composable 함수명·공개 데이터 클래스명(`CategoryAnalysisData`, `SessionDetailUiState`, `HistoryUiState` 등)은 **변경하지 않는다.** 본 task는 이동이지 개명이 아니다.
- 파일 내용 변경은 ⑴ `package` 선언 ⑵ `import` 조정 ⑶ FR-3·FR-4가 지시하는 시그니처 변경 세 가지에 한정한다.

### FR-3. `HistoryScreen`의 `debugModeEnabled` 기본값 제거

현재 시그니처는 다음과 같다.

```kotlin
@Composable
fun HistoryScreen(
    onNavigateToSessionDetail: (String) -> Unit,
    viewModel: HistoryViewModel,
    debugModeEnabled: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
)
```

`debugModeEnabled`의 **기본값 `= false`를 제거해 필수 인자로 만든다.** `onNavigateToSessionDetail`·`viewModel`은 이미 필수이며, `contentPadding`의 기본값은 유지한다.

> **왜 필요한가**(PHASE2_PLAN §8.2): 기본값이 있으면 호출자가 전달을 누락해도 컴파일이 통과하고 **디버그 UI가 조용히 꺼집니다.** 본 task에서 화면이 모듈을 넘어가며 호출 지점이 바뀌므로, 누락이 실제로 발생할 수 있는 시점이 바로 지금입니다. 컴파일러가 잡도록 만듭니다.

호출부(`AppNavHost`)는 이미 값을 명시적으로 전달하고 있으므로 **동작은 달라지지 않아야 한다.**

### FR-4. `SessionDetailScreen`의 ViewModel 획득 방식

`SessionDetailScreen`은 현재 파라미터가 아니라 본문에서 `hiltViewModel()`로 `SessionDetailViewModel`을 얻는다. 이 구조를 **다음 중 하나로 만족시킨다.**

- (a) `hiltViewModel()`을 라이브러리 모듈에서 그대로 사용하되, `hilt-navigation-compose` 의존성을 `:feature:history`에 선언해 컴파일과 런타임 주입이 성립하게 한다. 또는
- (b) `HistoryScreen`과 동일하게 ViewModel을 **파라미터로 받고**, `hiltViewModel()` 호출을 `:app`의 `AppNavHost`로 올린다.

**어느 쪽을 택하든 `SessionDetailViewModel`은 `SavedStateHandle`로 `sessionId` 내비게이션 인자를 계속 받아야 하며, 세션 상세 화면이 올바른 세션을 표시하는 동작이 보존되어야 한다.** (b)를 택할 경우 `SavedStateHandle`이 해당 라우트의 `NavBackStackEntry`에서 채워지도록 ViewModel을 그 진입점 스코프에서 획득해야 한다 — 잘못된 스코프에서 얻으면 `sessionId`가 비어 상세 화면이 항상 빈 상태가 된다.

선택한 방식과 그 근거를 코드 주석이 아니라 **커밋 메시지 또는 `AI_README.md`에 남길 필요는 없으나, Tester가 확인할 수 있도록 구현이 일관되어야 한다.**

### FR-5. Hilt 주입 경로의 유지

이관된 `HistoryViewModel`·`SessionDetailViewModel`은 `@HiltViewModel` + `@Inject constructor`를 유지한다. 이들이 요구하는 의존성의 제공처는 **`:app`의 `AppModule`에 그대로 둔다.**

- `SwingHistoryRepository`는 `AppModule`이 `TennisDocDatabase`로부터 제공한다.
- `CoachingCommentGenerator`는 `AppModule`이 `:app`의 구현체에 바인딩한다.

라이브러리 모듈의 `@HiltViewModel`은 애플리케이션 컴포넌트에서 집계되므로, **`:feature:history`에 별도의 Hilt 모듈을 신설하지 않는다.** 제공자를 feature 모듈로 복제하면 동일 타입에 두 개의 바인딩이 생겨 빌드가 실패하거나, 더 나쁘게는 서로 다른 인스턴스가 공존한다.

### FR-6. `:app` 호출부 갱신

- `AppNavHost.kt`의 `import io.github.loje0611.tennisdoc.ui.history.*` 세 줄을 새 패키지로 갱신한다.
- `app/build.gradle.kts`에 `implementation(project(":feature:history"))`를 추가한다.
- `AppRoutes.HISTORY` 라우트는 `HistoryScreen`에 ⑴ 세션 상세로 이동하는 콜백 ⑵ `HistoryViewModel` ⑶ `SwingAnalysisSessionState.debugModeEnabled`의 현재 값 ⑷ `contentPadding`을 계속 전달한다. **전달 값의 의미가 현재와 달라지면 안 된다.**
- `AppRoutes.SESSION_DETAIL` 라우트는 `SessionDetailScreen`에 뒤로가기 콜백과 `contentPadding`을 계속 전달한다.

### FR-7. 의존성 규칙 검증의 실효성

`verifyModuleDependencies`의 허용 맵은 이미 올바르므로 **수정하지 않는다.** 다만 `:app`이 `:feature:history`를 새로 참조하게 되므로, 이 태스크가 여전히 통과함을 확인한다.

빈 모듈이던 `:feature:history`가 소스를 갖게 되었으므로, 이제부터 이 모듈의 `test`·`verifyModuleDependencies` 통과는 **실제 검증의 의미를 갖는다**(PHASE2_PLAN §8.2의 "빈 모듈의 초록색은 검증이 아니다" 경고가 이 모듈에는 더 이상 적용되지 않는다).

### FR-8. 문서 갱신

`TennisDocAI/AI_README.md`의 모듈 구조 설명에 `:feature:history`가 이력 화면을 보유한다는 사실을 반영한다. **`docs/` 하위 문서는 수정하지 않는다** — PM 소유 경로이며, 미커밋 상태로 남으면 경계 위반으로 판정된다(PHASE2_PLAN §8.1).

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

### 4.1 이관 후 모듈 의존 그래프

```
:app ──> :feature:history ──> :core:ui ──┐
  │            │                          ├──> :core:model
  │            └──────────> :core:data ──┘
  └──> :core:{model, ui, sensor, data, analysis}
```

`:feature:history`는 `:core:analysis`를 참조하지 않는다(TASK-016 FR-4의 의존성 역전으로 확보된 성질). 이는 이력 조회 기능이 **Edge Impulse NDK 빌드에 묶이지 않게** 하기 위한 것이다.

### 4.2 공개 API (이관 후에도 시그니처 보존)

```kotlin
package io.github.loje0611.tennisdoc.feature.history

@Composable
fun HistoryScreen(
    onNavigateToSessionDetail: (String) -> Unit,
    viewModel: HistoryViewModel,
    debugModeEnabled: Boolean,                                 // FR-3: 기본값 제거
    contentPadding: PaddingValues = PaddingValues(0.dp),
)

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    // FR-4에서 (b)를 택한 경우 ViewModel 파라미터가 추가될 수 있다
)

@HiltViewModel
class HistoryViewModel @Inject constructor(/* SwingHistoryRepository */) : ViewModel()

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    /* SavedStateHandle, SwingHistoryRepository, CoachingCommentGenerator */
) : ViewModel()
```

`:app`이 `HistoryViewModel` 타입을 `hiltViewModel()`의 타입 인자로 사용하므로, 이 타입이 `:app`의 컴파일 클래스패스에서 보여야 한다. 프로젝트 의존성 선언 방식(`implementation` / `api`)은 이 요건을 만족하는 한 구현자가 결정한다.

---

## 5. UI/UX 요구사항

**시각적 결과물의 변화가 없어야 한다.** 본 task는 소스 위치 이동이며, 사용자가 관찰하는 화면은 이관 전과 동일해야 한다.

| 화면 | 보존해야 할 동작 |
|---|---|
| 이력 목록 | 세션 카드 목록 표시, 카드 탭 시 해당 세션의 상세 화면으로 이동, 디버그 모드가 켜졌을 때만 Mock 데이터 생성 FAB 표시 |
| 세션 상세 | 진입한 세션의 데이터 표시, 뒤로가기, 육각 레이더 차트, 델타 요약 칩, 삭제 다이얼로그 |

색상·타이포그래피·간격·문구를 변경하지 않는다.

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

- **빌드 도구**: 프로젝트 루트의 `./gradlew` wrapper만 사용한다.
- **버전 관리**: 라이브러리 버전은 `gradle/libs.versions.toml`을 통해서만 참조한다. 빌드 스크립트에 버전 문자열을 직접 적지 않는다.
- **새 외부 의존성 도입 금지**: 카탈로그에 이미 있는 별칭만 사용한다. CameraX·MediaPipe 등 Phase 2 후반 의존성을 미리 도입하지 않는다.
- **컴파일 설정 일관성**: minSdk 24, compileSdk 36, Java 11 — 관례 플러그인이 제공하는 값을 따른다.
- **경고**: 기존 빌드에서 발생하지 않던 새로운 **에러**를 도입하지 않는다.

---

## 7. 오류 처리 및 엣지 케이스

| 상황 | 요구 동작 |
|---|---|
| 이력 세션이 0건 | 이관 전과 동일하게 처리된다(빈 목록 표시). 크래시하지 않는다. |
| `sessionId` 인자가 유효하지 않거나 해당 세션이 삭제됨 | 이관 전과 동일하게 처리된다. 새로운 크래시 경로를 만들지 않는다. |
| 디버그 모드 꺼짐 | Mock 생성 FAB이 표시되지 않는다. |
| Hilt 주입 실패 | **컴파일 시점에 드러나야 한다.** 런타임에만 실패하는 구성(예: feature 모듈에 중복 바인딩 모듈 신설)은 허용하지 않는다. |

---

## 8. 인수 조건 (Acceptance Criteria)

> 모든 명령은 `TennisDocAI/`에서 실행한다.

- [ ] **AC-1** `./gradlew assembleDebug` 성공.
- [ ] **AC-2** `./gradlew test` 성공, 실패 0건. 총 테스트 수가 직전 기준선 **60건 미만이 아니다.**
- [ ] **AC-3** `./gradlew verifyModuleDependencies verifyJniBindings` 성공.
- [ ] **AC-4** **(핵심 — 이관 완료)** `feature/history/src/main/` 아래에 §2가 열거한 6개 파일이 모두 존재하고, 각 파일의 `package` 선언이 `io.github.loje0611.tennisdoc.feature.history`이다. `app/src/main/java/io/github/loje0611/tennisdoc/ui/history/` 경로가 **존재하지 않는다.**
- [ ] **AC-5** **(핵심 — 모듈 독립성)** `feature/history/build.gradle.kts`가 선언한 프로젝트 의존성이 `:core:model`·`:core:ui`·`:core:data` **3개뿐이다.** 그리고 `feature/history/src/` 전체에서 `io.github.loje0611.tennisdoc.` 로 시작하는 참조가 `core.model`·`core.ui`·`core.data`·`feature.history` 접두사에만 해당한다. `core.analysis.`·`core.sensor.`·`navigation.`·`session.` 참조가 0건이다. `import` 목록뿐 아니라 **FQCN 직접 호출**도 함께 검사한다.
- [ ] **AC-6** **(핵심 — 모듈 단독 빌드)** `./gradlew :feature:history:assembleDebug`가 **단독으로 성공한다.** `:app`을 빌드하지 않고도 이 모듈이 컴파일된다.
- [ ] **AC-7** **(Hilt 실증)** 라이브러리 모듈에서 Hilt 애노테이션 처리가 실제로 수행됨을 보인다. `:feature:history`의 KSP/Hilt 생성 산출물에 이관된 두 ViewModel에 대응하는 생성 클래스가 존재함을 확인한다(예: `feature/history/build/generated/` 하위에서 `HistoryViewModel`·`SessionDetailViewModel` 관련 생성 파일 확인). 생성물이 전혀 없으면 Hilt 설정이 이름만 붙은 상태이므로 불합격이다.
- [ ] **AC-8** **(변이 검증 — 의존성 규칙)** `feature/history/build.gradle.kts`에 `implementation(project(":core:analysis"))`를 **일시적으로** 추가하면 `./gradlew verifyModuleDependencies`가 **실패**해야 한다. 확인 후 반드시 원복한다.
- [ ] **AC-9** **(변이 검증 — 필수 인자화)** `AppNavHost`의 `HistoryScreen(...)` 호출에서 `debugModeEnabled` 인자를 **일시적으로** 제거하면 컴파일이 **실패**해야 한다(FR-3이 실효적임을 보임). 확인 후 반드시 원복한다.
- [ ] **AC-10** **(동작 보존 — 코드 확인)** `AppNavHost`의 두 라우트 연결부를 코드로 직접 확인하고 그 근거를 QA 리포트에 남긴다. ⑴ 세션 카드 탭 콜백이 세션 상세 라우트로 이동하며 `sessionId`를 전달한다 ⑵ `debugModeEnabled`에 전역 디버그 상태의 현재 값이 전달된다 ⑶ 세션 상세의 `onBack`이 뒤로가기를 수행한다 ⑷ `SessionDetailViewModel`이 `sessionId`를 얻는 경로가 끊기지 않았다.
- [ ] **AC-11** `settings.gradle.kts`의 `include` 목록에 변경이 없다(`:feature:history`는 이미 등록되어 있다). 루트 `build.gradle.kts`의 `verifyModuleDependencies` 허용 맵에 변경이 없다.
- [ ] **AC-12** `gradle/libs.versions.toml`에 **새 라이브러리 좌표나 새 버전이 추가되지 않았다.** 기존 별칭 재사용만 허용된다.
- [ ] **AC-13** Room 스키마 파일(`**/schemas/**`)에 변경이 없다.
- [ ] **AC-14** 변경 경로가 `TennisDocAI/` 내부에 한정된다. `docs/` 하위에 변경이 없다.

---

## 9. 테스트 지침 (Testing Instructions)

`TennisDocAI/AI_README.md`와 일치하는 명령을 사용한다. 모두 `TennisDocAI/`에서 실행한다.

```bash
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
./gradlew :feature:history:assembleDebug        # AC-6
```

계측 테스트(`./gradlew connectedAndroidTest`)는 **실기기가 필요하므로 실행하지 않아도 된다.** 미실행은 "검증 불가"가 아니며, 위 명령의 성공으로 검증을 갈음한다.

### 9.1 검증 시 주의사항

- **AC-6이 본 task의 존재 이유입니다.** `:app`을 통해서만 컴파일되는 모듈은 분리된 것이 아닙니다. 반드시 모듈 단독 빌드로 확인하십시오.
- **`test`는 테스트가 없는 모듈에서도 성공합니다.** `:feature:history`에는 이관된 단위 테스트가 없을 수 있으므로, 이 모듈의 `test` 성공을 검증의 근거로 삼지 마십시오. 실질적 근거는 AC-6·AC-7·AC-8입니다.
- **Hilt가 "설정된 것처럼 보이는" 상태를 조심하십시오.** 플러그인만 적용하고 컴파일러(ksp)를 빠뜨려도 애노테이션은 그냥 무시되어 빌드가 통과합니다. AC-7이 이를 잡기 위한 조건입니다.
- **화면 동작 보존은 단위 테스트로 확인되지 않습니다.** AC-10처럼 연결부를 코드로 읽어 확인하고 근거를 리포트에 남기십시오.
- 변이 검증(AC-8·AC-9)은 반드시 **원복**하고, 원복 후 AC-1~AC-3 재통과를 확인하십시오.
- `git mv`로 파일을 옮기면 리뷰에서 이동임이 드러나 검증이 쉬워집니다(강제 사항은 아님).

---

## 10. 완료 정의

AC-1 ~ AC-14 전부 충족. 특히 **AC-4·AC-5·AC-6·AC-7**이 충족되지 않으면 다른 조건이 모두 통과해도 완료가 아니다.
