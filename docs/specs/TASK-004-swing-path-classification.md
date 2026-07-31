# TASK-004 · Swing Path Classification — SRS

**depends_on:** none

## 1. Overview & Scope
임팩트 프레임 전후 손목 Y좌표 궤적을 선형 회귀로 분석해 구질을 `Topspin`/`Flat`/`Slice`로 분류한다. 상향 스윙 여부는 진단(`Low Path`, TASK-006)의 근거다. `pose_data` 계약과 임팩트 프레임 인덱스만 있으면 합성 데이터로 독립 단위 테스트가 가능하다.

## 2. Definitions & References
- **손목 인덱스**: 오른손 16, 왼손 15.
- **좌표계**: y 상단=0/하단=1 → **Y 감소 = 상승(Topspin)**.
- **분석 윈도우**: `analysis_window=10` 프레임. **기울기 임계값** `THRESHOLD=0.005`.

## 3. Functional Requirements
- **FR-1 (구간)**: `start=max(0, impact-10)`, `end=min(len, impact+10)` 손목 Y 시퀀스.
- **FR-2 (결측)**: NaN 제거, 유효 표본<2면 `"Unknown"`.
- **FR-3 (기울기)**: `np.polyfit(x, y, 1)` 1차 기울기.
- **FR-4 (분류)**: `slope<-0.005`→`Topspin`, `slope>0.005`→`Slice`, 그 외 `Flat`.
- **FR-5 (3D 궤적)** `get_swing_trajectory_3d`: 손목 `(x,y,z)`에서 x가 비-NaN인 행만 반환.

## 4. Interfaces & Data Structures
```python
def classify_swing_path(pose_data, impact_frame, hand='right', analysis_window=10) -> str
def get_swing_trajectory_3d(pose_data, WRIST_IDX=16) -> np.ndarray
```
- 반환: `{"Topspin","Flat","Slice","Unknown"}`.

## 5. UI/UX Requirements
N/A (backend module).

## 6. Non-Functional Requirements
- 의존성: `numpy`.

## 7. Error Handling & Edge Cases
- EH-1 `impact_frame is None`/빈 데이터 → `"Unknown"`. EH-2 유효 표본<2 → `"Unknown"`.

## 8. Acceptance Criteria
- [ ] Y 궤적 기울기로 Topspin/Flat/Slice가 분류된다.
- [ ] `slope<-0.005`=Topspin, `>0.005`=Slice, 사이=Flat.
- [ ] `hand='left'` 시 인덱스 15를 사용한다.
- [ ] 데이터 부족/None 시 `"Unknown"`을 반환한다.
- [ ] 합성 데이터만으로 단위 테스트가 통과한다.

## 9. Testing Instructions
`tennis-vision-analyzer/AI_README.md`를 따른다. `tennis-vision-analyzer/`에서:
```bash
source .venv/bin/activate && python -m unittest discover tests/
```
