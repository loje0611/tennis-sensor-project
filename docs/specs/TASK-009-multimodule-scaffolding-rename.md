# TASK-009: Gradle 멀티모듈 스캐폴딩 및 앱 개명

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-06 | PM | 최초 작성 |

---

## 1. Overview & Scope

### 1.1 목적
Android 애플리케이션이 단일 `:app` 모듈에 전 계층(BLE·Room·분석·UI·네이티브 추론)이 혼재된 상태다. 이후 기능 확장(카메라 기반 포즈 분석)을 추가하기 전에 **모듈 경계를 먼저 세우고**, 동시에 제품 명칭을 확정 명칭으로 정정한다.

본 task는 **구조와 식별자만 바꾸며, 동작을 바꾸지 않는다.** 완료 시점의 앱 기능은 착수 시점과 완전히 동일해야 한다.

### 1.2 착수 시점의 상태 (Implementation Entry Point)
> **중요**: 이 task 착수 시점에 모노레포에는 `TennisDocAI/` 디렉토리가 **존재하지 않고** `SwingSenseAI/` 디렉토리가 존재한다. `docs/task-board.json`의 `target_project` 값 `TennisDocAI`는 **본 task 완료 후의 상태**를 가리킨다. FR-1이 이 간극을 해소하므로 **FR-1을 가장 먼저 수행**해야 한다. 이후 모든 명령은 `TennisDocAI/`에서 실행한다.

### 1.3 In Scope
1. 서브프로젝트 디렉토리·`rootProject.name`·`applicationId`·패키지 루트·표시 명칭 개명 (FR-1 ~ FR-3)
2. Gradle 멀티모듈 골격 생성 — 9개 모듈 등록 및 빌드 스크립트 작성 (FR-4)
3. 공통 빌드 설정을 재사용하기 위한 `build-logic` 컴포지트 빌드 및 컨벤션 플러그인 (FR-5)
4. 버전 카탈로그 정비 (FR-6)
5. 모듈 의존 방향 규칙의 **기계적 강제** (FR-7)
6. 관련 문서 갱신 (FR-8)

### 1.4 Out of Scope (명시적 제외)
- **소스 코드의 모듈 이동.** 모든 Kotlin/C++ 소스, 리소스, `AndroidManifest.xml`, Room 스키마는 **`:app`에 그대로 남는다.** 신규 모듈은 소스가 없는 빈 골격이다. 코드 이관은 후속 task가 담당한다.
- **네이티브 빌드(`externalNativeBuild`/CMake) 이동.** `:app`에 잔류한다.
- 신규 모듈에 대한 Hilt·Room·Compose 등 라이브러리 의존성 부여. 각 모듈이 실제로 코드를 받는 후속 task에서 추가한다. 단, 컨벤션 플러그인이 제공하는 기본 설정은 예외다(FR-5).
- 기능 추가·삭제, UI 변경, DB 스키마 변경.
- 도메인 어휘로서의 `Swing` 을 포함한 클래스명 변경. FR-3에 열거된 3개 클래스만 개명한다.

---

## 2. Definitions & References

### 2.1 용어
| 용어 | 정의 |
|---|---|
| **모노레포 루트** | `docs/`, `README.md`, `prompts/` 를 포함하는 최상위 디렉토리 |
| **서브프로젝트 루트** | `settings.gradle.kts`와 `gradlew`를 포함하는 Gradle 빌드 루트. 개명 후 `TennisDocAI/` |
| **모듈** | `settings.gradle.kts`의 `include(...)`로 등록된 Gradle 프로젝트 |
| **골격 모듈(skeleton module)** | 빌드 스크립트와 소스 디렉토리만 존재하고 소스 파일이 없는 모듈 |
| **컨벤션 플러그인** | `build-logic` 컴포지트 빌드가 제공하는, 여러 모듈이 공유하는 빌드 설정 플러그인 |
| **프로젝트 의존(project dependency)** | `implementation(project(":core:data"))` 형태의 모듈 간 의존. 외부 아티팩트 의존과 구분한다 |

### 2.2 확정된 식별자
| 항목 | 값 |
|---|---|
| 표시명 (`app_name`) | `TennisDoc AI` |
| `rootProject.name` | `TennisDocAI` |
| 서브프로젝트 디렉토리 | `TennisDocAI` |
| `applicationId` | `io.github.loje0611.tennisdoc` |
| 패키지 루트 | `io.github.loje0611.tennisdoc` |
| 컨벤션 플러그인 id 접두사 | `tennisdoc.` |

### 2.3 참조
- 모듈 경계와 각 모듈의 책임: `docs/PRODUCT_DIRECTION.md` D-9
- 개명 결정과 적용 범위: `docs/PRODUCT_DIRECTION.md` D-8
- Phase 2 전체 task 순서: `docs/PHASE2_PLAN.md` §4
- 에이전트 테스트 명령 규약: `docs/AGENT_WORKFLOW.md` §7

---

## 3. Functional Requirements

### FR-1. 서브프로젝트 디렉토리 개명
- **입력**: 서브프로젝트 루트 `SwingSenseAI/`
- **처리**: 디렉토리를 `TennisDocAI/`로 이동한다. 버전 관리 이력이 보존되도록 **`git mv`를 사용**한다.
- **출력**: `TennisDocAI/`가 존재하고 `SwingSenseAI/`가 존재하지 않는다. 디렉토리 내부의 파일 목록과 내용은 이동 직후 변경되지 않는다.
- `TennisDocAI/settings.gradle.kts`의 `rootProject.name`은 `"TennisDocAI"` 여야 한다.

### FR-2. 패키지 및 애플리케이션 식별자 개명
- **입력**: 패키지 루트 `com.example.swingsenseai` 를 사용하는 모든 Kotlin 소스, 매니페스트, 빌드 스크립트, 테스트 소스
- **처리**:
  1. `:app` 모듈의 `namespace`와 `applicationId`를 `io.github.loje0611.tennisdoc`로 변경한다.
  2. `src/main/java`, `src/test/java`, `src/androidTest/java` 아래의 디렉토리 구조를 `io/github/loje0611/tennisdoc/`로 이동한다(`git mv` 사용). 하위 패키지 구조(`analysis/`, `data/db/`, `ui/theme/` 등)와 파일명은 유지한다.
  3. 모든 소스 파일의 `package` 선언과 `import` 문에서 `com.example.swingsenseai` 를 `io.github.loje0611.tennisdoc` 로 치환한다.
- **출력**: 서브프로젝트 전체에서 문자열 `com.example.swingsenseai` 의 출현 횟수가 **0**이다.
- **제약**: `AndroidManifest.xml`의 상대 클래스 참조(`android:name=".XxxActivity"` 형태)는 `namespace` 기준 상대 경로이므로 그대로 유효하다. 절대 경로로 바꾸지 않는다.

### FR-3. 제품명이 박힌 식별자 개명
- **입력**: 옛 제품명 `SwingSense` 를 포함하는 클래스명과 사용자 노출 문자열
- **처리**: 아래 표의 대상만 개명하고, 참조 지점(선언·사용처·`AndroidManifest.xml`·DI 모듈)을 모두 함께 갱신한다.

  | 종류 | 변경 전 | 변경 후 |
  |---|---|---|
  | Application 클래스 | `SwingSenseApplication` | `TennisDocApplication` |
  | Room Database 클래스 | `SwingSenseDatabase` | `TennisDocDatabase` |
  | 테마 색상 스킴 | `SwingColorScheme` | `TennisDocColorScheme` |
  | 문자열 리소스 `app_name` | `SwingSenseAI` | `TennisDoc AI` |
  | 문자열 리소스 `notification_title` | `SwingSenseAI` | `TennisDoc AI` |

  파일명은 클래스명과 일치시킨다(예: `SwingSenseDatabase.kt` → `TennisDocDatabase.kt`).
- **출력**: 서브프로젝트 전체에서 식별자 `SwingSenseApplication`·`SwingSenseDatabase`·`SwingColorScheme` 의 출현 횟수가 각각 **0**이다.
- **제약 (중요)**: Room 데이터베이스의 **파일명 문자열 `"swingsense.db"` 는 변경하지 않는다.** 변경 시 기존 단말에 축적된 세션 데이터에 접근할 수 없게 된다.
- **제약**: `SwingMetrics`·`SwingSessionEntity`·`SwingHistoryRepository`·`SwingEventEntity`·`SwingInferenceBuffer` 등 **`Swing`이 도메인 어휘로 쓰인 식별자는 변경하지 않는다.** 위 표에 없는 식별자는 개명 대상이 아니다.

### FR-4. 모듈 골격 생성
- **처리**: 서브프로젝트 `settings.gradle.kts`에 아래 9개 모듈을 등록하고, 각 모듈 디렉토리와 빌드 스크립트를 생성한다.

  | 모듈 경로 | 디렉토리 | 유형 | `namespace` |
  |---|---|---|---|
  | `:app` | `app/` | Android Application | `io.github.loje0611.tennisdoc` |
  | `:core:ui` | `core/ui/` | Android Library (Compose) | `io.github.loje0611.tennisdoc.core.ui` |
  | `:core:sensor` | `core/sensor/` | Android Library | `io.github.loje0611.tennisdoc.core.sensor` |
  | `:core:data` | `core/data/` | Android Library | `io.github.loje0611.tennisdoc.core.data` |
  | `:core:analysis` | `core/analysis/` | Android Library | `io.github.loje0611.tennisdoc.core.analysis` |
  | `:core:vision` | `core/vision/` | **순수 JVM (`org.jetbrains.kotlin.jvm`)** | 해당 없음 |
  | `:feature:match` | `feature/match/` | Android Library (Compose) | `io.github.loje0611.tennisdoc.feature.match` |
  | `:feature:history` | `feature/history/` | Android Library (Compose) | `io.github.loje0611.tennisdoc.feature.history` |
  | `:feature:lab` | `feature/lab/` | Android Library (Compose) | `io.github.loje0611.tennisdoc.feature.lab` |

- 각 신규 모듈은 소스 파일 없는 **골격 모듈**이며, 최소한 다음 소스 디렉토리를 갖는다.
  - Android Library: `src/main/java/`, `src/test/java/`
  - JVM Library(`:core:vision`): `src/main/kotlin/`, `src/test/kotlin/`
- **`:core:vision`은 어떤 Android 플러그인·아티팩트에도 의존하지 않는다.** 이 모듈의 테스트는 Android SDK나 기기 없이 JVM만으로 실행 가능해야 한다.
- **`:app`은 신규 모듈 중 어느 것도 아직 의존하지 않는다.** 골격이 비어 있으므로 의존을 추가할 이유가 없고, 소스가 이동하는 후속 task에서 추가된다.
- `:app`의 기존 설정(`externalNativeBuild`, `ndk.abiFilters`, KSP `room.schemaLocation`, 기존 의존성 목록, `buildTypes`, `compileOptions`)은 **변경 없이 유지**한다.

### FR-5. `build-logic` 컴포지트 빌드 및 컨벤션 플러그인
- **근거**: 8개 신규 모듈이 동일한 `compileSdk`·`minSdk`·`compileOptions`·Compose 설정을 반복하면 값이 갈라진다. 공통 설정은 한 곳에서 정의한다.
- **처리**:
  1. 서브프로젝트 루트에 `build-logic/` 컴포지트 빌드를 만들고 `settings.gradle.kts`의 `pluginManagement` 블록에서 `includeBuild("build-logic")` 로 포함한다.
  2. 아래 컨벤션 플러그인을 제공한다.

     | 플러그인 id | 적용 대상 | 제공 설정 |
     |---|---|---|
     | `tennisdoc.android.library` | Android Library 모듈 전체 | `com.android.library` 적용, `compileSdk` = 36, `minSdk` = 24, `sourceCompatibility`/`targetCompatibility` = Java 11 |
     | `tennisdoc.android.library.compose` | Compose를 쓰는 Android Library | `tennisdoc.android.library` + `org.jetbrains.kotlin.plugin.compose` 적용, `buildFeatures.compose = true`, Compose BOM 및 기본 Compose 의존성 |
     | `tennisdoc.jvm.library` | `:core:vision` | `org.jetbrains.kotlin.jvm` 적용, JVM toolchain 11, `junit` 테스트 의존성 |

  3. 각 모듈의 `build.gradle.kts`는 위 플러그인을 적용하고 `namespace` 등 모듈 고유 값만 선언한다.
- **출력**: 어떤 Android Library 모듈의 빌드 스크립트에도 `compileSdk`·`minSdk`·`compileOptions` 값이 직접 기재되지 않는다.
- **제약**: `:app` 모듈은 Application 모듈이므로 위 플러그인 적용 대상이 아니다. `:app`의 빌드 스크립트는 FR-2에 따른 식별자 변경 외에는 수정하지 않는다.

### FR-6. 버전 카탈로그 정비
- **입력**: `gradle/libs.versions.toml`
- **처리**: 멀티모듈 구성에 필요한, 현재 카탈로그에 없는 플러그인 별칭을 추가한다.

  | 별칭 | plugin id | 버전 참조 |
  |---|---|---|
  | `android-library` | `com.android.library` | 기존 `agp` |
  | `kotlin-jvm` | `org.jetbrains.kotlin.jvm` | 기존 `kotlin` |

- **제약**: 기존 `[versions]`·`[libraries]`·`[plugins]` 항목의 **값을 변경하거나 제거하지 않는다.** 라이브러리 버전 상향은 본 task의 범위가 아니다(동작 변경 금지 원칙).
- **제약**: 모든 신규 모듈과 `build-logic`의 의존성은 하드코딩된 좌표 문자열이 아니라 **버전 카탈로그 별칭을 통해서만** 선언한다.

### FR-7. 모듈 의존 방향 규칙과 강제 수단
- **규칙**: 각 모듈이 선언할 수 있는 **프로젝트 의존**은 아래 허용 목록으로 제한된다. 목록에 없는 모듈 간 의존은 금지다.

  | 모듈 | 허용되는 프로젝트 의존 |
  |---|---|
  | `:core:ui` | 없음 |
  | `:core:sensor` | 없음 |
  | `:core:data` | 없음 |
  | `:core:vision` | 없음 |
  | `:core:analysis` | `:core:sensor` |
  | `:feature:match` | `:core:ui`, `:core:sensor`, `:core:data`, `:core:analysis` |
  | `:feature:history` | `:core:ui`, `:core:data` |
  | `:feature:lab` | `:core:ui`, `:core:vision`, `:core:data`, `:core:analysis` |
  | `:app` | 모든 `:core:*` 및 `:feature:*` |

  이 표에서 도출되는 불변식은 다음과 같다.
  - **INV-1**: `:core:*` 모듈은 `:feature:*` 또는 `:app` 에 의존하지 않는다.
  - **INV-2**: `:feature:*` 모듈은 다른 `:feature:*` 모듈에 의존하지 않는다.
  - **INV-3**: 모듈 의존 그래프에 순환이 없다.
  - **INV-4**: `:core:vision` 은 어떤 모듈에도 의존하지 않는다.

- **처리**: 위 규칙을 사람이 지키는 관례가 아니라 **빌드가 검사하는 규칙**으로 만든다. 서브프로젝트 루트 빌드에 검증 태스크 **`verifyModuleDependencies`** 를 정의한다.
  - 동작: 모든 서브 모듈의 설정(configuration)을 순회해 프로젝트 의존을 수집하고, 허용 목록과 대조한다.
  - 위반 발견 시 **빌드를 실패**시키며, 오류 메시지에 위반한 **모듈 경로**와 **금지된 의존 대상**을 모두 포함한다.
  - 위반이 없으면 성공한다.
- **근거**: 후속 task들이 코드를 모듈로 옮기는 과정에서 의존이 뒤엉키는 것이 이 구조 변경의 가장 큰 실패 모드다. 규칙이 문서에만 있으면 위반을 발견하는 시점이 너무 늦다.

### FR-8. 문서 갱신
개명 이후 잘못된 경로·모듈명을 가리키게 되는 문서를 갱신한다. 경로는 **모노레포 루트 기준**이다.

| 파일 | 갱신 내용 |
|---|---|
| `TennisDocAI/AI_README.md` | 제목의 프로젝트명, "애플리케이션 모듈"·"소스 위치" 항목을 멀티모듈 구조에 맞게 수정. §2 실행 명령의 기준 디렉토리를 `TennisDocAI/`로 수정. **FR-9의 테스트 명령을 여기에 반영** |
| `README.md` | 디렉토리 구조 트리의 `SwingSenseAI/` 항목명과 설명, 모듈 구조 서술을 FR-4의 9개 모듈로 갱신 |
| `docs/AGENT_WORKFLOW.md` | §7 테스트 명령표에서 해당 서브프로젝트의 이름과 명령을 갱신 |
| `TennisDocAI/PROJECT_STATE_REPORT.md` | 문서 내 옛 프로젝트명·패키지 경로 표기를 갱신 |

> **`AI_README.md` 갱신은 선택이 아니다.** Tester 에이전트가 테스트 명령을 조회하는 파일이므로, 누락되면 이후 이 서브프로젝트의 모든 task에서 QA가 존재하지 않는 경로를 참조하게 된다.

### FR-9. 동작 보존
- 본 task 완료 후, 기존 단위 테스트 6종(`CoachingEngineTest`, `KinematicAnalyzerTest`, `SwingClassificationKeysTest`, `SwingInferenceBufferTest`, `VolleyDetectorTest`, `ImuPayloadParserTest`)이 **모두 통과**해야 한다.
- 테스트 파일의 **단정문(assertion) 내용을 수정해서는 안 된다.** 허용되는 변경은 `package`/`import` 선언과 FR-3에 따른 클래스명 참조 갱신뿐이다.
- 디버그 APK 빌드가 성공해야 하며, 네이티브 라이브러리가 기존과 동일한 ABI 4종(`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`)으로 포함되어야 한다.

---

## 4. Interfaces & Data Structures

본 task는 런타임 API를 도입하지 않는다. 계약은 **빌드 구성**의 형태로 표현된다.

### 4.1 최종 디렉토리 구조
```text
TennisDocAI/
├── settings.gradle.kts          # includeBuild("build-logic") + include 9개 모듈
├── build.gradle.kts             # 루트. verifyModuleDependencies 태스크 정의
├── gradle/libs.versions.toml    # FR-6 별칭 추가
├── build-logic/
│   ├── settings.gradle.kts
│   └── convention/
│       ├── build.gradle.kts
│       └── src/main/kotlin/     # 컨벤션 플러그인 3종
├── app/                         # 기존 소스 전량 잔류 (cpp, res, schemas 포함)
│   └── src/main/java/io/github/loje0611/tennisdoc/...
├── core/
│   ├── ui/        core/sensor/  core/data/
│   ├── analysis/  core/vision/
└── feature/
    ├── match/     history/      lab/
```

### 4.2 `verifyModuleDependencies` 태스크 계약
| 항목 | 값 |
|---|---|
| 실행 위치 | 서브프로젝트 루트 (`TennisDocAI/`) |
| 호출 | `./gradlew verifyModuleDependencies` |
| 성공 조건 | 모든 모듈의 프로젝트 의존이 FR-7 허용 목록의 부분집합 |
| 실패 시 종료 코드 | 0이 아님 |
| 실패 메시지 필수 포함 요소 | 위반 모듈 경로, 금지된 의존 대상 경로 |

---

## 5. UI/UX Requirements

화면 레이아웃·컴포넌트 구성·인터랙션은 **일절 변경하지 않는다.** 사용자에게 보이는 변화는 다음 두 가지로 한정된다.

| 위치 | 변경 전 | 변경 후 |
|---|---|---|
| 런처 아이콘 라벨 (`app_name`) | `SwingSenseAI` | `TennisDoc AI` |
| 포그라운드 서비스 알림 제목 (`notification_title`) | `SwingSenseAI` | `TennisDoc AI` |

그 외 모든 문자열 리소스(한국어 안내 문구, 에러 메시지, 차트 축 레이블 등)의 값은 변경하지 않는다.

---

## 6. Non-Functional Requirements

| 항목 | 요구사항 |
|---|---|
| 빌드 도구 | 서브프로젝트에 포함된 `./gradlew` wrapper 사용. 전역 `gradle` 사용 금지 |
| Gradle 설정 언어 | Kotlin DSL (`.kts`) |
| 의존성 선언 | 버전 카탈로그(`libs.versions.toml`) 별칭을 통해서만 선언 |
| SDK 수준 | `compileSdk` 36, `minSdk` 24, `targetSdk` 36 유지 |
| Java 호환성 | 소스/타깃 호환성 Java 11 유지 |
| 신규 외부 의존성 | **추가하지 않는다.** CameraX·MediaPipe 등 Phase 2 후반 의존성은 본 task 범위 밖 |
| 이력 보존 | 디렉토리·파일 이동은 `git mv`를 사용해 이름 변경으로 추적되게 한다 |
| 빌드 시간 | 신규 골격 모듈 8개 추가로 인해 클린 빌드가 실패하지 않아야 한다 |

---

## 7. Error Handling & Edge Cases

| ID | 상황 | 요구 동작 |
|---|---|---|
| **EH-1** | 패키지 경로 치환이 문자열 리터럴·주석·`proguard-rules.pro`·`AndroidManifest.xml`의 `${applicationId}` 치환자 등 예상 밖 위치에 존재 | 일괄 치환 후 잔여 검색으로 확인한다. `${applicationId}` 치환자는 빌드가 값을 주입하므로 **문자열을 직접 박아 넣지 않는다** |
| **EH-2** | Room 스키마 JSON(`app/schemas/*.json`)에 이전 패키지명이 기록되어 있음 | 스키마 파일은 특정 버전 시점의 산출물이다. **DB 버전을 올리지 않고 기존 스키마 파일을 손으로 편집하지 않는다.** Room 컴파일러가 클래스 경로 변경으로 새 스키마를 요구하면, DB 버전 증가가 아니라 엔티티 구조가 실제로 동일함을 확인하는 방향으로 해결한다 |
| **EH-3** | `git mv` 대상 경로에 IDE 생성물(`.idea/`, `build/`, `.gradle/`)이 섞임 | 버전 관리 대상이 아닌 생성물은 이동하지 않고 무시한다. 이동 후 남은 stale 빌드 산출물이 빌드를 방해하면 삭제한다 |
| **EH-4** | 소스가 없는 골격 모듈이 빌드/테스트 태스크에서 오류를 냄 | 빈 모듈도 빌드와 테스트가 **성공**해야 한다. 통과시키기 위한 목적의 더미 클래스나 더미 테스트를 추가하지 않는다 |
| **EH-5** | `:core:vision`에 Android 관련 플러그인·의존성이 유입 | 유입되면 안 된다. 이 모듈은 Android SDK 없이 빌드·테스트되어야 한다 |
| **EH-6** | 컨벤션 플러그인이 Compose를 쓰지 않는 모듈에까지 Compose 설정을 적용 | Compose 설정은 `tennisdoc.android.library.compose` 적용 모듈에만 적용된다. `:core:sensor`·`:core:data`·`:core:analysis`는 Compose 설정을 갖지 않는다 |
| **EH-7** | 개명 후에도 옛 식별자가 일부 남음 | FR-2·FR-3의 잔여 검색 결과가 0이어야 한다. 부분 개명 상태로 종료하지 않는다 |
| **EH-8** | `verifyModuleDependencies`가 현재 그래프에서 무조건 통과하도록 구현됨(예: 검사 로직 부재) | 허용되지 않는다. 금지된 의존이 실제로 추가되면 반드시 실패해야 한다 |

---

## 8. Acceptance Criteria

### 개명
- [ ] 모노레포에 `TennisDocAI/` 디렉토리가 존재하고 `SwingSenseAI/` 디렉토리가 존재하지 않는다.
- [ ] `TennisDocAI/` 전체에서 문자열 `com.example.swingsenseai` 를 검색한 결과가 0건이다.
- [ ] `TennisDocAI/` 전체에서 식별자 `SwingSenseApplication`, `SwingSenseDatabase`, `SwingColorScheme` 를 검색한 결과가 각각 0건이다.
- [ ] Room 데이터베이스 파일명 문자열 `"swingsense.db"` 는 그대로 존재한다.
- [ ] 문자열 리소스 `app_name` 의 값이 `TennisDoc AI` 이다.
- [ ] `rootProject.name` 이 `TennisDocAI` 이고, `:app` 의 `applicationId` 와 `namespace` 가 `io.github.loje0611.tennisdoc` 이다.

### 모듈 구성
- [ ] `./gradlew projects` 출력에 FR-4 표의 9개 모듈이 모두 나타난다.
- [ ] 신규 8개 모듈에는 소스 파일이 없다(빈 골격).
- [ ] `:core:vision` 의 빌드 스크립트와 해석된 의존성에 Android 플러그인·아티팩트가 포함되지 않는다.
- [ ] `:app` 의 빌드 산출물에 네이티브 라이브러리가 ABI 4종(`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`)으로 포함된다.
- [ ] 어떤 Android Library 모듈의 `build.gradle.kts` 에도 `compileSdk`·`minSdk`·`compileOptions` 값이 직접 기재되어 있지 않다.

### 의존 규칙
- [ ] `./gradlew verifyModuleDependencies` 가 성공한다.
- [ ] `:core:*` 모듈 중 어느 것도 `:feature:*` 또는 `:app` 을 프로젝트 의존으로 선언하지 않는다.
- [ ] 어떤 `:feature:*` 모듈도 다른 `:feature:*` 모듈을 프로젝트 의존으로 선언하지 않는다.
- [ ] 임의의 모듈에 FR-7 허용 목록에 없는 프로젝트 의존을 한 줄 추가하면 `./gradlew verifyModuleDependencies` 가 **실패하고**, 오류 메시지에 해당 모듈 경로와 금지된 의존 대상이 포함된다. (검증 후 추가한 줄은 원복한다.)

### 동작 보존
- [ ] `./gradlew test` 가 성공하며, 기존 단위 테스트 6종이 모두 실행되어 통과한다.
- [ ] 기존 테스트 파일의 단정문이 변경되지 않았다(변경은 `package`/`import`/클래스명 참조에 한정).
- [ ] `./gradlew assembleDebug` 가 성공한다.
- [ ] `app_name`·`notification_title` 외의 문자열 리소스 값이 변경되지 않았다.

### 문서
- [ ] `TennisDocAI/AI_README.md` 의 모듈·소스 위치 설명과 실행 명령이 실제 구조와 일치하며, §9의 테스트 명령을 포함한다.
- [ ] `README.md` 의 디렉토리 구조·모듈 구조 서술에 `SwingSenseAI` 표기가 남아 있지 않다.
- [ ] `docs/AGENT_WORKFLOW.md` §7 테스트 명령표가 갱신된 프로젝트명과 명령을 가리킨다.
- [ ] 모노레포 루트의 `README.md`·`docs/AGENT_WORKFLOW.md`·`docs/PHASE2_PLAN.md` 에서 링크된 서브프로젝트 경로가 실제로 존재한다.

---

## 9. Testing Instructions

모든 명령은 **서브프로젝트 루트 `TennisDocAI/`** 에서 실행한다.

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies test assembleDebug
```

- `verifyModuleDependencies`: FR-7 모듈 의존 규칙 검사
- `test`: 전 모듈 JVM 단위 테스트 (기기 불필요)
- `assembleDebug`: 네이티브 빌드를 포함한 디버그 APK 조립

계측 테스트(`./gradlew connectedAndroidTest`)는 실기기/에뮬레이터가 필요하므로 에이전트 환경에서는 건너뛰고 위 명령 결과로 검증한다.

### 검증 방법에 대한 제약
- 소스 파일의 **문자열 존재 여부를 단정하는 방식으로 요구사항 충족을 판정하지 않는다.** 예를 들어 "빌드 스크립트에 `tennisdoc.android.library` 문자열이 있다"는 검증은 무효하며, 해석된 빌드 구성이나 빌드 산출물로 확인해야 한다.
- 잔여 식별자 검색(§8의 0건 조건)은 예외적으로 텍스트 검색이 정당한 항목이다. 이 경우 검색 대상에서 `build/`·`.gradle/`·`.idea/` 생성물 디렉토리를 제외한다.
