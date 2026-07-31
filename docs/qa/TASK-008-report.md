# TASK-008 QA Report — App Orchestration, Result UI & Mechanics Graphs

**Date:** 2026-07-31T11:58:11Z  
**Target:** `tennis-vision-analyzer/app.py`  
**Spec:** `docs/specs/TASK-008-app-orchestration-ui.md`  
**Result:** **PASS**

## Test Execution

```bash
cd tennis-vision-analyzer
source venv/bin/activate && python -m unittest tests.test_app_orchestration -v
```

- Suite: 13 tests, 0 failures, 0 errors
- Note: Streamlit E2E는 범위 외. 스펙 §8에 따라 조율/집계는 단위 테스트, UI는 `app.py` 정적 AC 리뷰로 검증.
- Note: 실제 venv 경로는 `venv/` (AI_README의 `.venv`와 상이)

## Acceptance Criteria

| # | Criterion | Result | Evidence |
|---|-----------|--------|----------|
| 1 | 업로드 → 5단계 파이프라인이 순서대로 실행된다 | PASS | `test_pipeline_order_five_stages` |
| 2 | 진단/피드백은 TASK-006 모듈을 재사용한다 | PASS | `test_reuses_task006_diagnosis_module`, `test_build_swing_feedbacks_is_callable_dependency` |
| 3 | 결과가 좌(영상)/우(텍스트) 1:1, 세로 영상 65vh로 표시된다 | PASS | `test_layout_left_right_and_65vh` |
| 4 | 진단 텍스트가 §4.2 문구 및 영상 툴팁 영문과 1:1 매칭된다 | PASS | `test_diagnosis_texts_match_section_4_2`, `test_map_feedback_to_diagnosis_all_tags` |
| 5 | 최종 평가에 올바른 운동체인 횟수와 최빈 문제 처방이 포함된다 | PASS | `test_final_eval_and_prescriptions`, `test_final_evaluation_perfect_and_most_common`, `test_correct_chain_count` |
| 6 | 하단 Expander에 각도·운동체인 그래프가 임팩트 수직선과 함께 표시된다 | PASS | `test_mechanics_graphs_in_expander` |
| 7 | `process_video` None / 임팩트 0개 / `chain_data` 없음 안전 처리 | PASS | `test_error_handling_paths` |
| 8 | H.264 변환 실패 시 원본 영상으로 폴백한다 | PASS | `test_error_handling_paths` (`exists` 분기 + `st.video(output_video_path)`) |

## Additional Coverage

| Item | Result | Evidence |
|------|--------|----------|
| FR-1/2 업로더·버튼·스피너·대기 안내 | PASS | `test_upload_trigger_and_idle_message` |
| EH-2 FPS 기본 30.0 | PASS | `test_fps_default_pattern_in_source` |

## Notes

- Tester Agent handoff(`docs/turn.json` 갱신)는 사용자 요청에 따라 수행하지 않음.
- Board status는 요청에 따라 `QA_PASSED` 대신 `DONE`으로 설정.
- UI 런타임(브라우저 Streamlit) E2E는 수행하지 않음.

## Verdict

**DONE** — 모든 Acceptance Criteria 충족.
