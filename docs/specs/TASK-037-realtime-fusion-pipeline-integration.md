# TASK-037 — 실시간 센서-비전 스트림 융합 파이프라인 연동

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (Phase 3 D그룹: CameraX 포즈 스트림과 BLE IMU 스트림을 실시간 링버퍼로 융합하고 DB에 영속화하는 파이프라인 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 CameraX 비전 파이프라인(`PoseAnalysisAnalyzer`, 30fps)에서 생성되는 `PoseFrame` 스트림과 BLE 센서 수신 파이프라인(50Hz)에서 유입되는 `ImuDataPoint` 스트림을 **실시간 링 버퍼(Rolling Ring Buffer)** 로 수집하고, 스윙 트리거 발생 시 `:core:fusion`의 `FusionEngine` 및 `StatisticalAnomalyDetector`를 구동하여 융합 결과(`FusedSwing`)와 이상 진단 리포트를 생성한 뒤 Room DB(`lab_raw_records`, D-7.2)에 영속화하는 **실시간 스트림 융합 파이프라인(`LabFusionPipeline`)** 을 `:feature:lab` 모듈에 구현하는 작업을 규정합니다.

### 1.2 범위
- `:feature:lab` 모듈 내 실시간 융합 파이프라인 패키지(`io.github.loje0611.tennisdoc.feature.lab.pipeline`) 구축:
  - `LabFusionStreamBuffer`: 최근 3.0초(비전 약 90프레임, IMU 약 150샘플) 롤링 윈도우를 스레드 안전하게 관리하는 링 버퍼.
  - `LabFusionPipeline`: 비전/센서 프레임 인입, 스윙 트리거 감지 시 `FusionEngine.fuse()` 호출, `PersonalBaseline` 점진적 업데이트, `StatisticalAnomalyDetector` 실행, `LabRawRecordEntity` Room DB 저장(D-7.2) 및 UI StateFlow 발행을 총괄하는 파이프라인 매니저.
- `:feature:lab`의 `LabViewModel` 연동:
  - `LabViewModel`이 `LabFusionPipeline`을 주입받아 실시간 카메라 프레임 분석 결과 및 BLE 센서 패킷을 버퍼에 공급.
  - `latestFusedSwing: StateFlow<FusedSwing?>` 및 `latestAnomalyReport: StateFlow<BaselineComparisonReport?>` 노출.
- Room DB (`lab_raw_records`) 영속화:
  - 스윙 완료 시 `LabRawRecordDao.insert(LabRawRecordEntity(...))`를 통해 원시 JSON과 융합 메타데이터 저장.
- 단위 및 계측 테스트:
  - 링 버퍼 수명주기 및 메모리 누수 방지 테스트.
  - 모의 스트림(Mock Streams) 주입 ➔ 스윙 트리거 ➔ 융합 결과 및 DB 삽입 검증.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **실시간 링 버퍼 (Rolling Ring Buffer)**: 지정된 시간 윈도우(3.0초) 이전의 오래된 프레임을 자동 폐기하고 최신 프레임만 유지하는 순환 큐.
- **`LabRawRecordEntity`**: TASK-029에서 정의된 Lab 원시 데이터 테이블 (`sessionId`, `drillType`, `imuRawJson`, `poseJson`, `fusedMetaJson`, `timestamp`).

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- DB v8 스키마 명세: [`docs/specs/TASK-029-room-db-v7-lab-session-schema.md`](TASK-029-room-db-v7-lab-session-schema.md)
- 융합 엔진 명세: [`docs/specs/TASK-034-causal-coaching-engine.md`](TASK-034-causal-coaching-engine.md)
- 이상 탐지 명세: [`docs/specs/TASK-036-personal-baseline-anomaly-detection.md`](TASK-036-personal-baseline-anomaly-detection.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 실시간 링 버퍼 관리 (`LabFusionStreamBuffer`)
- `addPoseFrame(frame: PoseFrame)`:
  - 비전 포즈 프레임 추가. 현재 타임스탬프 기준 $3000\text{ms}$ 이전의 오래된 프레임은 즉시 제거.
- `addImuSample(sample: ImuDataPoint)`:
  - 센서 IMU 샘플 추가. 현재 타임스탬프 기준 $3000\text{ms}$ 이전의 오래된 샘플은 즉시 제거.
- `snapshot(): Pair<List<PoseFrame>, List<ImuDataPoint>>`:
  - 스레드 안전하게 현재 버퍼에 저장된 포즈 및 IMU 리스트의 스냅샷을 복사하여 반환.
- `clear()`: 세션 시작/종료 시 버퍼 초기화.

### FR-2: 스윙 트리거 및 융합 처리 파이프라인 (`LabFusionPipeline`)
- `processSwing(sessionId: String, drillType: DrillType)`:
  1. `LabFusionStreamBuffer.snapshot()`으로부터 최근 3초간의 `poses`와 `imuSamples` 획득.
  2. `FusionEngine.fuse(drillType, poses, imuSamples)` 실행 ➔ `FusedSwing` 생성.
  3. `baselineTracker.update(drillType, fusedSwing)` ➔ 점진적 `PersonalBaseline` 갱신.
  4. `anomalyDetector.detectAnomalies(baseline, fusedSwing)` ➔ `BaselineComparisonReport` 생성.
  5. JSON 직렬화 후 `labRawRecordDao.insert(LabRawRecordEntity(...))`를 비동기 IO로 실행.
  6. `_latestFusedSwing.value = fusedSwing`, `_latestAnomalyReport.value = report` 상태 갱신.

### FR-3: 세션 미활성 가드 (Session Guard)
- `SwingAnalysisSessionState.isSessionActive.value == false`이거나 `sessionId`가 없는 경우, 스트림 버퍼링은 수행하되 스윙 융합 분석 및 DB 영속화는 실행하지 않는다.

### FR-4: Hilt 의존성 주입 및 `LabViewModel` 배선
- `LabFusionPipeline`을 싱글톤 또는 ViewModelScoped로 Hilt 바인딩.
- `LabViewModel`에서 카메라 `PoseAnalysisAnalyzer.onPoseDetected` 및 센서 수신 채널을 `LabFusionPipeline`에 전달.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

```kotlin
package io.github.loje0611.tennisdoc.feature.lab.pipeline

import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport
import io.github.loje0611.tennisdoc.core.fusion.anomaly.PersonalBaseline
import io.github.loje0611.tennisdoc.core.fusion.anomaly.StatisticalAnomalyDetector
import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngine
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import kotlinx.coroutines.flow.StateFlow

class LabFusionStreamBuffer(
    private val bufferDurationMs: Long = 3000L
) {
    fun addPoseFrame(frame: PoseFrame)
    fun addImuSample(sample: ImuDataPoint)
    fun snapshot(): Pair<List<PoseFrame>, List<ImuDataPoint>>
    fun clear()
}

interface LabFusionPipeline {
    val latestFusedSwing: StateFlow<FusedSwing?>
    val latestAnomalyReport: StateFlow<BaselineComparisonReport?>
    val currentBaseline: StateFlow<PersonalBaseline?>

    fun feedPoseFrame(frame: PoseFrame)
    fun feedImuSample(sample: ImuDataPoint)
    suspend fun onSwingTriggered(sessionId: String, drillType: DrillType): FusedSwing?
    fun reset()
}
```

---

## 5. UI/UX 요구사항
- **실시간 반응성**: 스윙 감지 트리거 후 $50\text{ms}$ 이내에 `latestFusedSwing` StateFlow가 갱신되어 UI 렌더링에 지연이 없어야 함.

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 스레드 안전성 (Thread Safety)
- 비전(CameraX Background Executor)과 센서(BLE Coroutine Dispatcher)가 동시에 버퍼에 쓰기 작업을 수행해도 `ConcurrentModificationException`이 발생하지 않도록 `synchronized` 또는 동시성 컬렉션 적용.

### 6.2 메모리 효율성
- 3초 초과 프레임은 즉각 GC 대상이 되도록 제거하여 장시간 실행 시에도 메모리 누수가 발생하지 않아야 함.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **버퍼 데이터 부족 (스윙 직후 바로 트리거 발생)**: 사용 가능한 프레임만으로 `fuse()` 호출 또는 안전 폴백 반환.
- **DB 삽입 실패**: 예외 로그 기록 후 UI 흐름은 중단되지 않고 정상 전달 (`NonCancellable` IO).

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `LabFusionStreamBuffer` 및 `LabFusionPipeline` 클래스가 `:feature:lab`에 구현되고 컴파일에 성공한다.
- [ ] **AC-2**: 링 버퍼에 4초 분량의 데이터를 주입했을 때 정확히 최근 3초 이내의 데이터만 보존된다.
- [ ] **AC-3**: `onSwingTriggered` 호출 시 `FusionEngine.fuse()`가 실행되어 `latestFusedSwing` StateFlow로 방출된다.
- [ ] **AC-4**: `onSwingTriggered` 호출 시 `StatisticalAnomalyDetector`가 실행되어 `latestAnomalyReport`가 방출된다.
- [ ] **AC-5**: `onSwingTriggered` 성공 시 Room DB `lab_raw_records` 테이블에 원시 JSON 행이 정상 삽입된다.
- [ ] **AC-6**: `LabViewModel`이 `LabFusionPipeline`의 상태를 구독하여 UI 상태를 노출한다.
- [ ] **AC-7**: `./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
```
