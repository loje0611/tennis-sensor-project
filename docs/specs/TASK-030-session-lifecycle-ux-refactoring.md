# TASK-030 — 세션 라이프사이클 UX 개편 (모드 선택 기반 세션 생성 및 `service/`·`session/` 정비)

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (D-9.1 후속조치 #3 BLE 자동 세션 생성 탈피 및 Lab/Match 명시적 세션 제어 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Phase 3 Lab 모드 및 Match 모드를 지원하기 위해, 기존 **"BLE 연결 시 무조건 세션 자동 생성"** 구조를 탈피하고 **"사용자의 모드 선택 및 측정 시작/종료 액션 기반 명시적 세션 제어(D-9.1 후속조치 #3)"** 로 세션 라이프사이클을 개편하는 작업을 정의합니다. `SwingAnalysisForegroundService`와 `SwingAnalysisSessionState`의 결합을 분리하여, BLE 센서 연결 상태와 세션 활성화 상태를 독립적으로 관리하고 `SessionType`(`MATCH`/`LAB`) 및 `DrillType`에 맞춘 세션 생성/기록/종료 파이프라인을 구축합니다.

### 1.2 범위
- `SwingAnalysisSessionState` 상태 관리 확장:
  - `activeSessionId: StateFlow<String?>`
  - `activeSessionType: StateFlow<SessionType?>`
  - `activeDrillType: StateFlow<DrillType?>`
  - `isSessionActive: StateFlow<Boolean>`
  - 명시적 세션 시작(`startSession`), 종료(`finishSession`), 취소(`cancelSession`) API 제공.
- `SwingAnalysisForegroundService` 리팩터링:
  - BLE 연결 시 자동 provisional 세션 생성 로직 제거 (BLE 연결 유지와 세션 수명 분리).
  - 세션이 활성화된 상태(`isSessionActive == true`)에서만 스윙 이벤트 DB 삽입(`insertEvent`) 및 누적 카운트 갱신 수행.
  - 서비스 정지 또는 세션 종료 시 활성 세션 타입(`MATCH`/`LAB`) 및 `drillType`을 반영하여 `finalizeSession` 호출.
- `:core:data`의 `SwingHistoryRepository` 확장:
  - `startSession(sessionType: SessionType, drillType: DrillType?): String` 메서드 추가 (또는 `insertProvisionalSession` 파라미터 확장).
  - `finalizeSession` 호출 시 `sessionType` 및 `drillType`을 올바르게 보존.
- 단위 테스트:
  - BLE 연결 상태에서 세션 미시작 시 스윙 데이터 비저장 검증.
  - `startSession(LAB, FOREHAND_TOPSPIN)` ➔ 스윙 이벤트 발생 ➔ `finishSession` 라이프사이클 전체 검증.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **`D-9.1 후속조치 #3`**: BLE 연결 시 자동 세션 생성 ➔ 사용자가 모드를 먼저 선택하고 세션을 시작하는 UX 변경 원칙.
- **`SessionType`**: `MATCH` (자유 경기/연습 모드) 또는 `LAB` (단일 드릴 정밀 분석 모드).
- **`DrillType`**: Lab 모드에서 선택된 정답 드릴 (예: `FOREHAND_TOPSPIN`, `SERVE` 등).

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 제품 방향 결정: [`docs/PRODUCT_DIRECTION.md`](../PRODUCT_DIRECTION.md) (D-9.1)
- DB v8 스키마 명세: [`docs/specs/TASK-029-room-db-v7-lab-session-schema.md`](TASK-029-room-db-v7-lab-session-schema.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: BLE 연결과 세션 생성의 분리
- `SwingAnalysisForegroundService`는 BLE 연결(`BleConnectionState.Connected`) 시 임시 세션(`insertProvisionalSession`)을 **더 이상 자동으로 생성하지 않는다.**
- BLE 연결 시에는 센서 파이프라인 준비 및 연결 상태(`SwingAnalysisSessionState.updateConnection`)만 갱신한다.
- BLE 연결이 끊어지더라도 활성 세션이 없으면 `finalizeSession` 또는 `deleteSession`을 호출하지 않는다.

### FR-2: 명시적 세션 라이프사이클 제어 (`SwingAnalysisSessionState`)
- `SwingAnalysisSessionState`에 다음 세션 제어 함수 및 상태를 추가한다:
  - `fun startSession(type: SessionType, drillType: DrillType? = null): String`:
    - 신규 `sessionId` 생성 및 상태 등록 (`activeSessionId`, `activeSessionType`, `activeDrillType`, `isSessionActive = true`).
    - 시작 타임스탬프 기록 및 스윙 카운터 초기화.
    - Repository를 통해 DB에 `SwingSessionEntity(sessionId, sessionType = type.name, drillType = drillType?.name, ...)` 임시 세션 등록.
  - `fun finishSession()`:
    - 현재 세션의 스윙 수, 지속시간, 구종별 카운트를 취합하여 `finalizeSession` 수행.
    - `isSessionActive = false`, `activeSessionId = null`로 초기화.
  - `fun cancelSession()`:
    - 현재 세션을 저장하지 않고 DB에서 삭제(`deleteSession`) 후 상태 리셋.

### FR-3: 스윙 이벤트 수집 시 세션 활성 가드 (Session Active Guard)
- 센서 분석 파이프라인에서 스윙이 감지(`onSwingDetected`)되었을 때:
  - `isSessionActive.value == true`인 경우: `activeSessionId`를 FK로 하여 `swing_events` 테이블에 삽입하고 세션 스윙 카운트를 증가시킨다.
  - `isSessionActive.value == false`인 경우: 실시간 감지 레이블(`detectedSwingLabel`) 표시는 유지하되, DB에 스윙 이벤트를 영속화하지 않는다.

### FR-4: `SwingHistoryRepository` 세션 생성 및 완료 인터페이스 정비
- `SwingHistoryRepository` 인터페이스 및 구현체에 세션 타입과 드릴 타입을 지원하도록 확장:
  - `insertProvisionalSession(session: SwingSessionEntity)`
  - `finalizeSession(...)` 호출 시 `sessionType` 및 `drillType` 컬럼 값이 손실되지 않고 업데이트되도록 보장.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

### 4.1 `SwingAnalysisSessionState` 확장 API
```kotlin
package io.github.loje0611.tennisdoc.session

import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import kotlinx.coroutines.flow.StateFlow

object SwingAnalysisSessionState {
    val activeSessionId: StateFlow<String?>
    val activeSessionType: StateFlow<SessionType?>
    val activeDrillType: StateFlow<DrillType?>
    val isSessionActive: StateFlow<Boolean>

    fun startSession(type: SessionType, drillType: DrillType? = null): String
    fun finishSession()
    fun cancelSession()
}
```

### 4.2 세션 생성 엔티티 매핑
```kotlin
val newSession = SwingSessionEntity(
    sessionId = sid,
    sessionName = SwingSessionEntity.formatSessionName(startTime),
    startTime = startTime,
    sessionType = type.name,
    drillType = drillType?.name
)
```

---

## 5. UI/UX 요구사항
- **BLE 연결 인디케이터**: 센서가 연결되어도 "세션 측정 중" 상태가 아니라 "센서 연결 완료 (대기 중)" 상태를 명확히 표시.
- **모드별 진입 UX**: 사용자가 Lab 또는 Match 화면에서 "시작" 버튼을 누를 때 세션이 시작되고 타이머가 동작함.

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 스레드 안전성 (Thread Safety)
- `startSession`, `finishSession`, `cancelSession`은 UI 스레드 및 코루틴 백그라운드 스레드에서 안전하게 호출 가능해야 함 (원자적 StateFlow 갱신 및 Job 관리).

### 6.2 데이터 무결성
- 0개의 스윙으로 종료된 세션은 기존 비즈니스 룰에 따라 자동 삭제(Discard)되거나 최소 세션 기준을 충족할 때만 저장.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **세션 실행 중 BLE 연결 끊김**: 세션은 즉시 파기되지 않고 일시 중지 상태가 되거나, 연결 복구 시 동일 `sessionId`로 이어서 기록 가능하도록 처리.
- **세션 미시작 상태에서 센서 스윙 발생**: UI에 감지된 스윙 이름은 표시되지만 DB에는 기록되지 않음.
- **앱 강제 종료 후 재실행**: 이전 미완료 임시 세션 정리 로직 유지.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `SwingAnalysisSessionState`에 `activeSessionId`, `activeSessionType`, `activeDrillType`, `isSessionActive` 상태와 `startSession`, `finishSession`, `cancelSession` 함수가 제공된다.
- [ ] **AC-2**: BLE 연결 시 자동으로 `insertProvisionalSession`이 호출되지 않고 대기 상태를 유지한다.
- [ ] **AC-3**: `startSession(SessionType.LAB, DrillType.FOREHAND_TOPSPIN)` 호출 시 DB에 해당 타입의 세션이 생성되고 `isSessionActive == true`가 된다.
- [ ] **AC-4**: `isSessionActive == false` 상태에서 발생한 스윙 이벤트는 DB `swing_events`에 기록되지 않는다.
- [ ] **AC-5**: `isSessionActive == true` 상태에서 발생한 스윙 이벤트는 현재 `activeSessionId`로 정상 기록된다.
- [ ] **AC-6**: `finishSession` 호출 시 세션이 정상 확정(`endTime`, `totalSwingCount`, `sessionType`, `drillType` 저장)된다.
- [ ] **AC-7**: `./gradlew :core:data:test :app:testDebugUnitTest verifyModuleDependencies` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :core:data:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
```
