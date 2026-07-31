# TASK-003 · Impact Detection (Multi-Swing) — SRS

**depends_on:** none

## 1. Overview & Scope
손목 관절의 3D 이동 속도를 계산하고 `scipy.signal.find_peaks`로 다중 스윙 임팩트 프레임을 감지한다. 잔여 동작에 의한 거짓 감지를 필터링한다. 속도 유틸 `calculate_velocity`는 운동체인(TASK-005)에서도 재사용되는 **공용 계약**이다. `pose_data` 계약에만 의존하므로 합성 배열로 독립 단위 테스트가 가능하다.

## 2. Definitions & References
- **손목 인덱스**: 오른손 16, 왼손 15 (`hand` 인자).
- **속도 정의**: 프레임 간 3D 유클리드 거리 × fps. 기본 FPS 30.

## 3. Functional Requirements
- **FR-1** `calculate_velocity`: 관절 `(x,y,z)` 궤적 → `np.diff` → `np.linalg.norm` → `×fps`, index 0에 `0.0` 삽입. 프레임<2면 빈 배열.
- **FR-2 (결측)**: `np.nan_to_num(nan=0.0)`.
- **FR-3 (스무딩)**: `scipy.ndimage.gaussian_filter1d(sigma=2)` (ImportError 시 원본).
- **FR-4 (파라미터)**: `max_vel=max(smooth)` 기준 `height=max_vel*0.5`, `prominence=max_vel*0.3`, `distance=int(fps*2.0)`.
- **FR-5 (피크)**: `find_peaks(smooth, height, distance, prominence)` → `list[int]`.
- **FR-6 (폴백)**: `max_vel==0` → `[0]`; 피크 없음 → `[int(argmax(clean))]`.

## 4. Interfaces & Data Structures
```python
def calculate_velocity(pose_data, joint_index, fps=30) -> np.ndarray
def detect_impact_frame(pose_data, fps=30, hand='right') -> tuple[list[int] | None, np.ndarray]
```
- 반환 `(impact_frames, velocities)`. `velocities`는 스무딩 이전 원속도.
- **impact_frames 계약**: TASK-005/006/007/008이 소비.

## 5. UI/UX Requirements
N/A (backend module).

## 6. Non-Functional Requirements
- 의존성: `numpy`, `scipy`.

## 7. Error Handling & Edge Cases
- EH-1 프레임<2 → 빈 배열/`None`. EH-2 전 구간 0 → `[0]`. EH-3 피크 없음 → 최대 속도 프레임.

## 8. Acceptance Criteria
- [ ] `calculate_velocity`가 프레임 수와 동일 길이(첫 값 0) 배열을 반환한다.
- [ ] `hand='left'` 시 인덱스 15를 사용한다.
- [ ] Height 50%/Prominence 30%/Distance 2.0초 규칙이 적용된다.
- [ ] 다중 스윙이 개별 임팩트로 분리된다.
- [ ] 피크 미검출 시 최대 속도 프레임 1개를 반환한다.
- [ ] 합성 `pose_data`만으로 단위 테스트가 통과한다.

## 9. Testing Instructions
`tennis-vision-analyzer/AI_README.md`를 따른다. `tennis-vision-analyzer/`에서:
```bash
source .venv/bin/activate && python -m unittest tests/test_impact_detector.py
```
