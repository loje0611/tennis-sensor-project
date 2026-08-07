# TASK-012: `:core:data` 모듈 추출 (Room 영속화 · DataStore 환경설정)

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-07 | PM | 최초 작성 |

---

## 1. Overview & Scope

### 1.1 목적
TennisDocAI 앱의 **영속화 계층**을 `:app` 모듈에서 분리하여 독립 Android 라이브러리 모듈 `:core:data`로 이전한다.

이 모듈의 단일 책임은 **"데이터를 저장하고 꺼내온다"** 이다. Room 데이터베이스(스윙 세션·이벤트·통계)와 DataStore 기반 환경설정(캘리브레이션 값·테마 설정)이 여기에 속한다. 저장된 수치를 *해석*하는 일(스윙 분류, 코칭 문구 생성)은 이 모듈의 책임이 아니다.

### 1.2 아키텍처 제약 (필독)
프로젝트 루트 `build.gradle.kts`의 `verifyModuleDependencies` 태스크가 모듈 간 의존 방향을 **강제**한다. `:core:data`에 허용된 모듈 의존성은 **빈 집합(`emptySet()`)** 이다.

```
":core:data"     to emptySet()             // 다른 어떤 모듈에도 의존 불가
":core:analysis" to setOf(":core:sensor")  // analysis는 data에 의존할 수 없음
```

즉 **`:core:data`와 `:core:analysis`는 서로를 참조할 수 없다.** 이 제약이 본 작업의 범위를 결정하며, 아래 FR-7(범위 제외)의 유일한 근거다.

### 1.3 사용자 데이터 보존 (최우선 제약)
이 앱은 **이미 실기기에 사용자 데이터를 저장하고 있다.** 데이터베이스 파일명 `swingsense.db`, 스키마 버전 `7`로 운영 중이다.

본 작업은 코드의 **위치**만 바꾸는 리팩터링이며, 다음을 절대 변경해서는 안 된다.

| 항목 | 고정값 | 위반 시 결과 |
|---|---|---|
| DB 파일명 | `swingsense.db` | 기존 사용자 기록이 통째로 사라짐 |
| 스키마 버전 | `7` | 마이그레이션 오작동 |
| 테이블명 | `swing_sessions`, `session_swing_counts`, `swing_events`, `global_statistics` | 앱 시작 시 크래시 |
| 스키마 identityHash | `c8e201a871aaf3813dd535f4f0e6eefb` | Room이 스키마 불일치로 크래시 |
| 마이그레이션 정의 | 5→6, 6→7 모두 유지 | 구버전 사용자 업그레이드 실패 |

**Kotlin 패키지명이 바뀌어도 위 항목은 하나도 바뀌지 않는다.** 패키지는 컴파일 시점 개념이고, 위 항목은 런타임/온디스크 개념이기 때문이다. 만약 구현 후 identityHash가 달라졌다면 그것은 **엔티티 정의를 실수로 건드렸다는 신호**다.

### 1.4 In Scope / Out of Scope

| 구분 | 항목 |
|---|---|
| **In Scope** | Room 데이터베이스 클래스·DAO 2종·엔티티/POJO 5종, DataStore 기반 `CalibrationStore`·`ThemePreferencesRepository`, Room 스키마 JSON 내보내기 설정, 계측 테스트 이전, 호출부 import 갱신 |
| **Out of Scope** | `SwingHistoryRepository` 이전 (FR-7 참조) · 스키마/테이블/컬럼 변경 · 새 마이그레이션 추가 · 쿼리 로직 변경 · UI·ViewModel · `:core:analysis` 관련 작업 |

### 1.5 본 작업의 성격
**동작 보존 리팩터링(behavior-preserving refactoring)** 이다. 모듈 경계 재배치와 그에 수반되는 패키지/의존성 조정 외에, 저장·조회 동작을 변경해서는 안 된다.

---

## 2. Definitions & References

### 2.1 용어

| 용어 | 정의 |
|---|---|
| **엔티티(Entity)** | Room 테이블 1개에 대응하는 클래스. `@Entity`로 표시된다. |
| **투영 POJO** | 테이블이 아니라 `AVG(...)` 같은 집계 쿼리 결과를 담는 데이터 클래스(`SwingMetricsAvg`, `SessionSwingCountEntity` 등 쿼리 결과 매핑용). |
| **DAO** | 데이터 접근 객체. SQL 쿼리를 Kotlin 함수로 노출한다. |
| **identityHash** | Room이 엔티티 정의 전체로부터 계산하는 지문. 내보낸 스키마 JSON에 기록되며, 스키마가 한 글자라도 달라지면 값이 바뀐다. |
| **스키마 내보내기** | `exportSchema = true`일 때 Room이 빌드 시 스키마를 JSON으로 기록하는 것. 저장 위치는 KSP 인자 `room.schemaLocation`이 정한다. |
| **계측 테스트(instrumented test)** | `androidTest` 소스셋의 테스트. 실행에 실기기 또는 에뮬레이터가 필요하다. |

### 2.2 관련 문서
- `docs/PHASE2_PLAN.md` — Phase 2 모듈 분해 계획 (A그룹)
- `docs/specs/TASK-011-core-sensor-extraction.md` — 직전 모듈 추출 작업. **본 작업은 그 구조와 원칙을 그대로 따른다.**
- `TennisDocAI/build.gradle.kts` — `verifyModuleDependencies` 의존성 규칙 (SSOT)
- `TennisDocAI/AI_README.md` — 빌드/테스트 명령

### 2.3 대상 모듈
- `target_project`: `TennisDocAI`
- 모듈 경로: `TennisDocAI/core/data/`
- 신규 패키지 루트: `io.github.loje0611.tennisdoc.core.data`
- `settings.gradle.kts`의 `include(":core:data")`와 `core/data/build.gradle.kts`(namespace 설정 완료)는 **이미 존재**한다. 소스가 없는 **빈 스캐폴드를 채우는** 작업이다.

---

## 3. Functional Requirements

### FR-1. Room 엔티티 및 투영 POJO 이전
다음 5개 타입을 `:core:data`로 이전한다. 대상 패키지는 `io.github.loje0611.tennisdoc.core.data.db.entity`.

| 타입 | 성격 |
|---|---|
| `SwingSessionEntity` | 테이블 `swing_sessions` |
| `SessionSwingCountEntity` | 테이블 `session_swing_counts` |
| `SwingEventEntity` | 테이블 `swing_events` |
| `GlobalStatisticsEntity` | 테이블 `global_statistics` |
| `SwingMetricsAvg` | 집계 쿼리 투영 POJO (테이블 아님) |

- **테이블명, 컬럼명, 컬럼 타입, nullable 여부, 기본값, 인덱스, 기본키, 외래키를 하나도 변경하지 않는다.**
- Room 어노테이션(`@Entity`, `@PrimaryKey`, `@ColumnInfo`, `@ForeignKey`, `@Index` 등)의 인자를 변경하지 않는다.
- 변경이 허용되는 것은 `package` 선언과 `import` 뿐이다.

### FR-2. DAO 이전
`SwingSessionDao`, `GlobalStatisticsDao`를 `io.github.loje0611.tennisdoc.core.data.db.dao`로 이전한다.

- **모든 `@Query`의 SQL 문자열을 한 글자도 변경하지 않는다.**
- 함수 시그니처(이름·파라미터 순서·타입·반환 타입, `suspend`/`Flow` 여부)를 변경하지 않는다.

### FR-3. 데이터베이스 클래스 이전
`TennisDocDatabase`를 `io.github.loje0611.tennisdoc.core.data.db`로 이전한다.

- `@Database`의 `entities` 목록, `version = 7`, `exportSchema = true`를 유지한다.
- DB 파일명 `"swingsense.db"`를 유지한다.
- 마이그레이션 `5→6`(no-op), `6→7`(`swing_events`에 `rawMaxAccel`·`rawDurationMs`·`rawGyroFollow` 컬럼 추가)을 **정의와 등록 모두 유지**한다.
- 다운그레이드 시 파괴적 마이그레이션 정책을 유지한다.
- 싱글턴 인스턴스 획득 방식(`getInstance(context)`)과 그 스레드 안전성 보장을 유지한다.

### FR-4. Room 스키마 내보내기 설정 및 스키마 이력 보존
`exportSchema = true`이므로, `:core:data` 모듈이 스키마 JSON을 내보내도록 KSP 인자 `room.schemaLocation`을 설정해야 한다.

- 설정하지 않으면 빌드 경고와 함께 **스키마 이력이 끊긴다.** 반드시 설정한다.
- 스키마 저장 위치는 `:core:data` 모듈 디렉터리 하위로 한다(구현자가 정확한 경로를 결정).
- **기존에 저장소에 커밋되어 있는 스키마 JSON 파일을 삭제하지 않는다.** 여기에는 앱 개명 이전의 레거시 스키마 디렉터리도 포함된다. 이 파일들은 과거 버전에서의 마이그레이션 검증에 쓰이는 이력 자산이다.
- 내보내기 디렉터리 이름은 데이터베이스 클래스의 정규화된 이름에서 파생되므로, 패키지 이동에 따라 **새 이름의 디렉터리가 생기는 것은 정상**이다. 중요한 것은 디렉터리 이름이 아니라 §1.3의 고정값들이다.

### FR-5. DataStore 기반 저장소 이전
다음 2개 타입을 `io.github.loje0611.tennisdoc.core.data.repository`로 이전한다.

| 타입 | 책임 |
|---|---|
| `CalibrationStore` | 캘리브레이션 기준값의 영속화 및 조회 |
| `ThemePreferencesRepository` | 테마 환경설정의 영속화 및 조회 |

- 두 타입 모두 현재 프로젝트 내부의 다른 패키지를 참조하지 않으므로, **패키지 선언 변경만으로 이전이 완료**되어야 한다.
- **DataStore 파일명과 모든 Preferences 키 문자열을 변경하지 않는다.** 키가 바뀌면 기존 사용자의 설정값이 초기화된다.
- 기본값(default value)을 변경하지 않는다.
- 공개 API 시그니처를 변경하지 않는다.

### FR-6. `:core:data`의 도메인 독립성
`:core:data`의 소스는 `io.github.loje0611.tennisdoc.analysis` 패키지를 **참조하지 않는다.**

- 이는 §1.2의 의존 규칙에서 직접 도출되는 요구사항이다.
- 데이터 계층은 스윙 지표를 **수치(숫자·문자열)로만** 다룬다. 그 수치가 무엇을 의미하는지는 상위 계층의 관심사다.

### FR-7. `SwingHistoryRepository`는 이전 대상에서 제외
`app/.../data/repository/SwingHistoryRepository.kt`는 `:core:data`로 **이전하지 않는다.**

- 사유: 이 클래스는 `analysis` 패키지의 `SwingClassificationKeys`와 `SwingMetrics`를 참조한다. `:core:data`로 옮기면 `:core:data → :core:analysis` 의존이 발생하여 §1.2의 규칙을 위반한다.
- 조치: `:app` 모듈에 **현재 위치와 패키지 그대로 유지**한다.
- 단, 이 파일이 참조하는 DB·DAO·엔티티가 `:core:data`로 이동하므로 **import 구문은 갱신해야 한다.** import 외의 로직은 변경하지 않는다.
- `:app`은 `:core:data`에 의존할 수 있으므로 이 구성은 규칙 위반이 아니다.

### FR-8. 호출부 갱신
이전된 타입을 참조하는 `:app`의 모든 파일이 새 패키지를 import하도록 갱신한다. 최소한 다음이 영향을 받는다.

- `di/AppModule.kt` (Hilt 제공 함수)
- `MainActivity.kt`
- `service/SwingAnalysisForegroundService.kt`
- `ui/history/HistoryScreen.kt`, `ui/history/HistoryViewModel.kt`, `ui/history/SessionDetailViewModel.kt`
- `ui/settings/SettingsViewModel.kt`, `ui/settings/DeveloperSettingsViewModel.kt`
- `data/repository/SwingHistoryRepository.kt` (FR-7에 따라 잔류)

**호출부의 로직을 변경하지 않는다.** import 구문 갱신에 한정한다.

`analysis/KinematicAnalyzer.kt`와 `analysis/VolleyDetector.kt`는 `CalibrationStore`를 **KDoc 주석으로만** 언급하며 코드 의존이 아니다. 링크가 해석되지 않으면 일반 텍스트로 낮추되 설명 내용은 유지한다.

### FR-9. Hilt 의존성 주입 구성 유지
`di/AppModule.kt`가 데이터베이스·DAO·저장소 인스턴스를 제공하고 있다면, **제공되는 타입의 집합과 각 바인딩의 스코프를 그대로 유지**한다.

- 주입 지점(생성자·필드)의 요구 타입이 새 패키지의 동일 타입으로 해석되어야 한다.
- Hilt 컴포넌트 구성이나 스코프 어노테이션을 변경하지 않는다.
- **관찰 가능한 요구사항**: 앱이 정상 조립되어야 하며, Hilt 코드 생성이 실패하지 않아야 한다.

### FR-10. 모듈 빌드 구성
`core/data/build.gradle.kts`를 이 모듈이 컴파일되도록 구성한다.

- 기존 `plugins`/`namespace` 설정을 유지한다. 이 모듈은 Compose UI를 포함하지 않으므로 Compose 컨벤션 플러그인을 적용하지 않는다.
- Room 어노테이션 처리를 위한 KSP 구성과 Room·DataStore·Coroutines 의존성을 선언한다.
- 계측 테스트(FR-11) 컴파일에 필요한 `androidTest` 의존성을 선언한다.
- **어떤 `project(...)` 의존성도 선언하지 않는다.** (§1.2)
- `:app`의 `build.gradle.kts`에 `:core:data`에 대한 의존을 추가한다.
- `:app`이 더 이상 Room 컴파일러를 필요로 하지 않게 되더라도, `:app`에서 Room 관련 선언을 제거할지 여부는 구현자가 결정한다. 다만 **제거하는 경우 `:app`의 KSP/Hilt 코드 생성이 여전히 성공해야 한다.**

### FR-11. 계측 테스트 이전
`app/src/androidTest/.../data/db/SwingSessionDaoTest.kt`를 `:core:data` 모듈의 `androidTest` 소스셋으로 이전한다.

- 검증 대상(`SwingSessionDao`)이 `:core:data`에 있으므로, 테스트도 같은 모듈에 두는 것이 자연스럽다.
- **테스트의 검증 내용을 축소하지 않는다.** 패키지/import 조정만 허용한다.
- 이 테스트는 실기기·에뮬레이터를 요구하므로 본 작업의 자동 검증에서는 **컴파일 성공까지만 확인**한다(§9 참조). 실행 불가는 결함이 아니다.
- `:app`에 동일 대상을 중복 검증하는 계측 테스트를 남기지 않는다.

---

## 4. Interfaces & Data Structures

### 4.1 `:core:data` 패키지 구조

```
io.github.loje0611.tennisdoc.core.data
├── db
│   ├── TennisDocDatabase              // @Database(version = 7, exportSchema = true)
│   ├── dao
│   │   ├── SwingSessionDao
│   │   └── GlobalStatisticsDao
│   └── entity
│       ├── SwingSessionEntity         // table: swing_sessions
│       ├── SessionSwingCountEntity    // table: session_swing_counts
│       ├── SwingEventEntity           // table: swing_events
│       ├── GlobalStatisticsEntity     // table: global_statistics
│       └── SwingMetricsAvg            // 집계 투영 POJO
└── repository
    ├── CalibrationStore
    └── ThemePreferencesRepository
```

위 11개 타입의 **공개 API 시그니처는 현재 `:app`에 있는 것과 완전히 동일하게 유지**한다. 변경 가능한 것은 패키지 경로뿐이다.

### 4.2 모듈 의존 그래프 (본 작업 완료 시점)

```
:app  ──▶  :core:ui
  ├──▶  :core:sensor  ──▶  (없음)
  └──▶  :core:data    ──▶  (없음)
```

`:core:data`에서 나가는 화살표는 **존재해서는 안 된다.**

---

## 5. UI/UX Requirements

**N/A (백엔드/영속화 모듈).**

단, 본 작업은 동작 보존 리팩터링이므로 **기존 화면의 동작과 표시가 달라져서는 안 된다.** 특히 히스토리 화면의 세션 목록·상세 지표, 설정 화면의 테마 선택은 이전과 동일하게 동작해야 한다.

---

## 6. Non-Functional Requirements

| 항목 | 요구사항 |
|---|---|
| 언어/플랫폼 | Kotlin, Android Library 모듈 |
| 신규 서드파티 라이브러리 | **추가 금지.** 현재 사용 중인 Room·DataStore·Coroutines로 충분하다. |
| 버전 카탈로그 | 의존성은 `gradle/libs.versions.toml`의 기존 alias만 사용한다. 새 alias 추가가 불가피하면 사유를 커밋 메시지에 남긴다. |
| 컨벤션 플러그인 | `build-logic`의 기존 컨벤션 플러그인을 사용한다. 새 컨벤션 플러그인을 만들지 않는다. |
| minSdk/컴파일 설정 | 컨벤션 플러그인이 정하는 값을 따르며 모듈에서 재정의하지 않는다. |
| 데이터 호환성 | §1.3의 고정값 전부 유지. **기존 사용자의 DB와 환경설정이 그대로 읽혀야 한다.** |
| 쿼리 성능 | 인덱스와 쿼리를 변경하지 않으므로 성능 특성이 동일해야 한다. |

---

## 7. Error Handling & Edge Cases

| 상황 | 요구 동작 |
|---|---|
| 기존 v7 DB를 가진 사용자가 앱 실행 | 기존 데이터를 그대로 읽는다. 마이그레이션이나 재생성이 발생하지 않는다. |
| v5 또는 v6 DB를 가진 사용자가 업그레이드 | 기존 마이그레이션 경로로 v7까지 정상 승격된다. |
| DB 다운그레이드 | 기존 정책(파괴적 마이그레이션)을 유지한다. |
| DataStore에 저장된 값이 없음 | 기존과 동일한 기본값을 반환한다. |
| `:core:data`에 모듈 의존 추가 | `verifyModuleDependencies`가 **빌드를 실패**시켜야 한다. |
| 엔티티 정의가 실수로 변경됨 | 내보낸 스키마의 identityHash가 달라지므로 **검증에서 반드시 검출**되어야 한다 (AC-8). |
| `room.schemaLocation` 미설정 | 스키마가 내보내지지 않아 AC-8을 만족할 수 없다 → 결함으로 간주한다. |

---

## 8. Acceptance Criteria

> 각 항목은 **결과물의 관찰 가능한 속성**이다. 구현 코드를 다시 읽어 확인하는 방식이 아니라, 빌드·테스트·산출물 검사로 확인되어야 한다.

- [ ] **AC-1** `./gradlew verifyModuleDependencies test assembleDebug`가 성공한다.
- [ ] **AC-2** `:core:data` 모듈이 컴파일되며, 산출물에 §4.1의 **11개 타입이 모두 포함**된다.
- [ ] **AC-3** `:core:data`는 **어떤 프로젝트 모듈에도 의존하지 않는다.** (`verifyModuleDependencies` 통과 + 빌드 스크립트에 `project(":...")` 선언 없음)
- [ ] **AC-4** `:core:data`의 소스 어디에도 `io.github.loje0611.tennisdoc.analysis` 패키지를 import하는 구문이 없다.
- [ ] **AC-5** 이전된 11개 타입이 `:app` 모듈의 소스 트리에 **더 이상 존재하지 않는다.**
- [ ] **AC-6** `SwingHistoryRepository.kt`는 `:app`에 **여전히 존재**하며, 그 변경 내역이 **import 구문에 한정**된다.
- [ ] **AC-7** 빌드 산출물로 **v7 스키마 JSON이 내보내진다.** 파일이 실제로 생성되어야 한다.
- [ ] **AC-8** 내보내진 v7 스키마의 `identityHash`가 **`c8e201a871aaf3813dd535f4f0e6eefb`** 와 정확히 일치한다. `version`은 `7`이다.
- [ ] **AC-9** 내보내진 스키마의 테이블 집합이 `swing_sessions`, `session_swing_counts`, `swing_events`, `global_statistics` **4개와 정확히 일치**한다.
- [ ] **AC-10** 작업 전 저장소에 존재하던 스키마 JSON 파일이 **하나도 삭제되지 않았다** (레거시 디렉터리 포함).
- [ ] **AC-11** 데이터베이스 파일명 문자열 `"swingsense.db"`가 유지된다.
- [ ] **AC-12** `swing_events` 테이블의 스키마에 `rawMaxAccel`, `rawDurationMs`, `rawGyroFollow` 컬럼이 존재한다 (6→7 마이그레이션 결과가 보존되었음을 의미).
- [ ] **AC-13** 마이그레이션 5→6과 6→7이 **정의되어 있고 데이터베이스 빌더에 등록**되어 있다.
- [ ] **AC-14** DataStore 파일명과 Preferences 키 문자열이 작업 전과 동일하다.
- [ ] **AC-15** `SwingSessionDaoTest`가 `:core:data`의 계측 테스트로 존재하며 **컴파일에 성공**한다 (`assembleDebugAndroidTest` 성공). `:app`에는 중복 계측 테스트가 남아 있지 않다.
- [ ] **AC-16** `:app`과 모든 `:core:*` 모듈의 기존 단위 테스트가 **모두 통과**한다 (회귀 없음). TASK-011에서 도입된 `ImuFrameSpecConsistencyTest`를 포함한다.
- [ ] **AC-17** Hilt 코드 생성이 성공하고 `assembleDebug`가 완료된다 (FR-9).
- [ ] **AC-18** 작업 범위 밖 파일이 수정되지 않았다. 특히 `tennis-vision-analyzer/` 등 다른 서브프로젝트와 `docs/` 하위 산출물(본 스펙·QA 리포트 제외)에 변경이 없다.

---

## 9. Testing Instructions

`TennisDocAI/AI_README.md`에 정의된 표준 검증 명령을 사용한다.

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies test assembleDebug
```

계측 테스트 컴파일 검증(AC-15)은 다음을 추가로 실행한다. **기기 없이 컴파일만 수행**되는 태스크다.

```bash
./gradlew :core:data:assembleDebugAndroidTest
```

### 검증 시 유의사항

- **AC-8이 본 작업의 핵심 안전장치다.** identityHash는 엔티티 정의 전체의 지문이므로, 컬럼 하나만 잘못 건드려도 값이 달라진다. 빌드 성공만으로는 스키마 훼손을 잡아낼 수 없으니, **내보내진 JSON 파일을 직접 열어 해시 문자열을 대조**한다.
- 스키마 JSON은 빌드 산출물이 아니라 **소스 디렉터리에 기록**된다. `room.schemaLocation`이 가리키는 경로에서 찾는다. 파일이 없다면 FR-4가 이행되지 않은 것이며, "빌드가 성공했으니 통과"로 판정하지 않는다.
- `test` 태스크는 모듈에 테스트가 없어도 성공한다. `BUILD SUCCESSFUL`만으로 AC-16을 판정하지 말고, **테스트 리포트에서 실제 실행 건수**를 확인한다.
- **`SwingSessionDaoTest`의 실행(assertion 수행)은 본 작업의 검증 대상이 아니다.** 이 테스트는 실기기/에뮬레이터를 요구하며, 기기 부재로 인한 미실행은 "검증 불가(verification impossible)"에 해당하지 않는다. AC-15는 **컴파일 성공**만을 요구한다.
- 실기기가 있는 환경이라면 `connectedDebugAndroidTest` 실행 결과를 참고 자료로 리포트에 첨부할 수 있으나, **합격/불합격 판정 근거로 삼지 않는다.**
- AC-6은 `git diff`로 `SwingHistoryRepository.kt`의 변경 줄이 `import` 구문에 한정되는지 직접 확인한다.
