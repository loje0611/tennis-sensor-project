# TASK-001 · Pose Extraction (MediaPipe) — SRS

**depends_on:** none

## 1. Overview & Scope
업로드된 스윙 영상의 각 프레임에서 MediaPipe Pose Landmarker(Vision API v2, VIDEO 모드)로 33개 3D 관절 랜드마크를 추출하여 `(Frames, 33, 4)` 배열을 생성하고 `.npy`로 저장한다. 이 배열(`pose_data`)은 이후 모든 분석 모듈의 **공통 입력 계약**이다.

## 2. Definitions & References
- **pose_data 계약**: `np.ndarray`, shape `(Frames, 33, 4)`, 마지막 축 `[x, y, z, visibility]`. 좌표는 0~1 정규화, MediaPipe 좌표계에서 y는 상단=0/하단=1.
- **모델 파일**: `models/pose_landmarker_full.task`.
- **주 인물**: 다중 인물 감지 시 `pose_landmarks[0]`만 사용.

## 3. Functional Requirements
- **FR-1 (모델 경로)**: 기본 `../models/pose_landmarker_full.task`, 없으면 `models/pose_landmarker_full.task` 재시도. 둘 다 없으면 에러 출력 후 `None`.
- **FR-2 (영상 로드)**: `cv2.VideoCapture`로 FPS·프레임수·해상도 획득. 실패 시 `None`.
- **FR-3 (프레임 처리)**: BGR→RGB → `mp.Image(SRGB)` → `detector.detect_for_video(mp_image, timestamp_ms)`.
- **FR-4 (타임스탬프 보정)**: `CAP_PROP_POS_MSEC==0`이고 2번째+ 프레임이면 `int((current_frame/fps)*1000)`. 중복 예외 시 `+1` 재시도.
- **FR-5 (수집)**: 33개 관절 `[x,y,z,visibility]` 수집, visibility 미제공 시 1.0.
- **FR-6 (결측)**: 미감지 프레임은 `[[NaN]*4]*33`.
- **FR-7 (저장/반환)**: `(Frames,33,4)` 배열을 `{video_basename}_pose.npy`로 저장 후 반환.

## 4. Interfaces & Data Structures
```python
def process_video(video_path: str,
                  model_path: str = "../models/pose_landmarker_full.task"
                  ) -> np.ndarray | None
```
- 부수효과: `_pose.npy` 저장. MediaPipe 옵션: `RunningMode.VIDEO`, `output_segmentation_masks=False`.
- CLI: `python pose_extractor.py <video_path>`.

## 5. UI/UX Requirements
N/A (backend module).

## 6. Non-Functional Requirements
- 의존성: `mediapipe`, `opencv-python`, `numpy`. 모델 파일 필수.

## 7. Error Handling & Edge Cases
- EH-1 모델/영상 부재 → `None`. EH-2 타임스탬프 0/중복 → FR-4 보정. EH-3 미감지 프레임 → NaN 33행.

## 8. Acceptance Criteria
- [ ] `(Frames, 33, 4)` 배열을 반환한다.
- [ ] 각 랜드마크가 `[x, y, z, visibility]`를 갖는다.
- [ ] 다중 인물에서 주 인물 1명만 사용한다.
- [ ] 미감지 프레임이 NaN으로 채워진다.
- [ ] `_pose.npy`가 저장된다.
- [ ] 모델/영상 부재 시 `None`을 반환한다.

## 9. Testing Instructions
`tennis-vision-analyzer/AI_README.md`를 따른다. `tennis-vision-analyzer/`에서:
```bash
source .venv/bin/activate && python -m unittest discover tests/
```
