# TASK-014 — `:core:model` 신설 (공용 도메인 타입 분리)

| 항목 | 값 |
|---|---|
| Task ID | TASK-014 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-009 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §4, §4.2 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-07 | PM | 최초 작성 |

---

## 1. 배경

`SwingMetrics`(스윙 품질 6개 지표)와 `SwingClassificationKeys`(스윙 레이블 상수 + 정규화 유틸)는 현재 `:app`의 `io.github.loje0611.tennisdoc.analysis` 패키지에 있으나, 실제로는 **분석·저장·화면·서비스 네 계층이 공유하는 도메인 타입**입니다.

현행 아키텍처 규칙(`build.gradle.kts`의 `verifyModuleDependencies`)에서 `:core:data`는 허용 의존성이 **빈 집합**, `:core:analysis`는 `{:core:sensor}` 뿐입니다. **두 모듈이 서로를 참조할 수 없으므로 이 타입들이 놓일 자리가 없습니다.**

TASK-012에서는 `SwingHistoryRepository`를 `:app`에 남겨 이 문제를 우회했습니다. 그러나 **이 우회는 C그룹에서 통하지 않습니다.** `:feature:history`의 허용 의존성은 `{:core:ui, :core:data}`인데 히스토리 화면(`HexagonalRadarChart`·`DeltaSummaryChips`·`SessionDetailViewModel`)이 `SwingMetrics`를 사용합니다. 이 타입이 `:app`에 남아 있으면 하위 모듈은 `:app`을 참조할 수 없으므로 **`:feature:history`는 영원히 추출할 수 없습니다.**

따라서 본 task는 선택적 개선이 아니라 **TASK-015·TASK-016의 선행 조건**입니다.

## 2. 목적

Android 의존성이 없는 **순수 Kotlin(JVM) 모듈 `:core:model`** 을 신설하고, 위 두 타입을 이전한다. 이 모듈은 의존성 그래프의 **최하위 잎(leaf)** 으로서 어떤 모듈도 참조하지 않는다.

## 3. 범위

### 3.1 포함

| 대상 | 작업 |
|---|---|
| `settings.gradle.kts` | `include(":core:model")` 추가 |
| `core/model/build.gradle.kts` | 신규 — `tennisdoc.jvm.library` 컨벤션 플러그인 적용 |
| `SwingMetrics.kt` | `:app`의 `...tennisdoc.analysis` → `:core:model`의 `...tennisdoc.core.model` 로 이동 |
| `SwingClassificationKeys.kt` | 동일하게 이동 |
| `SwingClassificationKeysTest.kt` | `:core:model`의 테스트 소스셋으로 이동 |
| 참조 코드 | 이동에 따른 `import` 갱신 |
| `app/build.gradle.kts` | `implementation(project(":core:model"))` 추가 |
| `build.gradle.kts` | `verifyModuleDependencies`의 `allowedDeps` 갱신 |

### 3.2 제외 (건드리지 말 것)

- **`analysis` 패키지의 나머지 7개 파일** — `CoachingEngine`·`KinematicAnalyzer`·`VolleyDetector`·`SwingInferenceBuffer`·`SwingKinematicsBuffer`·`RawSwingTelemetry`·`EdgeImpulseInputSpec` 은 **TASK-015**에서 `:core:analysis`로 이동합니다. 본 task에서는 **`import` 추가 외의 수정을 하지 않습니다.**
- **`SwingHistoryRepository`의 모듈 이동** — 본 task 범위 밖입니다(`:app` 잔류). `import` 갱신만 합니다.
- **`:core:data`의 `SwingMetricsAvg`** — 이름이 비슷하지만 **`SwingMetrics`와 무관한 별개 타입**입니다. Room 쿼리 projection(`AVG(...)` 결과 수신용)이며 `:core:data` 내부에 그대로 둡니다. **이동·병합·참조 대상이 아닙니다.**
- Room 스키마, DB 버전, `schemas/` 디렉토리.
- JNI·CMake 관련 일체.

---

## 4. 기능 요구사항

### FR-1. 모듈 생성
`core/model/` 에 Gradle 서브프로젝트를 만들고 `settings.gradle.kts`에 `include(":core:model")`를 추가한다. 선언 위치는 기존 `:core:*` 목록과 함께 두어 가독성을 유지한다.

### FR-2. 순수 Kotlin(JVM) 모듈
`core/model/build.gradle.kts`는 기존 컨벤션 플러그인 **`tennisdoc.jvm.library`** 를 적용한다(현재 등록되어 있으나 사용처가 없는 상태). Android Gradle Plugin·`namespace`·`android { }` 블록을 사용하지 않는다.

> 이 모듈이 JVM 모듈이어야 하는 이유: Android 프레임워크에 의존하지 않음을 **빌드 시스템 차원에서 강제**하기 위함입니다. `android.*` import가 섞이면 컴파일 자체가 실패하므로, 규칙을 문서가 아니라 컴파일러가 지킵니다.

### FR-3. 타입 이동
`SwingMetrics.kt`·`SwingClassificationKeys.kt`를 `:core:model`로 이동하고 패키지 선언을 **`io.github.loje0611.tennisdoc.core.model`** 로 변경한다. `:app`의 기존 파일 2개는 삭제한다(양쪽에 남기지 않는다).

**두 타입의 public API(클래스명, 프로퍼티명과 순서, 함수 시그니처, 상수 값)는 변경하지 않는다.** 특히 `SwingClassificationKeys`의 7개 문자열 상수 값은 Edge Impulse 모델의 레이블과 일치해야 하므로 **한 글자도 바뀌어서는 안 된다.**

### FR-4. 테스트 이동
`SwingClassificationKeysTest.kt`를 `:core:model`의 테스트 소스셋으로 이동한다. **테스트 내용(단정문·기대값·케이스)은 수정하지 않으며**, 패키지 선언과 `import`만 이동에 맞게 조정한다.

> 이 테스트는 `SwingClassificationKeys`만 검증하므로 함께 이동할 수 있습니다. 반면 `CoachingEngineTest`·`VolleyDetectorTest`·`SwingInferenceBufferTest`는 아직 `:app`에 있는 클래스를 검증하므로 **`:app`에 남습니다**(TASK-015에서 이동).

### FR-5. 참조 갱신
이동한 두 타입을 사용하는 모든 코드의 `import`를 새 패키지로 갱신한다.

**주의 — 두 종류의 참조가 있습니다.**

1. **명시적 `import`가 있는 파일** (현재 8곳): `SwingAnalysisSessionState.kt`, `HexagonalRadarChart.kt`, `SessionDetailViewModel.kt`, `DeltaSummaryChips.kt`, `PracticeScreen.kt`, `SwingHistoryRepository.kt`(2건), `SwingAnalysisForegroundService.kt`
2. **`import`가 없는 파일** — `analysis` 패키지 내부 파일들은 **같은 패키지라서 import 없이 참조**하고 있습니다. 타입이 다른 패키지로 나가면 이들에는 **새 `import`를 추가**해야 합니다. 해당 파일과 테스트를 빠짐없이 처리한다.

FR-3의 삭제와 본 요구사항 누락이 겹치면 컴파일이 실패하므로, **AC-3의 빌드 성공이 이 요구사항의 실질적 검증 수단**이다.

### FR-6. `:app` 의존성 선언
`app/build.gradle.kts`의 `dependencies`에 `implementation(project(":core:model"))`를 추가한다.

### FR-7. 아키텍처 규칙 갱신
루트 `build.gradle.kts`의 `verifyModuleDependencies` 내 `allowedDeps` 맵을 갱신한다.

1. **`":core:model" to emptySet()` 항목을 명시적으로 추가한다.** 이 모듈은 어떤 모듈도 참조하지 않는 잎이다.
2. `:core:model`을 참조할 수 있는 모듈의 허용 집합에 `":core:model"`을 추가한다: `:core:ui`, `:core:data`, `:core:analysis`, `:feature:match`, `:feature:history`, `:feature:lab`, `:app`.
3. **`:core:sensor`의 허용 집합은 빈 집합으로 유지한다.** 원시 IMU 계층은 스윙 품질 지표를 알 필요가 없으며, 불필요한 허용은 규칙의 구속력을 약화시킨다.

> **왜 1번을 "명시적으로" 요구하는가**: 현재 구현은 `allowedDeps[projPath] ?: emptySet()` 이므로, 맵에 키가 **없어도** 빈 집합으로 취급되어 검사가 그냥 통과합니다. 즉 **맵 갱신을 잊어도 `verifyModuleDependencies`는 초록색입니다.** 이 조용한 통과를 막기 위해 키 존재 자체를 AC-6에서 확인합니다.

### FR-8. 문서 갱신
`TennisDocAI/AI_README.md`(존재 시)와 `README.md`의 모듈 구조 설명에 `:core:model`을 반영한다. **본 task에서 수정이 허용된 문서는 이 두 파일뿐이다.** `docs/` 하위의 계획·명세 문서는 수정하지 않는다.

---

## 5. 인수 조건 (Acceptance Criteria)

> 모든 명령은 `TennisDocAI/`에서 실행한다.

| # | 조건 |
|---|---|
| **AC-1** | `settings.gradle.kts`에 `include(":core:model")`이 존재하고, `./gradlew projects` 출력에 `:core:model`이 나타난다. |
| **AC-2** | `core/model/build.gradle.kts`가 `tennisdoc.jvm.library`를 적용하며, 파일 내에 `com.android` 플러그인 선언과 `android {` 블록이 **없다**. |
| **AC-3** | `./gradlew assembleDebug` 성공. |
| **AC-4** | `./gradlew test` 성공, **실패 0건**. 총 테스트 수가 직전 기준선인 **57건 미만이 아니다**(TASK-013 QA 리포트 기준). 감소했다면 이동 중 테스트가 유실된 것이다. |
| **AC-5** | `./gradlew verifyModuleDependencies` 성공. |
| **AC-6** | 루트 `build.gradle.kts`의 `allowedDeps`에 `":core:model"` **키가 문자열로 존재**하고 그 값이 빈 집합이다. 또한 `:core:sensor`의 허용 집합에는 `":core:model"`이 **없다**. |
| **AC-7** | `:app`의 `.../tennisdoc/analysis/SwingMetrics.kt` 와 `.../analysis/SwingClassificationKeys.kt` 가 **존재하지 않는다**. 전체 소스에서 `io.github.loje0611.tennisdoc.analysis.SwingMetrics` / `...analysis.SwingClassificationKeys` 문자열이 **0건**이다(`build/` 제외). |
| **AC-8** | `:core:model`의 소스에 `import android.` 및 `import androidx.` 가 **0건**이다. |
| **AC-9** | `SwingClassificationKeys`의 7개 상수 값이 이동 전과 **바이트 단위로 동일**하다: `Backhand_Slice`, `Backhand_Topspin`, `Backhand_Volley`, `Forehand_Slice`, `Forehand_Topspin`, `Forehand_Volley`, `Idle`. |
| **AC-10** | **(변이 검증 — 규칙 실효성)** `core/model/build.gradle.kts`에 `implementation(project(":core:data"))`를 일시 추가하면 `verifyModuleDependencies`가 **실패**한다. 확인 후 원복하고, 원복 뒤 다시 통과함을 보인다. |
| **AC-11** | **(변이 검증 — 테스트 실효성)** 이동한 `SwingClassificationKeysTest`가 `:core:model`에서 실제로 실행됨을 보인다. `SwingClassificationKeys.normalize`의 `lowercase(Locale.US)` 를 제거하는 등 동작을 일시 훼손하면 **해당 모듈의 `test`가 실패**해야 한다. 확인 후 원복한다. |
| **AC-12** | 변경된 파일 목록에 `TennisDocAI/` 밖의 경로가 없다. 단, `README.md`(루트)는 FR-8에 의해 허용된다. |
| **AC-13** | Room 스키마 파일(`app/schemas/**`)에 변경이 없다. |

---

## 6. 검증 시 주의사항

- **`test`는 테스트가 0개여도 성공합니다.** `:core:model`에 테스트가 실제로 존재하고 실행되었는지를 반드시 확인하십시오. AC-11의 변이 검증이 이를 위한 것입니다. "빌드가 초록색"만으로는 FR-4의 이행을 증명하지 못합니다.
- **`verifyModuleDependencies`는 맵에 키가 없어도 통과합니다**(FR-7의 설명 참조). AC-6은 명령 실행이 아니라 **소스 확인**으로 판정하십시오.
- **`SwingMetricsAvg`는 이 task와 무관합니다.** 이름이 유사해 검색에 걸리지만 `:core:data`의 Room projection입니다. 이 파일이 변경되었다면 범위 위반입니다.
- **`analysis` 패키지의 나머지 파일에 `import` 외의 변경이 있으면 범위 위반입니다.** diff를 확인하십시오. 로직·시그니처 수정은 TASK-015의 몫입니다.
- 계측 테스트(`androidTest`)는 실기기가 필요하므로 **실행하지 않아도 됩니다.** 미실행은 "검증 불가"가 아니며, 컴파일 성공으로 충분합니다.
- AC-10·AC-11의 변이는 **반드시 원복**하고, 원복 후 상태에서 AC-3~AC-5를 다시 통과함을 확인하십시오.

## 7. 완료 정의

AC-1 ~ AC-13 전부 충족.
