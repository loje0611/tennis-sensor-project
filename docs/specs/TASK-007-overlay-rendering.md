# TASK-007 · Skeleton Overlay & Tooltip Rendering — SRS

**depends_on:** TASK-001, TASK-003, TASK-006

## 1. Overview & Scope
원본 영상 위에 관절 스켈레톤을 그리고, 임팩트 프레임에서 시각 효과(붉은 테두리·`IMPACT!`)와 오토 일시정지·진단 툴팁을 합성해 분석 영상(`mp4v`)을 생성한다. 입력은 `pose_data`(TASK-001), `impact_frames`(TASK-003), `swing_feedbacks`(TASK-006) 계약이다. 합성 입력으로 독립 테스트가 가능하다.

## 2. Definitions & References
- **POSE_CONNECTIONS**: MediaPipe 33-point 뼈대 연결쌍 35개를 모듈 상수로 직접 정의(외부 의존성 제거).
- **가시성 임계값**: `visibility > 0.5`.
- **피드백 계약**: `swing_feedbacks[frame]=[{"text": str, "target_joint": int}]` (TASK-006).

## 3. Functional Requirements
- **FR-1 (출력 경로)**: 미지정 시 `{video}_analyzed{ext}`.
- **FR-2 (점)**: `vis>0.5` 관절에 반경 4 녹색(0,255,0) 원. 픽셀좌표 `x*width`, `y*height`.
- **FR-3 (선)**: 연결쌍 양끝 `vis>0.5`이면 흰색(255,255,255) 두께 2.
- **FR-4 (결측 스킵)**: 프레임 첫 관절 NaN이면 스켈레톤 생략.
- **FR-5 (임팩트 효과)**: `frame ∈ impact_frames`이면 두께 10 붉은(0,0,255) 테두리 + `IMPACT!`(scale 1.5, 두께 4).
- **FR-6 (오토 일시정지)**: 임팩트이고 `swing_feedbacks`에 있으면 정지 프레임 `int(fps*2.5)`회 반복 기록.
- **FR-7 (반투명)**: 정지 프레임에 검정 사각형 `addWeighted(0.5, 0.5)`.
- **FR-8 (툴팁)**: 각 피드백 `i`에 대해 타겟 관절 유효 시 청록(0,255,255) 지시선/원(반경 8 채움, 15 테두리), 박스 `box_x=clamp(tx+50,50,width-600)`, `box_y=clamp(ty-50+i*100,100,height-100)`(간격 100px), `font_scale=1.6`, `thickness=3`, 검정 배경 + 청록 테두리/텍스트.
- **FR-9 (writer)**: `VideoWriter_fourcc(*'mp4v')`, 원본 fps/해상도.

## 4. Interfaces & Data Structures
```python
def render_overlay(video_path, pose_data, impact_frames=None,
                   swing_feedbacks=None, output_path=None) -> str | None
```
- 반환: 저장 경로, 실패 시 `None`. H.264(libx264) 변환은 app 계층(TASK-008)이 moviepy로 수행.

## 5. UI/UX Requirements
영상 합성 요소의 시각 스펙은 §3에 정의. 웹 배치는 TASK-008.

## 6. Non-Functional Requirements
- 의존성: `opencv-python`, `numpy`.

## 7. Error Handling & Edge Cases
- EH-1 영상 부재/열기 실패 → `None`. EH-2 `pose_data` 길이 부족 → 초과 프레임 스켈레톤 생략. EH-3 타겟 관절 NaN → 툴팁 스킵.

## 8. Acceptance Criteria
- [ ] `vis>0.5` 관절(녹색)·뼈대(흰색)가 그려진다.
- [ ] 임팩트에 붉은 테두리·`IMPACT!`가 출력된다.
- [ ] 임팩트에서 `fps*2.5`회 정지 프레임이 반복된다.
- [ ] 정지 프레임에 반투명(0.5) 오버레이가 적용된다.
- [ ] 툴팁이 청록 지시선/박스·`font_scale 1.6`·`thickness 3`·100px 간격으로 렌더링된다.
- [ ] 영상 부재 시 `None`을 반환한다.
- [ ] 합성 입력(pose_data/impact_frames/swing_feedbacks)만으로 테스트가 통과한다.

## 9. Testing Instructions
`tennis-vision-analyzer/AI_README.md`를 따른다. `tennis-vision-analyzer/`에서:
```bash
source .venv/bin/activate && python -m unittest discover tests/
```
