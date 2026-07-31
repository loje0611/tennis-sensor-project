# TASK-005 · Kinetic Chain Analysis — SRS

**depends_on:** TASK-003

## 1. Overview & Scope
골반→어깨→손목의 최대 가속 순서를 검증하는 운동체인 분석(`kinetic_chain`)을 정의한다. 각 관절 속도 산출에 TASK-003의 `calculate_velocity`를 재사용한다. 산출물은 진단(TASK-006)과 운동체인 그래프(TASK-008)의 근거다.

## 2. Definitions & References
- **관절 인덱스(오른손)**: 골반 24, 어깨 12, 손목 16. 왼손: 23/11/15.
- **정상 체인**: 골반 → 어깨 → 손목 순 가속 피크.
- **의존 함수**: `impact_detector.calculate_velocity` (TASK-003, import).

## 3. Functional Requirements
- **FR-1 (속도)**: 골반/어깨/손목 각각 `calculate_velocity` 후 `nan_to_num(nan=0.0)`.
- **FR-2 (피크)**: 각 속도 `np.argmax`로 `peak_hip/shoulder/wrist`.
- **FR-3 (타이밍)**: `ms_per_frame=1000/fps`로 `hip_to_shoulder`, `shoulder_to_wrist`(ms).
- **FR-4 (순서)**: `is_correct_chain=(peak_hip<=peak_shoulder) and (peak_shoulder<=peak_wrist)`.
- **FR-5**: 속도 배열이 비면 `None`.

## 4. Interfaces & Data Structures
```python
def analyze_kinetic_chain(pose_data, fps=30, hand='right') -> dict | None
```
반환:
```python
{
  "peak_frames": {"hip": int, "shoulder": int, "wrist": int},
  "timing_ms": {"hip_to_shoulder": float, "shoulder_to_wrist": float},
  "is_correct_chain": bool,
  "velocities": {"hip": np.ndarray, "shoulder": np.ndarray, "wrist": np.ndarray}
}
```
> `velocities`는 TASK-006(스윙별 로컬 진단)과 TASK-008(그래프)이 슬라이싱해 사용한다.

## 5. UI/UX Requirements
N/A (backend module).

## 6. Non-Functional Requirements
- 의존성: `numpy`, 내부 `impact_detector`(TASK-003).

## 7. Error Handling & Edge Cases
- EH-1 속도 배열 공백 → `None`. EH-2 NaN 속도 → 0 치환.

## 8. Acceptance Criteria
- [ ] peak_frames·timing_ms·is_correct_chain·velocities를 포함한 dict를 반환한다.
- [ ] `is_correct_chain`이 골반≤어깨≤손목 순서를 검증한다.
- [ ] `calculate_velocity`(TASK-003)를 재사용한다.
- [ ] 속도 데이터가 없으면 `None`을 반환한다.
- [ ] 합성 `pose_data`만으로 단위 테스트가 통과한다.

## 9. Testing Instructions
`tennis-vision-analyzer/AI_README.md`를 따른다. `tennis-vision-analyzer/`에서:
```bash
source .venv/bin/activate && python -m unittest discover tests/
```
