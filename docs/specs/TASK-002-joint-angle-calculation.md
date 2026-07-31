# TASK-002 · Joint Angle Calculation — SRS

**depends_on:** none

## 1. Overview & Scope
세 관절점으로 3D 내적 각도를 계산하는 순수 기하 유틸(`angle_calculator`)을 정의한다. 단일 프레임 포즈에서 팔 펴짐 각도와 무릎 굽힘 각도를 산출하며, 진단(TASK-006)과 각도 그래프(TASK-008)의 근거가 된다. `pose_data` 계약(TASK-001 §2)에만 의존하므로 합성 좌표로 독립 단위 테스트가 가능하다.

## 2. Definitions & References
- **관절 인덱스(오른손)**: 어깨 12, 팔꿈치 14, 손목 16, 골반 24, 무릎 26, 발목 28.
- **각도 범위**: 0~180도. 유효하지 않으면 `NaN`.

## 3. Functional Requirements
- **FR-1** `calculate_3d_angle(a, b, c)`: 벡터 `ba=a-b`, `bc=c-b`의 코사인(내적/노름) → `[-1,1]` 클리핑 → `arccos` → degree.
  - a/b/c에 NaN 포함 또는 노름 0 → `NaN`.
- **FR-2** `get_joint_angles_from_pose(pose_frame)`:
  - `right_arm_angle` = angle(12, 14, 16), `right_knee_angle` = angle(24, 26, 28).
  - 관절 33개 미만이면 각도 NaN dict.

## 4. Interfaces & Data Structures
```python
def calculate_3d_angle(a, b, c) -> float           # degree 또는 NaN
def get_joint_angles_from_pose(pose_frame) -> dict  # {"right_arm_angle", "right_knee_angle"}
```

## 5. UI/UX Requirements
N/A (backend module).

## 6. Non-Functional Requirements
- 의존성: `numpy`.

## 7. Error Handling & Edge Cases
- EH-1 NaN 좌표/제로 벡터 → `NaN`. EH-2 관절 수 부족 → NaN dict.

## 8. Acceptance Criteria
- [ ] 일직선 세 점 → 180°, 직각 → 90°를 정확히 계산한다.
- [ ] NaN 입력/제로 벡터 시 `NaN`을 반환한다.
- [ ] `get_joint_angles_from_pose`가 팔/무릎 각도 dict를 반환한다.
- [ ] 합성 좌표만으로 단위 테스트가 통과한다.

## 9. Testing Instructions
`tennis-vision-analyzer/AI_README.md`를 따른다. `tennis-vision-analyzer/`에서:
```bash
source .venv/bin/activate && python -m unittest tests/test_angle_calculator.py
```
