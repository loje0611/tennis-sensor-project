# TASK-019 — `SwingHistoryRepository` 인터페이스 추출 및 `:feature:history` ViewModel 단위 테스트 구현

| 항목 | 값 |
|---|---|
| Task ID | TASK-019 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-016, TASK-017 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §8.2 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-11 | PM | 최초 작성 (사용자 요청: History ViewModel JVM 단위 테스트를 위한 Fake Repository 도입 및 인터페이스 분리) |

---

## 1. 개요 및 범위

### 1.1 개요
현재 `:core:data` 모듈의 `SwingHistoryRepository`는 Room `TennisDocDatabase`에 직접 결합된 구체 클래스(Concrete Class)로 존재한다. 이로 인해 `:feature:history` 모듈의 `HistoryViewModel` 및 `SessionDetailViewModel`을 순수 JVM 단위 테스트(Fast Unit Test) 환경에서 독립 검증하기 어렵고, 테스트 더블(Fake Repository)을 주입할 수 없는 구조적 한계가 존재한다.

본 태스크에서는 `SwingHistoryRepository`를 인터페이스와 구현체(`SwingHistoryRepositoryImpl`)로 분리하고, Hilt DI 바인딩을 정비한 뒤, `:feature:history` 모듈에 `FakeSwingHistoryRepository`를 구축하여 두 ViewModel의 핵심 비즈니스 로직을 JVM 단위 테스트로 깊게 검증한다.

### 1.2 범위
- **포함**:
  - `:core:data` 모듈 내 `SwingHistoryRepository` 인터페이스 추출 및 기존 메서드 서명 선언.
  - 기존 구체 클래스를 `SwingHistoryRepositoryImpl`로 명명 변경 및 인터페이스 구현.
  - Hilt DI 모듈(`RepositoryModule` 등)에 `@Binds`를 통한 `SwingHistoryRepository` → `SwingHistoryRepositoryImpl` 바인딩 추가.
  - `:feature:history/src/test/`에 메모리 기반 `FakeSwingHistoryRepository` 작성.
  - `HistoryViewModelTest` 및 `SessionDetailViewModelTest` 작성 (세션 목록 Flow 관찰, Mock 세션 삽입, 세션 상세 조회, 세션 삭제 등의 비즈니스 로직 검증).
- **제외**:
  - `SwingHistoryRepository` 공개 API 명세나 비즈니스 로직의 변경.
  - Room DB 스키마 및 DAO 쿼리의 변경.

---

## 2. 정의 및 참조

- **참조 문서**: [`docs/specs/TASK-016-history-decoupling.md`](TASK-016-history-decoupling.md), [`docs/specs/TASK-017-feature-history-module.md`](TASK-017-feature-history-module.md)
- **모듈 의존성**: `:core:data` → `{:core:model}`, `:feature:history` → `{:core:model, :core:ui, :core:data}`

---

## 3. 기능 요구사항

### FR-1. `SwingHistoryRepository` 인터페이스 추출 및 구현체 분리
- `io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository`를 `interface`로 전환하고, 기존 외부 노출 메서드 서명을 선언한다:
  - `fun observeSessions(): Flow<List<SwingSessionEntity>>`
  - `suspend fun generateCsvString(sessionId: String?, startTimeMillis: Long?, endTimeMillis: Long?): String`
  - `suspend fun getSessionDetail(sessionId: String): SessionDetailData?`
  - `suspend fun deleteSession(sessionId: String)`
  - `suspend fun insertProvisionalSession(session: SwingSessionEntity)`
  - `suspend fun finalizeSession(...)`
  - `suspend fun insertSessionWithBreakdown(...)`
  - `suspend fun insertMockSession(session: SwingSessionEntity, breakdownMap: Map<String, Int>, events: List<SwingEventEntity>)`
  - `suspend fun insertSwingEvent(event: SwingEventEntity)`
  - `suspend fun getAverageMetrics(sessionId: String, categoryKey: String): SwingMetricsAvg?`
  - `suspend fun getSwingEventsForSession(sessionId: String): List<SwingEventEntity>`
  - `suspend fun updateGlobalStatistics(categoryKey: String, metrics: SwingMetrics)`
  - `suspend fun batchUpdateGlobalStatistics(events: List<SwingEventEntity>)`
  - `suspend fun getGlobalAverageMetrics(categoryKey: String): SwingMetrics?`
- 기존 Room DB 기반 구현 코드를 `SwingHistoryRepositoryImpl` 클래스로 변경하고 `SwingHistoryRepository` 인터페이스를 구현하도록 한다.

### FR-2. Hilt 저장소 바인딩 구성
- `:core:data` 또는 `:app` 모듈의 Hilt Module에 `@Binds` 메서드를 작성하여 `SwingHistoryRepositoryImpl`을 `SwingHistoryRepository` 인터페이스 타입으로 제공한다:
  ```kotlin
  @Binds
  abstract fun bindSwingHistoryRepository(
      impl: SwingHistoryRepositoryImpl
  ): SwingHistoryRepository
  ```

### FR-3. `:feature:history` 테스트 더블 (`FakeSwingHistoryRepository`) 구축
- `:feature:history/src/test/` 패키지 아래 메모리 `MutableStateFlow` 및 `MutableList` 기반의 `FakeSwingHistoryRepository` 클래스를 작성한다.
- DB 연결 없이 `observeSessions()`, `getSessionDetail()`, `deleteSession()`, `insertMockSession()` 등이 인메모리 데이터를 갱신하고 검증 가능한 상태를 유지하도록 구현한다.

### FR-4. `HistoryViewModelTest` 및 `SessionDetailViewModelTest` 구현
- `:feature:history/src/test/`에 Coroutine Test Dispatcher(`MainDispatcherRule` 또는 `StandardTestDispatcher`)를 적용한 단위 테스트 클래스 작성.
- `HistoryViewModelTest`:
  - 세션 목록 수집(`sessions` StateFlow) 검증.
  - `insertMockSessionData()` 호출 시 진행 상태(`mockInsertInProgress`) 및 Fake Repository 데이터 변경 검증.
- `SessionDetailViewModelTest`:
  - `SavedStateHandle`에 전달된 `sessionId`로 세션 상세 정보(`uiState`) 로딩 검증.
  - `deleteSession()` 호출 시 Repository 삭제 처리 및 삭제 완료 이벤트/상태 검증.

---

## 4. 인터페이스 및 데이터 구조

- **`SwingHistoryRepository` (Interface)**:
  `package io.github.loje0611.tennisdoc.core.data.repository`
- **`SwingHistoryRepositoryImpl` (Class)**:
  `package io.github.loje0611.tennisdoc.core.data.repository`
- **`FakeSwingHistoryRepository` (Test Double Class)**:
  `package io.github.loje0611.tennisdoc.feature.history` (`src/test/`)

---

## 5. 비기능 요구사항

- **테스트 실행 속도**: JVM 단독 단위 테스트로 실행되며, 에뮬레이터나 실기기 없이 `./gradlew :feature:history:test` 명령으로 5초 이내 수행 완료되어야 함.
- **하위 호환성**: `:app` 및 기존 기능에서의 `SwingHistoryRepository` 주입 및 사용 시 런타임 오류나 동작 변경이 없어야 함.

---

## 6. 인수 조건 (Acceptance Criteria)

| # | 조건 |
|---|---|
| **AC-1** | `./gradlew :core:data:assembleDebug` 및 `./gradlew :feature:history:test` 빌드/테스트 성공. |
| **AC-2** | `./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug` 성공. |
| **AC-3** | `SwingHistoryRepository`가 인터페이스로 선언되어 있고, `SwingHistoryRepositoryImpl`이 이를 구현하고 있음. |
| **AC-4** | `HistoryViewModel` 및 `SessionDetailViewModel` 생성자가 `SwingHistoryRepository` 인터페이스를 의존성으로 선언하고 있음. |
| **AC-5** | `:feature:history/src/test/` 하위에 `FakeSwingHistoryRepository`, `HistoryViewModelTest`, `SessionDetailViewModelTest` 파일이 작성되어 있고 단위 테스트 5건 이상 통과. |
| **AC-6** | 단위 테스트 실행 시 Room DB나 Android Context 인스턴스를 요구하지 않음. |
| **AC-7** | 변경 범위가 `TennisDocAI/` 모듈 및 본 명세/태스크 보드 문서로 한정됨. |

---

## 7. 테스트 지침

명령어 실행 위치: `TennisDocAI/`

1. `:feature:history` 모듈 단위 테스트 실행:
   ```bash
   ./gradlew :feature:history:test
   ```
2. 전체 빌드 및 검증 태스크 실행:
   ```bash
   ./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
   ```
