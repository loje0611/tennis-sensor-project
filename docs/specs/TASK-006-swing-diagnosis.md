# TASK-006 · Swing Diagnosis & Feedback Generation — SRS

**depends_on:** TASK-002, TASK-004, TASK-005

## 1. Overview & Scope
스윙별로 관절 각도(TASK-002), 구질(TASK-004), 운동체인 속도(TASK-005)를 입력받아 **진단 결과와 피드백 데이터를 생성**하는 순수 로직 모듈이다. 이 모듈은 렌더링 툴팁(TASK-007)과 결과 UI 텍스트(TASK-008)가 공유하는 **단일 진단 소스**로, 기존에 `app.py`에 흩어져 있던 진단 로직을 독립 유닛(`src/swing_diagnosis.py`)으로 추출해 의존성 뒤엉킴을 제거한다. 합성 입력(각도/속도 배열/구질)으로 독립 단위 테스트가 가능하다.

## 2. Definitions & References
- **로컬 윈도우**: 임팩트 전 `fps*1.0` ~ 후 `fps*0.5`.
- **target_joint 인덱스**: 골반 24, 손목 16, 팔꿈치 14, 어깨 12.
- **팔 각도 임계값**: `arm_angle < 120`.
- **피드백 계약**: `{"text": str, "target_joint": int}`. 진단 태그(problem key): `운동 체인(하체->상체 순서)`, `팔/손목 가속`, `타점(팔 각도)`, `상향 스윙 궤적`.

## 3. Functional Requirements
- **FR-1 (로컬 피크)**: 임팩트 `frame`에 대해 `start_f=max(0,int(frame-fps*1.0))`, `end_f=min(len(vel_hip),int(frame+fps*0.5))`. 각 속도 슬라이스의 argmax로 `peak_hip/shoulder/wrist=start_f+argmax(...)`.
- **FR-2 (로컬 정상 판정)**: `is_local_correct=(peak_hip < peak_shoulder < peak_wrist)`.
- **FR-3 (진단 우선순위)** — 순서대로 평가:
  1. `peak_hip >= peak_shoulder` → text `Use Hip First`(joint 24), problem `운동 체인(하체->상체 순서)`.
  2. 아니고 `peak_shoulder >= peak_wrist` → text `Late Wrist`(joint 16), problem `팔/손목 가속`.
  3. `arm_angle < 120` → text `Arm Bent({arm_angle:.0f})`(joint 14), problem `타점(팔 각도)`.
  4. `swing_type ∈ {Flat, Slice}` → text `Low Path`(joint 16), problem `상향 스윙 궤적`.
  5. 위가 하나도 없으면 → text `Good Swing!`(joint 12).
- **FR-4 (출력)**: 스윙별 피드백 리스트와 problem 태그 리스트를 반환하여 (a) 렌더링 툴팁(TASK-007), (b) UI 진단 텍스트·최빈 문제 집계(TASK-008)에 제공한다.
- **FR-5 (진단 텍스트/처방)**: 사용자 표시용 한글 문구와 처방은 TASK-008 §4.2에 정의된 매핑을 사용한다. 본 모듈은 태그/영문 text를 생성하고, 표시 문구 매핑은 UI 계층이 소유한다.

## 4. Interfaces & Data Structures
권장 시그니처(구현 시 조정 가능):
```python
def build_swing_feedbacks(impact_frames: list[int], swing_types: list[str],
                          arm_angles: list[float], chain_velocities: dict, fps: float
                          ) -> tuple[dict[int, list[dict]], list[str]]
```
- 반환: `(swing_feedbacks, all_problems)` — `swing_feedbacks[frame]=[{"text","target_joint"}]`, `all_problems`는 problem 태그 누적 리스트.

## 5. UI/UX Requirements
N/A (backend logic). 표시 문구/처방은 TASK-008이 소유.

## 6. Non-Functional Requirements
- 의존성: `numpy`. 입력은 TASK-002/004/005의 출력 계약.

## 7. Error Handling & Edge Cases
- EH-1 `chain_velocities` 없음 → 운동체인 진단 스킵, 각도/구질 진단만 수행.
- EH-2 `frame >= len(arm_angles)` → `arm_angle=0` 대체.
- EH-3 `start_f >= end_f` → 운동체인 진단 스킵.

## 8. Acceptance Criteria
- [ ] 로컬 윈도우(전 1.0초~후 0.5초)에서 골반/어깨/손목 피크를 계산한다.
- [ ] FR-3 우선순위대로 5종 태그(`Use Hip First`/`Late Wrist`/`Arm Bent`/`Low Path`/`Good Swing!`)를 생성한다.
- [ ] `arm_angle<120`이 `Arm Bent`로 각도와 함께 생성된다.
- [ ] problem 태그 리스트가 최빈 문제 집계용으로 반환된다.
- [ ] `chain_velocities` 부재 시 각도/구질 진단만 수행한다.
- [ ] 합성 입력만으로 단위 테스트가 통과한다.

## 9. Testing Instructions
`tennis-vision-analyzer/AI_README.md`를 따른다. `tennis-vision-analyzer/`에서:
```bash
source .venv/bin/activate && python -m unittest discover tests/
```
