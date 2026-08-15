# TASK-040 — History 및 세션 상세 Lab 융합 리팩터링 & 동기 리플레이 내비게이션 연동

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-15 | PM | 최초 작성 (Phase 3 Lab 훈련 기록 중심 History/SessionDetail 화면 개편, 스윙별 융합 지표 리스트 및 LabReplayScreen 내비게이션 배선 명세) |
| v2 | 2026-08-15 | PM | MockDataGenerator 개편 명세 추가: Mock 버튼 클릭 시 Match 세션 대신 Lab 훈련 세션(`sessionType="LAB"`, `drillType="FOREHAND"`) 및 3차원 원시 레코드(`lab_raw_records`: 30fps PoseFrame + 50Hz IMU + FusedSwing) 생성하여 실내/오프라인에서도 리플레이 화면 즉시 검증 가능하도록 지원 (FR-4, AC-7 신설) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Phase 3 Lab 모드에서 생성된 훈련 세션 데이터(드릴 라벨, 스윙별 라켓 페이스 상태, 5단계 운동 체인 효율, 인과 코칭 진단)를 사용자가 기록 목록 및 상세 화면에서 직관적으로 조회하고, 개별 스윙의 **동기 리플레이 뷰어(`LabReplayScreen`, [TASK-039](TASK-039-synchronized-replay-diagnostic-viewer.md))**로 직접 진입할 수 있도록 `:feature:history`, `:feature:lab`, `:app` 모듈의 내비게이션과 UI를 개편하는 작업을 규정합니다.

불필요한 모드 필터 없이 Lab 훈련 기록 목록을 즉시 노출하고, 세션 상세 화면에서 스윙별 융합 카드 목록과 원클릭 리플레이 진입 경로를 제공합니다. 또한 개발/테스트 환경에서 실센서나 카메라 없이도 즉시 리플레이 화면을 검증할 수 있도록 `MockDataGenerator`를 Lab 세션 및 `lab_raw_records` 원시 데이터 생성용으로 개편합니다.

### 1.2 범위
- `:feature:history` 모듈 UI 및 ViewModel 개편:
  - `HistoryScreen`: 각 세션 카드에 `드릴 이름`(`포핸드`, `백핸드`, `서브`, `포발리`, `백발리`), `스윙 횟수`, `세션 소요 시간`, `세션 일시` 표시. (불필요한 Match 필터 제외)
  - `SessionDetailScreen`:
    - `sessionType == "LAB"` 전용 뷰 구성:
      1. 상단 훈련 요약 헤더 (목표 드릴, 총 스윙 수, 훈련 시간, 라켓 페이스 정타율 %, 평균 에너지 전달 효율 %).
      2. 스윙별 분석 리스트 (#1, #2... 스윙별 페이스 뱃지, 에너지 효율 %, 인과 코칭 한줄 요약).
      3. 스윙 카드 탭 시 `onNavigateToReplay(sessionId, rawRecordId)` 콜백 호출.
    - `sessionType == "MATCH"` 기존 6각 레이더 차트 뷰 하위 호환 유지.
  - `SessionDetailViewModel`:
    - `SwingHistoryRepository`를 통해 해당 세션의 `List<LabRawRecordEntity>` 로드 및 `LabSessionDetailUiState` 바인딩.
  - `MockDataGenerator`:
    - `[Mock]` 버튼 클릭 시 `sessionType = "LAB"`, `drillType = "FOREHAND"` 세션 및 스윙별 `LabRawRecordEntity`(30fps PoseFrame 시계열 + 50Hz IMU 파형 + FusedSwing) 생성.
- `:app` 모듈 내비게이션 배선 (`io.github.loje0611.tennisdoc.navigation`):
  - `AppRoutes.LAB_REPLAY = "lab_replay/{sessionId}/{recordId}"` 라우트 등록.
  - `AppNavHost`에 `LabReplayScreen` 컴포저블 연결: `sessionId`/`recordId` 기반으로 `LabRawRecordEntity`의 융합 데이터를 `LabReplayViewModel`에 전달하여 화면 렌더링.
  - `LabReplayScreen`의 뒤로가기(`onBack`) 시 `NavController.popBackStack()` 처리.
- 단위 테스트 및 내비게이션 통합 검증:
  - `SessionDetailViewModelTest`: Lab 세션 원시 레코드 로드 및 상태 매핑 단위 테스트.
  - `HistoryScreenTest` & `SessionDetailScreenTest`: Compose UI 렌더링 검증.
  - `AppNavigationInstrumentedTest`: History ➔ SessionDetail ➔ LabReplay ➔ BackStack 복귀 통합 테스트.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **Lab 훈련 세션 기록 (`LabRawRecordEntity`)**: [TASK-029](TASK-029-room-db-v7-lab-session-schema.md)에서 구축된 테이블로, 세션 내 각 스윙의 `(드릴 라벨, IMU 50Hz 시계열 JSON, PoseFrame 시계열 JSON, impactOffsetMs)`를 영속화한 원시 레코드.
- **동기 리플레이 진입점**: 사용자가 과거 훈련 세션에서 특정 스윙을 선택하여 비전 스켈레톤과 IMU 파형의 1:1 동기 리플레이를 재생할 수 있는 내비게이션 경로.

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- Lab DB 스키마: [`docs/specs/TASK-029-room-db-v7-lab-session-schema.md`](TASK-029-room-db-v7-lab-session-schema.md)
- 동기 리플레이 명세: [`docs/specs/TASK-039-synchronized-replay-diagnostic-viewer.md`](TASK-039-synchronized-replay-diagnostic-viewer.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: `HistoryScreen` Lab 훈련 목록 카드 개편
- 복잡한 모드 필터 칩 없이 순수 세션 목록을 최신순으로 표시한다.
- 각 세션 아이템 카드는 다음 정보를 렌더링한다:
  - **헤더**: `formatSessionName(startTime)` (예: `2026.08.15 03:40 PM`)
  - **드릴 명칭**: `drillType`이 지정된 경우 해당 드릴의 한국어 명칭(예: `포핸드 훈련`, `포발리 훈련`), 미지정 시 `Lab 훈련`.
  - **세부 지표**: `${totalSwingCount}회 스윙 · ${formatDurationMillis(durationMillis)}`
- 카드를 탭하면 `onNavigateToSessionDetail(session.sessionId)`를 호출한다.

### FR-2: `SessionDetailScreen` Lab 세션 전용 뷰 및 스윙 리스트
- 세션의 `sessionType`에 따라 화면 구성을 분기한다:
  - **Lab 세션 (`sessionType == "LAB"`)**:
    1. **훈련 요약 카드**:
       - 목표 드릴 이름 (예: `포핸드`)
       - 총 스윙 수 및 훈련 소요 시간
       - 라켓 페이스 정타율 (전체 스윙 중 `SQUARE` 비율 %)
       - 평균 운동 체인 에너지 전달 효율 (%)
    2. **스윙별 분석 목록 (LazyColumn / Items)**:
       - 각 스윙마다 카드 아이템 표시:
         - 스윙 순번 (`스윙 #1`, `스윙 #2` 등) 및 타임스탬프
         - 라켓 페이스 뱃지: `SQUARE`(녹색), `OPEN`(주황), `CLOSED`(파랑)
         - 5단계 체인 에너지 효율 (예: `92%`)
         - 인과 코칭 요약 문구 (예: *"골반 회전 조기 개방으로 페이스 열림"*)
         - 우측 "리플레이" 아이콘 또는 카드 탭 힌트
       - 스윙 카드를 클릭하면 `onNavigateToReplay(sessionId, rawRecordId)`를 트리거한다.
  - **Match 세션 (`sessionType == "MATCH"`)**:
    - 기존의 6각형 레이더 차트 및 카테고리 브레이크다운 뷰를 그대로 유지한다.

### FR-3: `AppNavHost` 내비게이션 그래프 연결
- `AppRoutes.kt`에 리플레이 라우트 계약을 추가한다:
  ```kotlin
  const val LAB_REPLAY = "lab_replay/{sessionId}/{recordId}"
  fun createLabReplayRoute(sessionId: String, recordId: Long): String = "lab_replay/$sessionId/$recordId"
  ```
- `AppNavHost`의 `composable(AppRoutes.LAB_REPLAY)`에서:
  - 인자로 전달된 `sessionId`와 `recordId`를 추출한다.
  - 해당 레코드의 `LabRawRecordEntity` 또는 파싱된 `FusedSwing`을 `LabReplayScreen`에 주입하여 렌더링한다.
  - `onBack = { navController.popBackStack() }`를 통해 이전 세션 상세 화면으로 자연스럽게 복귀한다.

### FR-4: Lab 전용 Mock 데이터 생성기 (`MockDataGenerator`) 개편
- `HistoryViewModel.insertMockSessionData()` 실행 시:
  - `sessionType = "LAB"`, `drillType = "FOREHAND"`로 설정된 `SwingSessionEntity` 생성.
  - 10개의 가상 스윙에 대해 각각 `LabRawRecordEntity`를 생성하여:
    - 30fps 비전 포즈 시계열 (약 30프레임 PoseFrame JSON)
    - 50Hz IMU 파형 시계열 (약 50샘플 ImuDataPoint JSON)
    - `impactOffsetMs`, 라켓 페이스 상태(`SQUARE`/`OPEN`/`CLOSED`), 5단계 체인 지표
  - `SwingHistoryRepository.insertLabRawRecord()`를 통해 `lab_raw_records` 테이블에 영속화.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.feature.history

import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing

data class LabSwingSummaryItem(
    val recordId: Long,
    val swingIndex: Int,
    val timestampMillis: Long,
    val faceState: String,
    val energyEfficiency: Float,
    val coachingFeedback: String,
    val fusedSwing: FusedSwing? = null
)

data class LabSessionDetailUiState(
    val session: SwingSessionEntity? = null,
    val swingItems: List<LabSwingSummaryItem> = emptyList(),
    val squareRatePercent: Int = 0,
    val averageEnergyEfficiency: Float = 0f,
    val isLoading: Boolean = false
)
```

```kotlin
package io.github.loje0611.tennisdoc.core.data.repository

import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import kotlinx.coroutines.flow.Flow

interface SwingHistoryRepository {
    // 기존 메서드 유지...
    fun getLabRawRecordsForSession(sessionId: String): Flow<List<LabRawRecordEntity>>
    suspend fun getLabRawRecordById(recordId: Long): LabRawRecordEntity?
    suspend fun insertLabRawRecord(record: LabRawRecordEntity): Long
}
```

---

## 5. UI/UX 요구사항
- **일관된 다크 테마 디자인**: `:core:ui`의 `SwingTheme` 컬러 시스템(Electric Cyan, SubGray, CardSurface 등)을 일관되게 적용.
- **부드러운 화면 전환**: 세션 목록 ➔ 세션 상세 ➔ 리플레이 뷰어로 이어지는 화면 전환 시 자연스러운 애니메이션 적용.
- **터치 영역**: 스윙 아이템 및 뒤로가기 버튼은 최소 48dp 터치 타겟 확보.

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 성능 최적화
- `lab_raw_records`의 JSON 파싱(`imuRawJson`, `visionPosesJson`)은 무거운 연산이므로 백그라운드 Dispatchers.Default에서 비동기로 수행하여 UI 프레임 드랍을 차단한다.

### 6.2 모듈 격리
- `:feature:history`는 `:feature:lab`의 내부 구현에 직접 의존하지 않고, 공용 라우트 계약(`AppRoutes`) 및 `:core:model`, `:core:fusion`을 통해 느슨하게 결합한다.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **세션 내 스윙 레코드가 0개인 경우**: "기록된 스윙 데이터가 없습니다" 빈 상태(Empty State) UI 표시.
- **원시 레코드 파싱 실패 시**: 안전하게 Fallback 텍스트("분석 데이터 파싱 실패")를 노출하고 크래시를 방지.
- **유효하지 않은 recordId로 리플레이 진입 시**: Toast("스윙 데이터를 찾을 수 없습니다") 안내 후 이전 화면으로 `popBackStack()`.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `HistoryScreen` 카드에 Lab 드릴 명칭(예: `포핸드 훈련`), 세션 일시, 총 스윙 수, 소요 시간이 정상 렌더링된다.
- [ ] **AC-2**: `SessionDetailScreen`에서 `sessionType == "LAB"`인 경우 상단 훈련 요약과 스윙별 카드 목록(#1, #2...)이 렌더링된다.
- [ ] **AC-3**: `SessionDetailScreen`에서 스윙 카드를 탭하면 `AppRoutes.LAB_REPLAY` 라우트로 내비게이션 이동하여 `LabReplayScreen`이 열린다.
- [ ] **AC-4**: `LabReplayScreen`에서 상단 뒤로가기 버튼을 누르면 `SessionDetailScreen`으로 정상 복귀한다.
- [ ] **AC-5**: `SessionDetailViewModelTest` 및 `HistoryViewModelTest` 단위 테스트가 100% 통과한다.
- [ ] **AC-6**: `./gradlew :feature:history:test :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.
- [ ] **AC-7**: `HistoryScreen`에서 `[Mock]` 버튼 클릭 시 `sessionType == "LAB"` 세션 및 스윙별 `LabRawRecordEntity`가 DB에 생성되어, `SessionDetailScreen` ➔ `LabReplayScreen`으로 진입하여 스켈레톤과 IMU 파형이 렌더링된다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :feature:history:test :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
```
