# TASK-020 — A그룹 기술 부채 정리 (ProGuard 중복·사문화 규칙, CSV 타임스탬프 가시성, Hilt ViewModel 의존성 정리)

| 항목 | 값 |
|---|---|
| Task ID | TASK-020 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-009, TASK-010, TASK-012, TASK-015, TASK-017, TASK-018 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §8.2 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-11 | PM | 최초 작성 (A그룹 완료 후 식별된 기술 부채 4건 일괄 정리) |

---

## 1. 개요 및 범위

### 1.1 개요
Phase 2 A그룹(멀티모듈 분리 및 `:feature:*` 이관) 진행 과정에서 5개 핵심 모듈과 2개 피처 모듈이 안정을 찾았으나, 이 과정에서 **ProGuard 중복/사문화 규칙**, **스레드에 안전하지 않은 공개 포맷 객체 노출**, **사문화된 Hilt Navigation Compose import 및 Gradle 의존성** 등 4건의 기술 부채가 축적되었다.

본 태스크는 A그룹 완결 시점에 맞춰 위 4건의 부채를 일괄 정리함으로써 죽은 규칙 및 사문화된 의존성을 배제하고, 아키텍처 결합도를 최적화하는 데 목적이 있다.

### 1.2 범위
- **포함**:
  - `TennisDocAI/app/proguard-rules.pro` 내의 `:core:analysis` JNI keep 중복 규칙 제거 및 `io.github.loje0611.tennisdoc.data.db.**` 사문화 규칙 3줄 제거.
  - `SwingHistoryRepository` (또는 `SwingHistoryRepositoryImpl`) companion object 내 `CSV_TIMESTAMP_FORMAT` (`SimpleDateFormat`) 인스턴스의 가시성을 `private`으로 전환하고, 필요 시 `CSV_TIMESTAMP_PATTERN` (`String`) 상수만 노출.
  - `SessionDetailScreen.kt` 내 미사용 `import androidx.hilt.navigation.compose.hiltViewModel` 제거.
  - `TennisDocAI/feature/history/build.gradle.kts` 내 미사용 `implementation(libs.hilt.navigation.compose)` 의존성 삭제.
- **제외**:
  - 프로덕션 기능의 비즈니스 로직, DB 스키마, UI 레이아웃의 변경.
  - 타 모듈(`:core:sensor`, `:core:vision` 등)의 신규 기능 개발.

---

## 2. 정의 및 참조

- **참조 문서**: [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §8.2, [`docs/specs/TASK-016-history-decoupling.md`](TASK-016-history-decoupling.md), [`docs/specs/TASK-017-feature-history-module.md`](TASK-017-feature-history-module.md)

---

## 3. 기능 요구사항

### FR-1. ProGuard 규칙 중복 일원화 및 사문화 규칙 삭제
1. `TennisDocAI/app/proguard-rules.pro` 파일에서 `core/analysis/consumer-rules.pro`와 중복되는 JNI keep 규칙을 제거하여 규칙 소유권을 `:core:analysis`로 일원화한다.
2. `app/proguard-rules.pro` 내의 `io.github.loje0611.tennisdoc.data.db.**` 경로 관련 사문화된 ProGuard keep 규칙 3줄을 삭제한다.

### FR-2. `CSV_TIMESTAMP_FORMAT` 가시성 좁히기 및 Thread-safety 보장
1. `SwingHistoryRepository` 및 그 구현체의 companion object 내 `CSV_TIMESTAMP_FORMAT` (`SimpleDateFormat`) 객체의 접근 제어자를 `public`에서 `private`으로 좁힌다.
2. 포맷 서식 문자열이 외부(테스트 코드 등)에서 필요한 경우 `const val CSV_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss"` 서식 문자열 상수를 노출하거나 private 인스턴스를 내부에서 안전하게 활용하도록 개선한다.
3. 관련 테스트 소스에서 `CSV_TIMESTAMP_FORMAT`을 직접 참조하던 부분을 서식 포맷 문자열 상수를 참조하도록 업데이트한다.

### FR-3. `:feature:history` 사문화된 Hilt import 및 Gradle 의존성 제거
1. `SessionDetailScreen.kt` 파일 상단에 존재하는 미사용 `import androidx.hilt.navigation.compose.hiltViewModel`을 정결히 제거한다.
2. `TennisDocAI/feature/history/build.gradle.kts` 파일의 `dependencies` 블록에서 미사용 중인 `implementation(libs.hilt.navigation.compose)` 줄을 구심점 있게 삭제한다.

---

## 4. 인수 조건 (Acceptance Criteria)

| # | 조건 |
|---|---|
| **AC-1** | `app/proguard-rules.pro`에서 `tennisdoc.data.db.**` 가리키는 3줄과 `:core:analysis`에 중복된 JNI keep 규칙이 제거됨. |
| **AC-2** | `SwingHistoryRepository` / `SwingHistoryRepositoryImpl` 내의 `SimpleDateFormat` 객체(`CSV_TIMESTAMP_FORMAT`)가 `public`으로 노출되지 않음 (검색 시 `public val CSV_TIMESTAMP_FORMAT` 0건). |
| **AC-3** | `SessionDetailScreen.kt` 내 `import androidx.hilt.navigation.compose.hiltViewModel`이 존재하지 않음. |
| **AC-4** | `feature/history/build.gradle.kts` 내 `libs.hilt.navigation.compose` 의존성 구문이 존재하지 않음. |
| **AC-5** | `./gradlew :feature:history:assembleDebug` 및 `./gradlew :feature:history:test` 성공. |
| **AC-6** | `./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug` 전체 빌드 및 검증 통과. |
| **AC-7** | 변경 범위가 `TennisDocAI/` 서브프로젝트 및 명세/보드 문서에 한정됨. |

---

## 5. 테스트 지침

명령어 실행 위치: `TennisDocAI/`

1. 빌드 및 테스트:
   ```bash
   ./gradlew :feature:history:test :feature:history:assembleDebug
   ```
2. 전체 검증 및 바인딩 체크:
   ```bash
   ./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
   ```
