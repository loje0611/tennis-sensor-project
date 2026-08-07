# TASK-016 — `:feature:history` 추출 준비 (결합 해소 리팩터링)

| 항목 | 값 |
|---|---|
| Task ID | TASK-016 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-012, TASK-014, TASK-015 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §4, §8.2 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-07 | PM | 최초 작성 |

---

## 1. 배경

계획서는 `:feature:history`가 `{:core:ui, :core:data}`(+`:core:model`)만 참조한다고 가정했으나, **실제 코드를 조사한 결과 이관 대상 7개 파일이 허용되지 않는 참조를 4건 갖고 있습니다.** 하위 모듈은 `:app`을 참조할 수 없으므로 현 상태로는 모듈 신설이 불가능합니다.

| # | 결합 | 문제 |
|---|---|---|
| 1 | `SwingHistoryRepository`가 `:app`에 있음 | 두 ViewModel이 사용 |
| 2 | `SessionDetailViewModel` → `CoachingEngine` (`:core:analysis`) | 허용 집합 밖 |
| 3 | `HistoryScreen` → `AppRoutes`·`SwingAnalysisSessionState` (`:app`) | 구조적으로 참조 불가 |
| 4 | `accentColorForCategory`가 `ui/history/`에 있는데 `PracticeScreen`이 사용 | 모듈 분리 시 역참조 발생 |

**본 task는 모듈을 만들지 않습니다.** 위 4건을 해소해 `ui/history/`가 `:core:*`에만 의존하는 상태로 만드는 것이 전부이며, 실제 모듈 신설과 파일 이관은 **TASK-017**에서 수행합니다.

> **왜 나누는가**: 한 task에 ⑴ 저장소 이전 ⑵ 의존성 역전 ⑶ 화면 시그니처 변경 ⑷ 모듈 신설 ⑸ 라이브러리 모듈 Hilt 도입이 겹치면, 실패 시 원인을 특정할 수 없습니다. 특히 **라이브러리 모듈에 Hilt를 적용한 전례가 이 저장소에 없어** TASK-017은 미검증 인프라를 다룹니다. 분리해 두면 TASK-017이 `BLOCKED`되더라도 본 task의 결합 해소는 이미 커밋되어 안전합니다. 구 TASK-013을 013/014/015로 분해한 것과 같은 판단입니다.

## 2. 목적

`ui/history/` 7개 파일이 `:app` 고유 심볼과 `:core:analysis`를 참조하지 않게 만든다. 완료 시점에 이 파일들은 **위치만 `:app`일 뿐 내용상 `:feature:history`의 요건을 이미 충족**한다.

## 3. 범위

### 3.1 포함

| 대상 | 작업 |
|---|---|
| `SwingHistoryRepository` | `:app` → `:core:data`로 이전 + CSV 내보내기 분할 (FR-1·FR-2) |
| `SwingCategoryUi.kt` | `:app` → `:core:ui`로 이전 (FR-3) |
| 코칭 텍스트 생성 | 인터페이스를 `:core:model`에 신설, `:app`이 구현 주입 (FR-4) |
| `HistoryScreen` | `NavController`·`SwingAnalysisSessionState` 결합 제거 (FR-5) |
| `AppNavHost`·`AppModule`·`DeveloperSettingsViewModel` | 위 변경에 따른 호출부 갱신 |

### 3.2 제외 (건드리지 말 것)

- **모듈 신설·`settings.gradle.kts`·`verifyModuleDependencies` 맵** — TASK-017의 범위입니다. 본 task에서 새 모듈을 만들지 않습니다.
- **`ui/history/` 파일의 물리적 위치** — `:app` 안에 그대로 둡니다. 패키지명도 바꾸지 않습니다.
- **`service/`·`session/`의 모듈 소속** — D-9.1에서 Phase 3까지 유보. `SwingAnalysisSessionState` 자체는 이동하지 않습니다.
- **`CoachingEngine`의 위치** — `:core:analysis`에 그대로 둡니다(TASK-015 결과 유지).
- **내보내기 파일명 `swingsense_export.csv`** — 구 브랜드명이지만 사용자에게 노출되는 산출물명이라 개명은 별도 판단 사항입니다.
- **`Theme.SwingSenseAI`** 등 개명 잔재 — 별도 정리 task 대상.
- Room 스키마, DB 버전.

---

## 4. 기능 요구사항

### FR-1. `SwingHistoryRepository`를 `:core:data`로 이전
파일을 `core/data/src/main/java/.../core/data/repository/`로 옮기고 패키지를 **`io.github.loje0611.tennisdoc.core.data.repository`** 로 변경한다(`CalibrationStore`와 같은 위치). 함께 선언된 `SessionDetailData`도 동행한다.

`:core:data`의 허용 의존성은 `{:core:model}`이며 이 저장소는 `SwingMetrics`·`SwingClassificationKeys`만 추가로 필요하므로 규칙 위반이 없다. **`verifyModuleDependencies` 맵은 이미 `:core:data → {:core:model}`을 허용하므로 수정이 불필요하다.**

### FR-2. CSV 내보내기의 경계 분할
현재 `exportDataToCsv(context, ...)`는 ⑴ DB 조회 ⑵ CSV 문자열 생성 ⑶ 캐시 파일 쓰기 ⑷ `FileProvider` URI 생성을 한 함수에서 수행한다. 이 중 ⑶⑷를 `:app`으로 분리한다.

- **`:core:data`** — 저장소는 ⑴⑵만 담당하고 **CSV 문자열을 반환**하는 함수를 노출한다. `Context`·`Uri`·`FileProvider`·`File` 의존을 갖지 않는다.
- **`:app`** — 파일 쓰기와 `FileProvider` URI 생성을 담당하는 클래스를 신설하고, `DeveloperSettingsViewModel`이 이를 사용하도록 호출부를 바꾼다. Hilt로 제공한다.

**동작은 보존한다.** 11개 열의 헤더와 각 행의 서식(`%s,%s,%d,%d,%d,%d,%d,%d,%.2f,%d,%.1f`), 타임스탬프 형식(`yyyy-MM-dd HH:mm:ss`, `Locale.US`), 캐시 파일명, FileProvider authority(`${context.packageName}.fileprovider`)를 변경하지 않는다.

> **왜 통째로 옮기지 않는가**: `FileProvider`는 소비자 앱의 매니페스트에 `<provider>` 선언이 있어야 동작합니다. 저장소를 통째로 `:core:data`에 넣으면 이 모듈이 **매니페스트에 대한 암묵적 계약**을 갖게 되어, 선언이 없는 소비자에서 런타임에 실패합니다. 컴파일로는 드러나지 않는 종류의 결함입니다.
>
> **부수 효과가 본질적입니다**: 분할하면 CSV 생성이 Android 의존 없는 순수 로직이 되어 **JVM 단위 테스트로 검증할 수 있습니다.** 현재 이 로직은 검증 수단이 전혀 없습니다.

### FR-3. `SwingCategoryUi.kt`를 `:core:ui`로 이전
`accentColorForCategory`·`brushForCategory`는 `PracticeScreen`(`:app`)과 이력 화면이 함께 사용하는 공용 헬퍼이며, 이미 `:core:ui`의 `SwingTheme`에 의존한다. 파일을 `:core:ui`로 옮기고 패키지를 `io.github.loje0611.tennisdoc.core.ui`(또는 그 하위)로 변경한 뒤, 호출부의 `import`를 갱신한다.

`PracticeScreen`은 현재 FQCN으로 직접 호출(`io.github.loje0611.tennisdoc.ui.history.accentColorForCategory(...)`)하고 있으므로 **이 호출도 반드시 함께 갱신**한다.

### FR-4. 코칭 텍스트 생성의 의존성 역전
`SessionDetailViewModel`이 `CoachingEngine`을 직접 참조하지 않도록 인터페이스를 도입한다.

- **`:core:model`** — 코칭 문구 생성 인터페이스를 선언한다. 시그니처는 현재 호출 형태를 보존한다: 구종 키(`String`), 대상 지표(`SwingMetrics`), 비교 기준 지표(`SwingMetrics?`)를 받아 `String`을 반환.
- **`:app`** — 이 인터페이스를 `CoachingEngine`에 위임하는 구현을 두고 Hilt로 바인딩한다. `CoachingEngine`은 `object`이므로 구현체는 이를 호출하는 별도 클래스여야 한다.
- **`SessionDetailViewModel`** — 인터페이스를 생성자 주입으로 받는다.

**코칭 문구의 내용은 달라지지 않아야 한다.** `CoachingEngine`의 로직은 수정하지 않으며, 위임만 한다.

> `:feature:history`에 `:core:analysis` 참조를 허용하는 대안은 채택하지 않았습니다. `:core:analysis`는 **Edge Impulse NDK 빌드(약 29MB)** 를 포함하므로, 이력 조회 기능이 IMU 추론 네이티브 빌드에 묶이고 모듈 테스트가 매번 CMake에 종속됩니다. D-9.2가 `:core:vision`을 `:core:analysis`에서 분리한 이유와 동일합니다.

### FR-5. 화면의 `:app` 결합 제거
`HistoryScreen`에서 다음 두 참조를 제거한다.

1. **`NavController`** — 파라미터에서 없애고, 세션 선택을 상위로 알리는 **콜백(`(String) -> Unit` 형태)** 을 받는다. 실제 내비게이션(`AppRoutes.sessionDetail(...)` 호출)은 `AppNavHost`가 수행한다.
2. **`SwingAnalysisSessionState.debugModeEnabled`** — 화면이 전역 상태를 직접 읽지 않고, 디버그 모드 여부를 **파라미터로 받는다.** `AppNavHost`가 값을 공급한다.

`SessionDetailScreen`은 이미 `onBack: () -> Unit`을 받으므로 같은 방식이며 추가 조치가 필요 없다. 단, 내부에서 `hiltViewModel()`을 호출하는 구조는 TASK-017에서 Hilt 설정과 함께 다루므로 **본 task에서 바꾸지 않는다.**

**화면 동작은 보존한다.** 세션 카드를 눌렀을 때의 이동 대상, 디버그 모드에 따른 표시 차이가 현재와 같아야 한다.

### FR-6. 결합 해소의 완결성
위 작업 완료 시 `app/src/main/java/.../tennisdoc/ui/history/` 하위 파일들의 `io.github.loje0611.tennisdoc.*` import는 **`core.model`·`core.ui`·`core.data` 세 접두사만** 남아야 한다. `navigation.*`·`session.*`·`data.*`·`core.analysis.*`·`R` 참조가 남아 있으면 TASK-017이 착수 불가하므로, 이것이 본 task의 실질적 완료 기준이다.

### FR-7. 문서 갱신
본 task에서 수정이 허용된 문서는 `TennisDocAI/AI_README.md`뿐이며, 모듈 구조가 바뀌지 않으므로 **갱신이 불필요하면 수정하지 않아도 된다.** `docs/` 하위 문서는 수정하지 않는다.

---

## 5. 인수 조건 (Acceptance Criteria)

> 모든 명령은 `TennisDocAI/`에서 실행한다.

| # | 조건 |
|---|---|
| **AC-1** | `./gradlew assembleDebug` 성공. |
| **AC-2** | `./gradlew test` 성공, 실패 0건. 총 테스트 수가 직전 기준선 **57건 미만이 아니다**. |
| **AC-3** | `./gradlew verifyModuleDependencies verifyJniBindings` 성공. |
| **AC-4** | **(핵심)** `app/src/main/java/.../tennisdoc/ui/history/` 하위 `.kt` 파일 전체에서 `io.github.loje0611.tennisdoc.` 로 시작하는 참조가 `core.model`·`core.ui`·`core.data` 접두사에만 해당한다. `navigation.`·`session.`·`data.repository.`·`core.analysis.` 참조가 **0건**이다. FQCN 직접 호출(import 없는 전체 경로 표기)도 함께 검사한다. |
| **AC-5** | `SwingHistoryRepository`가 `core/data/` 아래에 존재하고, `:app` 아래에는 존재하지 않는다. |
| **AC-6** | `:core:data` 소스 전체에서 `FileProvider`·`android.net.Uri` 참조가 **0건**이다. |
| **AC-7** | **CSV 생성 로직이 Android 의존 없이 호출 가능함을 실증한다.** Tester가 `:core:data`의 JVM 단위 테스트로 CSV 문자열을 검증한다 — 헤더 11개 열이 기존과 동일하고, 이벤트 1건 이상이 지정된 서식으로 직렬화됨을 확인한다. |
| **AC-8** | `SessionDetailViewModel`이 `CoachingEngine`을 직접 참조하지 않는다(AC-4에 포함되나 명시적으로 확인). 코칭 문구 생성 인터페이스가 `:core:model`에 존재한다. |
| **AC-9** | `HistoryScreen`의 파라미터에 `NavController` 타입이 없고, 본문에서 `SwingAnalysisSessionState`를 참조하지 않는다. |
| **AC-10** | `PracticeScreen`이 `accentColorForCategory`를 `:core:ui` 경로로 호출한다. 저장소 전체에서 `ui.history.accentColorForCategory` 문자열이 **0건**이다. |
| **AC-11** | **(변이 검증 — 동작 보존)** 코칭 문구 위임이 실제로 `CoachingEngine`에 도달함을 보인다. `CoachingEngine.generateComment`를 일시 훼손하면 이를 검증하는 테스트가 **실패**해야 한다. 확인 후 원복한다. |
| **AC-12** | **(변이 검증 — CSV)** CSV 헤더 문자열이나 서식 문자열을 일시 훼손하면 AC-7의 테스트가 **실패**해야 한다. 확인 후 원복한다. |
| **AC-13** | Room 스키마(`app/schemas/**`)에 변경이 없다. `settings.gradle.kts`와 루트 `build.gradle.kts`에 변경이 없다(모듈 신설은 TASK-017 범위). |
| **AC-14** | 변경 경로가 `TennisDocAI/` 내부에 한정된다. |

---

## 6. 검증 시 주의사항

- **AC-4가 본 task의 존재 이유입니다.** 빌드가 통과해도 이 조건이 미충족이면 TASK-017을 착수할 수 없으므로 완료로 볼 수 없습니다. `import` 목록만 보지 말고 **FQCN 직접 호출**도 검사하십시오 — `PracticeScreen`이 실제로 그렇게 호출하고 있었습니다.
- **화면 동작 보존은 단위 테스트로 확인되지 않습니다.** Compose 화면의 시그니처가 바뀌므로 컴파일은 통과해도 `AppNavHost`가 콜백을 잘못 연결하면 세션 카드를 눌러도 이동하지 않거나 디버그 UI가 항상 꺼진 상태가 될 수 있습니다. `AppNavHost`의 연결부를 **코드로 직접 확인**하고 그 근거를 리포트에 남기십시오.
- **`test`는 테스트가 없는 모듈에서도 성공합니다.** AC-7의 테스트가 실제로 `:core:data`에서 실행되었는지 확인하십시오. AC-12의 변이가 이를 겸합니다.
- 계측 테스트(`androidTest`)는 실기기가 필요하므로 **실행하지 않아도 됩니다.** 미실행은 "검증 불가"가 아니며 컴파일 성공으로 충분합니다.
- `:core:data`에는 이미 Room 계측 테스트가 있습니다. **이 테스트를 수정하지 마십시오.** 저장소 이전으로 컴파일이 깨지면 `import`만 조정합니다.
- 변이(AC-11·AC-12)는 반드시 **원복**하고, 원복 후 AC-1~AC-3 재통과를 확인하십시오.

## 7. 완료 정의

AC-1 ~ AC-14 전부 충족. 특히 **AC-4**가 충족되지 않으면 다른 조건이 모두 통과해도 완료가 아니다.
