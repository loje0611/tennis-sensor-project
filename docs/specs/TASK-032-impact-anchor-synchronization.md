# TASK-032 — 임팩트 앵커(Impact Anchor) 시간 동기화 알고리즘 구현

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (Phase 3 B그룹: 고주파 IMU와 30fps 비전 포즈 간 임팩트 앵커 시간 동기화 알고리즘 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 스마트폰 카메라(CameraX 30fps)에서 캡처된 `PoseFrame` 시계열과 라켓 센서(BLE IMU 50Hz)에서 수신된 `ImuDataPoint` 시계열 간의 시간축을 물리적 임팩트 시점을 기준으로 정렬하는 **임팩트 앵커 동기화(Impact Anchor Synchronization) 알고리즘**을 `:core:fusion` 모듈에 구현하는 작업을 규정합니다.

스마트폰과 센서는 독립적인 클록과 샘플링 레이트를 가지므로, 공과 라켓이 충돌하는 급격한 물리적 충격 순간을 앵커(기준점 $t=0$)로 식별하고 두 데이터 스트림의 시간 오프셋($\Delta t$)을 보정하여 $\le \pm 33\text{ms}$ (비전 1프레임 이내) 정밀도의 동기화 앵커(`SyncAnchor`)를 산출합니다.

### 1.2 범위
- `:core:fusion` 모듈 내 동기화 엔진 및 알고리즘 구현:
  - `ImpactAnchorSynchronizer`: 센서-비전 시계열 입력으로부터 임팩트 시점을 검출하고 `SyncAnchor`를 계산하는 핵심 클래스.
  - 비전 임팩트 검출: 손목 관절(Right/Left Wrist)의 3D 속도 극대점 및 가속 방향 반전 프레임 탐색 (`:core:vision` `ImpactDetector` 연계 또는 3D 속도 분석).
  - 센서 임팩트 검출: 3축 가속도 합성 크기($\text{Magnitude} = \sqrt{a_x^2 + a_y^2 + a_z^2}$)의 최대 충격 피크(Impact Spike) 및 자이로 각속도 변곡점 탐색.
  - 시간 오프셋 보정 및 동기화 신뢰도(`confidence: Float`) 산출.
- 골든 픽스처(Golden Fixtures) 기반 JVM 단위 테스트 구축:
  - 정상 동기화 케이스 (정확한 임팩트 스파이크와 손목 속도 피크 매칭).
  - 시간차 보정 케이스 (센서가 비전보다 $\pm 50\text{ms}$ 먼저/늦게 기록된 경우).
  - 노이즈 및 약한 임팩트 엣지 케이스 (빈 스트림, 완만한 스윙).

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **임팩트 앵커(Impact Anchor)**: 테니스 스윙에서 공이 라켓 스트링에 충돌하는 순간으로, 비전 손목 속도 정점과 센서 가속도 충격 피크가 일치하는 물리적 기준 시점.
- **`SyncAnchor`**: TASK-031에서 정의된 동기화 결과 모델 (`visionImpactTimestampMs`, `sensorImpactTimestampMs`, `timeOffsetMs`, `confidence`, `isSynchronized`).

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 융합 데이터 계약: [`docs/specs/TASK-031-core-fusion-module-data-contracts.md`](TASK-031-core-fusion-module-data-contracts.md)
- 비전 임팩트 감지: [`docs/specs/TASK-022-core-vision-impact-detector.md`](TASK-022-core-vision-impact-detector.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 비전 임팩트 피크 탐색 (`detectVisionImpact`)
- `poses: List<PoseFrame>` 시계열로부터 주 스윙 손목(Right Wrist index 16 또는 Left Wrist index 15)의 3D 속도 크기를 계산한다.
- 3D 속도 $v_t = \frac{\sqrt{(x_t - x_{t-1})^2 + (y_t - y_{t-1})^2 + (z_t - z_{t-1})^2}}{\Delta t}$
- 속도가 극대화된 후 급격히 감속되는 피크 프레임을 찾아 해당 프레임의 `timestampMs`를 `visionImpactTimestampMs`로 결정한다.
- 유효한 포즈 프레임이 없거나 속도 피크가 불분명한 경우 `null`을 반환한다.

### FR-2: 센서 임팩트 피크 탐색 (`detectSensorImpact`)
- `imuSamples: List<ImuDataPoint>` 시계열로부터 3축 가속도 크기 $a_{\text{mag}} = \sqrt{a_x^2 + a_y^2 + a_z^2}$ 및 자이로 각속도 크기 $\omega_{\text{mag}} = \sqrt{g_x^2 + g_y^2 + g_z^2}$를 계산한다.
- 가속도 크기가 사전 정의된 임팩트 임계값(예: $\ge 4.0\text{g}$)을 초과하는 지점 중 최대 극대값 피크를 탐색한다.
- 피크 지점의 `timestampMs`를 `sensorImpactTimestampMs`로 결정한다.

### FR-3: 시간 오프셋 계산 및 정렬 (`calculateSyncAnchor`)
- `timeOffsetMs = sensorImpactTimestampMs - visionImpactTimestampMs`
- 시간 오프셋의 절대값이 유효 동기화 윈도우(기본값 $\le 150\text{ms}$) 이내인 경우 `isSynchronized = true`로 설정한다.
- 신뢰도 점수 `confidence` (0.0f ~ 1.0f) 계산식:
  - 센서 피크 강도 비례 점수 (가속도 크기가 뚜렷할수록 상승, 가중치 0.5)
  - 시간차 근접도 점수 ($1.0 - \frac{|timeOffsetMs|}{150\text{ms}}$, 가중치 0.5)
  - 시간차가 150ms를 초과하거나 피크가 미검출된 경우 `confidence = 0.0f`, `isSynchronized = false`.

### FR-4: 동기화 실패 및 예외 안전성
- 비전 포즈 리스트가 비어있거나, IMU 샘플이 비어있는 경우 예외를 던지지 않고 `isSynchronized = false`, `confidence = 0f`인 기본 `SyncAnchor` 객체를 안전하게 반환한다.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

### 4.1 `ImpactAnchorSynchronizer` 인터페이스 및 클래스
```kotlin
package io.github.loje0611.tennisdoc.core.fusion.sync

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

class ImpactAnchorSynchronizer(
    private val maxAllowedOffsetMs: Long = 150L,
    private val minAccelImpactThresholdG: Float = 4.0f
) {
    /**
     * 비전 포즈 프레임과 IMU 센서 샘플을 비교하여 임팩트 앵커를 계산한다.
     */
    fun synchronize(
        poses: List<PoseFrame>,
        imuSamples: List<ImuDataPoint>
    ): SyncAnchor
}
```

---

## 5. UI/UX 요구사항
- **N/A (순수 JVM 동기화 알고리즘 모듈)**

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 동기화 정밀도 (Sync Precision)
- 정상 스윙 시뮬레이션 및 골든 픽스처 데이터셋에서 동기화 오차 $|dt| \le 33\text{ms}$ (1 비전 프레임 이내)를 만족해야 한다.

### 6.2 실행 성능 (Performance)
- 3초 분량 스윙(비전 90프레임, IMU 150샘플) 기준 동기화 계산 지연 시간 $5\text{ms}$ 미만.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **센서 가속도 스파이크 부재 (헛스윙/느린 연습 스윙)**: `confidence = 0f`, `isSynchronized = false` 반환.
- **비전 손목 가려짐 (Wrist Visibility < 0.5)**: 신뢰도 감점 및 어깨/팔꿈치 속도로 대체 추정 또는 미동기화 처리.
- **다중 임팩트 스파이크 (라켓 헤드 흔들림)**: 윈도우 내 최대 크기의 단일 최고 피크를 주 앵커로 선정.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `ImpactAnchorSynchronizer` 클래스가 `:core:fusion` 모듈에 구현되고 컴파일에 성공한다.
- [ ] **AC-2**: 비전 손목 속도 피크와 센서 가속도 피크가 동일한 시점($t=1000\text{ms}$)에 존재할 때 `timeOffsetMs == 0L`, `isSynchronized == true`, `confidence >= 0.8f`를 산출한다.
- [ ] **AC-3**: 센서 피크가 비전보다 30ms 늦게 발생($t=1030\text{ms}$)할 때 `timeOffsetMs == 30L`, `isSynchronized == true`가 정확히 계산된다.
- [ ] **AC-4**: 센서와 비전 피크의 시간차가 200ms 이상 벌어지면 `isSynchronized == false`, `confidence == 0f`로 처리된다.
- [ ] **AC-5**: 빈 리스트 또는 가속도 임계값 미만 데이터가 전달될 때 크래시 없이 안전하게 기본 `SyncAnchor`를 반환한다.
- [ ] **AC-6**: 골든 픽스처(JSON) 기반 동기화 단위 테스트가 100% 통과한다.
- [ ] **AC-7**: `./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :core:fusion:test verifyModuleDependencies :app:assembleDebug
```
