# TASK-010 — `:core:ui` 추출 (테마 + 공용 Compose 컴포넌트)

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-06 | PM | 최초 작성 |

---

## 1. Overview & Scope

### 1.1 목적

TennisDocAI 앱의 **디자인 시스템(테마·타이포그래피·색상 토큰)과 도메인 비의존 공용 UI 유틸리티**를 `:app` 모듈에서 `:core:ui` 라이브러리 모듈로 이관한다.

TASK-009가 9개 모듈의 **빈 스캐폴딩**을 생성했으므로, 본 task는 그중 `:core:ui`에 **실제 내용을 채우는 첫 이관 작업**이다.

### 1.2 왜 `:core:ui`가 먼저인가

이후 모든 feature 모듈(`:feature:history`, `:feature:match`, `:feature:lab`)은 화면을 그리기 위해 테마에 의존한다. 테마가 `:app`에 남아 있으면 feature 모듈이 `:app`을 참조해야 하는데, 이는 **의존 방향 역전**(app → feature 이어야 함)이라 순환이 발생한다. 따라서 테마 추출이 A그룹 이관의 **선행 조건**이다.

### 1.3 범위 (In Scope)

- 디자인 시스템 파일 4종을 `:core:ui`로 이동: `Color.kt`, `TennisDocColorScheme.kt`, `Theme.kt`, `Type.kt`
- 도메인 비의존 포맷터 `SwingLabelFormatter.kt`를 `:core:ui`로 이동
- 위 이동에 따른 **패키지 선언·import 갱신** 및 `:app`의 모듈 의존성 추가
- 테마 진입점 Composable의 **잔존 구 브랜드 명칭 제거**(FR-4)

### 1.4 범위 밖 (Out of Scope) — 명시적으로 건드리지 말 것

| 대상 | 사유 |
|---|---|
| `ui/history/**`, `ui/practice/**`, `ui/settings/**` 화면 및 ViewModel | 각각 TASK-014(`:feature:history`), TASK-015(`:feature:match`)가 이관을 담당 |
| `HexagonalRadarChart`, `DeltaSummaryChips`, `SwingCategoryUi` | 이력 화면 전용 컴포넌트이므로 TASK-014에서 `:feature:history`로 이동 |
| `:core:sensor`, `:core:data`, `:core:analysis`, `:core:vision` 내용 채우기 | TASK-011~013, TASK-016 소관 |
| `service/`, `session/` 패키지의 소속 변경 | `PHASE2_PLAN.md` §9에 따라 Phase 3까지 `:app` 잔류 |
| 색상 값·타이포그래피 **수치 자체의 변경** | 본 task는 **이동**이며 디자인 변경이 아니다 |

> **중요**: 본 task는 **리팩터링(이동)**이다. 화면의 렌더링 결과가 이관 전후로 **동일**해야 한다.

---

## 2. Definitions & References

### 2.1 용어

| 용어 | 정의 |
|---|---|
| **디자인 시스템** | 색상 토큰, 색상 스킴, 타이포그래피, 테마 진입점 Composable의 총칭 |
| **도메인 비의존** | 테니스 스윙 도메인 타입(예: 스윙 분류 키, 세션 엔티티)에 의존하지 않는 코드 |
| **모듈 의존 방향** | `:app` → `:feature:*` → `:core:*` 의 단방향. 역방향 및 `:core:*` 간 순환은 금지 |

### 2.2 현재 위치 (이관 전)

모든 경로는 `TennisDocAI/app/src/main/java/io/github/loje0611/tennisdoc/` 기준이다.

| 파일 | 현재 패키지 |
|---|---|
| `ui/theme/Color.kt` | `io.github.loje0611.tennisdoc.ui.theme` |
| `ui/theme/TennisDocColorScheme.kt` | `io.github.loje0611.tennisdoc.ui.theme` |
| `ui/theme/Theme.kt` | `io.github.loje0611.tennisdoc.ui.theme` |
| `ui/theme/Type.kt` | `io.github.loje0611.tennisdoc.ui.theme` |
| `ui/SwingLabelFormatter.kt` | `io.github.loje0611.tennisdoc.ui` |

### 2.3 `:core:ui` 모듈 정보 (TASK-009 산출물)

- 빌드 스크립트: `TennisDocAI/core/ui/build.gradle.kts`
- 적용 컨벤션 플러그인: `tennisdoc.android.library.compose`
- 선언된 `namespace`: `io.github.loje0611.tennisdoc.core.ui`
- 현재 소스 없음(빈 스캐폴딩)

### 2.4 관련 문서

- `docs/PHASE2_PLAN.md` §4 — A그룹 task 목록 및 검증 방법
- `docs/PRODUCT_DIRECTION.md` D-8(개명), D-9(모듈 경계)
- `TennisDocAI/AI_README.md` — 빌드·테스트 명령

---

## 3. Functional Requirements

### FR-1. 디자인 시스템 파일 이관

다음 4개 파일을 `:app`에서 `:core:ui` 모듈의 소스 세트로 이동한다.

| 파일 | 이동 후 패키지 |
|---|---|
| `Color.kt` | `io.github.loje0611.tennisdoc.core.ui.theme` |
| `TennisDocColorScheme.kt` | `io.github.loje0611.tennisdoc.core.ui.theme` |
| `Theme.kt` | `io.github.loje0611.tennisdoc.core.ui.theme` |
| `Type.kt` | `io.github.loje0611.tennisdoc.core.ui.theme` |

- 이동 후 `:app`에는 해당 파일이 **존재하지 않아야 한다**(복제 금지).
- 각 파일의 `package` 선언을 이동 후 패키지로 갱신한다.
- 패키지 경로는 `:core:ui` 모듈의 `namespace`(`io.github.loje0611.tennisdoc.core.ui`) 하위여야 한다.

### FR-2. 공개 API 보존

이관 후에도 아래 심볼이 **`:core:ui`의 공개(public) API로 존재**해야 하며, 다른 모듈에서 참조 가능해야 한다. (FR-4가 규정하는 이름 변경은 예외)

| 심볼 | 종류 | 역할 |
|---|---|---|
| `TennisDocColorScheme` | `data class` | 커스텀 색상 토큰 집합 |
| `DarkSwingColors` | `val` | 다크 모드 색상 인스턴스 |
| `LightSwingColors` | `val` | 라이트 모드 색상 인스턴스 |
| `LocalTennisDocColorScheme` | `CompositionLocal` | 색상 스킴 주입 통로 |
| `SwingTheme` | `object` | Composable에서 현재 색상에 접근하는 accessor |
| `Typography` | `val` | Material3 타이포그래피 |
| 색상 토큰 (`NeonGreen`, `ElectricBlue`, `NavyDeep` 등 `Color.kt`의 최상위 `val`) | `val` | 원시 색상 상수 |

- 기존에 `public`이던 심볼을 `internal`/`private`로 축소하지 않는다. 축소하면 후속 feature 모듈이 컴파일되지 않는다.

### FR-3. 색상·타이포그래피 값 불변

이관 과정에서 **색상 채널 값과 타이포그래피 수치를 변경하지 않는다.**

- `TennisDocColorScheme`의 각 필드에 대해, `DarkSwingColors`/`LightSwingColors`가 갖는 색상 값은 이관 전과 **정확히 동일**해야 한다.
- `Typography`의 각 텍스트 스타일이 갖는 `fontSize`, `fontWeight`, `lineHeight`, `letterSpacing` 값은 이관 전과 **정확히 동일**해야 한다.

### FR-4. 테마 진입점 명칭 정규화

현재 테마 진입점 Composable의 이름은 `SwingSenseAITheme`로, **개명 전 구 브랜드명이 잔존**한 상태다. 이를 `TennisDocTheme`으로 변경한다.

- 함수 시그니처의 파라미터 구성(다크 모드 여부 플래그, `content` 람다)과 기본값 동작은 **변경하지 않는다**. 이름만 바꾼다.
- 호출부(예: `MainActivity`)를 새 이름으로 갱신한다.
- 변경 후 프로젝트 전체에 `SwingSenseAITheme` 식별자가 **남아 있지 않아야 한다**.

> 사유: TASK-009는 패키지·디렉토리·애플리케이션 ID를 개명했으나 이 심볼이 누락되었다. `:core:ui`는 이 심볼을 소유하는 모듈이므로 본 task가 정규화 지점이다.

### FR-5. `SwingLabelFormatter` 이관

`ui/SwingLabelFormatter.kt`를 `:core:ui`로 이동하고 패키지를 `io.github.loje0611.tennisdoc.core.ui`로 갱신한다.

- 이 파일은 표준 라이브러리(`java.util.Locale`) 외에 프로젝트 내부 의존이 없으므로, 이동으로 인해 `:core:ui`가 다른 모듈에 의존하게 되어서는 **안 된다**.
- 포맷 결과 문자열은 이관 전후 **동일**해야 한다(FR-8 참조).

### FR-6. 모듈 의존성 배선

- `:app`의 빌드 스크립트에 `:core:ui`에 대한 의존을 추가하여, `:app`의 코드가 이관된 심볼을 참조할 수 있게 한다.
- `:core:ui`는 `:app`, `:feature:*`, 그리고 다른 `:core:*` 모듈 중 **어느 것에도 의존하지 않아야 한다**. (Compose/AndroidX 등 외부 라이브러리 의존은 허용)
- 기존에 이 심볼들을 사용하던 `:app` 내 모든 파일의 `import` 문을 새 패키지로 갱신하여 컴파일이 성립해야 한다.

### FR-7. 빌드 무결성

- `./gradlew verifyModuleDependencies test assembleDebug`가 **성공(그린)**해야 한다.
- TASK-009가 도입한 `verifyModuleDependencies` 검증 태스크가 통과해야 한다(의존 방향 규칙 위반 없음).
- 기존 단위 테스트가 **하나도 깨지지 않아야 한다**.

### FR-8. 회귀 방지 테스트 추가

`:core:ui` 모듈에 아래를 검증하는 단위 테스트를 추가한다. (JVM 단위 테스트로 실행 가능해야 하며, 실기기/에뮬레이터를 요구해서는 안 된다)

- **FR-8.1**: `DarkSwingColors`와 `LightSwingColors`가 서로 다른 인스턴스이며, 각 색상 필드가 `TennisDocColorScheme`에 정의된 모든 필드에 대해 값을 갖는다(미초기화/기본값 누락이 없다).
- **FR-8.2**: `SwingLabelFormatter`가 대표 입력에 대해 기대 문자열을 반환한다. 입력·기대값은 이관 전 구현의 동작을 기준으로 한다.

> Compose UI 렌더링 테스트는 요구하지 않는다. 계측 환경이 없어도 검증 가능해야 한다.

---

## 4. Interfaces & Data Structures

### 4.1 이관 후 모듈 구조

```text
TennisDocAI/
├── app/
│   └── src/main/java/io/github/loje0611/tennisdoc/
│       ├── MainActivity.kt              (import 갱신, TennisDocTheme 호출)
│       └── ui/                          (theme/·SwingLabelFormatter.kt 제거됨)
│           ├── history/                 (TASK-014에서 이동 예정 — 본 task 유지)
│           ├── practice/                (TASK-015에서 이동 예정 — 본 task 유지)
│           └── settings/                (본 task 유지)
└── core/ui/
    ├── build.gradle.kts
    └── src/
        ├── main/java/io/github/loje0611/tennisdoc/core/ui/
        │   ├── SwingLabelFormatter.kt
        │   └── theme/
        │       ├── Color.kt
        │       ├── TennisDocColorScheme.kt
        │       ├── Theme.kt              (TennisDocTheme)
        │       └── Type.kt
        └── test/java/io/github/loje0611/tennisdoc/core/ui/
            └── (FR-8 테스트)
```

### 4.2 테마 진입점 시그니처

```kotlin
@Composable
fun TennisDocTheme(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit,
)
```

- 파라미터 이름·순서·기본값은 이관 전 `SwingSenseAITheme`과 동일하게 유지한다.

### 4.3 모듈 의존 방향

```text
:app ──▶ :core:ui
```

`:core:ui`에서 나가는 프로젝트 내부 의존은 **없어야 한다**.

---

## 5. UI/UX Requirements

**시각적 변경 없음 (no-op refactor).**

본 task는 코드 배치 변경이며, 사용자에게 보이는 화면은 이관 전후 **동일**해야 한다.

- 다크/라이트 모드 전환 동작이 이관 전과 동일하게 유지되어야 한다.
- 시스템 바(상태 바·내비게이션 바) 색상이 테마 배경색을 따르는 기존 동작이 유지되어야 한다.
- 색상·폰트 크기·자간 등 어떤 디자인 값도 변경하지 않는다(FR-3).

---

## 6. Non-Functional Requirements

| 항목 | 요구사항 |
|---|---|
| 언어/빌드 | Kotlin, Gradle Kotlin DSL |
| 모듈 타입 | `:core:ui`는 Android Library + Compose (`tennisdoc.android.library.compose` 컨벤션 플러그인 사용) |
| 신규 외부 의존성 | **추가하지 않는다.** 기존 버전 카탈로그(`gradle/libs.versions.toml`)에 등록된 의존성만 사용 |
| 버전 하드코딩 | 금지. 의존성은 반드시 버전 카탈로그를 경유 |
| `namespace` | `:core:ui`의 기존 `namespace` 값을 변경하지 않는다 |
| minSdk / compileSdk | 기존 프로젝트 설정을 따른다(변경 금지) |
| 코드 스타일 | 기존 프로젝트 컨벤션 유지. 불필요한 포맷 변경으로 diff를 키우지 않는다 |

---

## 7. Error Handling & Edge Cases

| 상황 | 요구 동작 |
|---|---|
| 이관 대상 심볼을 참조하던 `:app` 파일이 컴파일 실패 | `import`를 새 패키지로 갱신하여 해결한다. **심볼을 `:app`에 복제하여 우회하지 않는다.** |
| `SwingSenseAITheme` 참조가 일부 남음 | FR-4에 따라 전부 `TennisDocTheme`으로 갱신. 별칭(typealias)을 만들어 구 이름을 살려두지 않는다 |
| `:core:ui`가 다른 모듈 심볼을 필요로 하는 상황 발견 | 해당 심볼은 도메인 의존이므로 `:core:ui`로 옮기지 않는다. 그 파일은 `:app`에 잔류시키고, 이관 범위에서 제외한 뒤 그 사실을 명확히 한다 |
| 이동 대상 파일이 이미 부분적으로 이동되어 있음 | 최종 상태(FR-1~FR-5)를 만족시키되, 동일 심볼이 두 모듈에 **중복 정의되지 않도록** 한다 |
| Compose 프리뷰(`@Preview`)가 테마를 참조하며 깨짐 | 새 이름·새 패키지로 갱신한다 |

---

## 8. Acceptance Criteria

- [ ] **AC-1**: `Color.kt`, `TennisDocColorScheme.kt`, `Theme.kt`, `Type.kt`, `SwingLabelFormatter.kt`가 `:core:ui` 모듈의 소스 세트에 존재한다.
- [ ] **AC-2**: 위 5개 파일이 `:app` 모듈에 더 이상 존재하지 않는다(중복 정의 없음).
- [ ] **AC-3**: FR-2 표에 나열된 모든 심볼이 `:core:ui`의 public API로 노출되어, 다른 모듈에서 참조 가능하다.
- [ ] **AC-4**: 프로젝트 전체 소스에서 식별자 `SwingSenseAITheme`가 검색되지 않는다.
- [ ] **AC-5**: 테마 진입점이 `TennisDocTheme`이라는 이름으로 존재하며, 다크 모드 플래그와 `content` 람다를 받는다.
- [ ] **AC-6**: `:core:ui`의 빌드 스크립트에 프로젝트 내부 모듈(`:app`, `:core:*`, `:feature:*`)에 대한 의존이 **하나도 없다**.
- [ ] **AC-7**: `:app`의 빌드 스크립트가 `:core:ui`에 의존한다.
- [ ] **AC-8**: `./gradlew verifyModuleDependencies test assembleDebug`가 성공 종료한다(exit code 0).
- [ ] **AC-9**: 이관 전 존재하던 단위 테스트가 모두 통과한다(신규 실패 0건).
- [ ] **AC-10**: `:core:ui`에 FR-8.1을 검증하는 테스트가 존재하고 통과한다 — 두 색상 스킴 인스턴스가 구별되며 모든 색상 필드가 값을 갖는다.
- [ ] **AC-11**: `:core:ui`에 FR-8.2를 검증하는 테스트가 존재하고 통과한다 — `SwingLabelFormatter`가 대표 입력에 대해 기대 문자열을 반환한다.
- [ ] **AC-12**: 신규 외부 라이브러리 의존성이 추가되지 않았고, 빌드 스크립트에 버전 문자열이 하드코딩되지 않았다.
- [ ] **AC-13**: `ui/history/`, `ui/practice/`, `ui/settings/` 하위 화면 파일은 `import` 갱신을 제외하면 이동·삭제되지 않았다(범위 밖 준수).

---

## 9. Testing Instructions

`TennisDocAI/AI_README.md`에 정의된 명령을 사용한다. **`TennisDocAI/` 디렉토리에서** 실행한다.

```bash
./gradlew verifyModuleDependencies test assembleDebug
```

- 특정 모듈만 실행: `./gradlew :core:ui:testDebugUnitTest`
- 계측 테스트(`connectedAndroidTest`)는 실기기가 필요하므로 **본 task의 판정 기준이 아니다.** 위 단위 테스트 명령 결과로 검증한다.
- AC-4(식별자 잔존 여부)와 AC-2(중복 정의 없음)는 소스 트리 검색으로 확인한다. 이는 구현 재확인이 아니라 **이관 완료 여부라는 관측 가능한 사실**에 대한 검증이다.
