# TASK-001 QA Report — Pose Extraction (MediaPipe)

**Date:** 2026-07-31T06:47:48Z  
**Target:** `tennis-vision-analyzer/src/pose_extractor.py`  
**Spec:** `docs/specs/TASK-001-pose-extraction.md`  
**Result:** **PASS**

## Test Execution

```bash
cd tennis-vision-analyzer
source .venv/bin/activate && python -m unittest discover tests/
```

- Suite: 19 tests, 0 failures, 0 errors
- TASK-001 specific: `tests/test_pose_extractor.py` — 9 tests, all OK

## Acceptance Criteria

| # | Criterion | Result | Evidence |
|---|-----------|--------|----------|
| 1 | `(Frames, 33, 4)` 배열을 반환한다 | PASS | `test_returns_frames_33_4_shape` |
| 2 | 각 랜드마크가 `[x, y, z, visibility]`를 갖는다 | PASS | `test_landmark_xyz_visibility` |
| 3 | 다중 인물에서 주 인물 1명만 사용한다 | PASS | `test_uses_primary_person_only` (`pose_landmarks[0]`) |
| 4 | 미감지 프레임이 NaN으로 채워진다 | PASS | `test_undetected_frame_filled_with_nan` |
| 5 | `_pose.npy`가 저장된다 | PASS | `test_saves_pose_npy` |
| 6 | 모델/영상 부재 시 `None`을 반환한다 | PASS | `test_missing_model_returns_none`, `test_missing_video_returns_none` |

## Additional FR Coverage

| FR / EH | Result | Evidence |
|---------|--------|----------|
| FR-5 visibility 기본값 1.0 | PASS | `test_visibility_defaults_to_one_when_missing` |
| FR-4 / EH-2 타임스탬프 중복 +1 재시도 | PASS | `test_timestamp_retry_on_duplicate` |

## Notes

- 단위 테스트는 MediaPipe / OpenCV를 모킹하여 Acceptance Criteria를 검증함 (실영상 E2E는 범위 외).
- `process_video`의 모델/영상 실패 경로는 bare `return`(암시적 `None`)이며, AC의 `None` 반환과 동등함.
- Tester Agent handoff(`docs/turn.json` 갱신)는 사용자 요청에 따라 수행하지 않음.

## Verdict

**QA_PASSED** — 모든 Acceptance Criteria 충족.
