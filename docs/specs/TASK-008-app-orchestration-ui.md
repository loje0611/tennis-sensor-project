# TASK-008 · App Orchestration, Result UI & Mechanics Graphs — SRS

**depends_on:** TASK-001, TASK-002, TASK-003, TASK-004, TASK-005, TASK-006, TASK-007

## 1. Overview & Scope
Streamlit 진입점(`app.py`)으로, 영상 업로드·임시 저장부터 전체 파이프라인 조율(TASK-001~007), H.264 변환, 결과 UI(좌우 1:1), 스윙별 진단 텍스트/최종 처방, 하단 역학 그래프까지를 통합한다. 통합 계층이므로 최상위 의존을 가지며, 검증은 단위 테스트 가능한 조율 로직 + Acceptance Criteria 리뷰로 수행한다.

## 2. Definitions & References
- **모듈 경로**: `src/`를 `sys.path`에 추가 후 각 모듈 import.
- **진단 소스**: 진단/피드백은 TASK-006(`swing_diagnosis`)의 반환을 사용(중복 구현 금지).

## 3. Functional Requirements
- **FR-1 (업로드/임시저장)**: 사이드바 `file_uploader(type=['mp4','mov'])` → `tempfile.NamedTemporaryFile(delete=False, suffix='.mp4')` 저장, 미리보기 표시.
- **FR-2 (트리거/스피너)**: `AI 분석 시작하기` 클릭 시에만 실행, `st.spinner`로 진행 표시.
- **FR-3 (FPS)**: `cv2.VideoCapture`로 FPS 취득, 실패 시 30.0.
- **FR-4 (파이프라인 조율)**: `process_video`(001) → `detect_impact_frame`(003) → 스윙별 `classify_swing_path`(004) → 프레임별 `get_joint_angles_from_pose`(002) + `analyze_kinetic_chain`(005) → `build_swing_feedbacks`(006) → `render_overlay`(007).
- **FR-5 (H.264 변환)**: `moviepy.VideoFileClip(...).write_videofile(codec="libx264", audio=False)`로 `{video}_analyzed_h264.mp4` 생성.
- **FR-6 (진단 텍스트/집계)**: TASK-006 결과로 스윙별 진단 텍스트를 출력하고 `correct_chain_count`, `all_problems`를 집계.
- **FR-7 (최종 평가/처방)**: 완벽 시 성공 메시지, 아니면 올바른 체인 횟수 표기 + `Counter(all_problems).most_common(1)` 최빈 문제 처방.

## 4. UI/UX Requirements
### 4.1 레이아웃
- `st.set_page_config(page_title="Tennis Vision Analyzer", layout="wide")`.
- 전역 CSS `video { max-height: 65vh; }` 주입.
- 타이틀 "🎾 테니스 비전 AI 스윙 분석기" + 설명. 사이드바 헤더 "1. 영상 업로드".
- 결과: `st.columns([1,1])` — 좌 "🎥 AI 스켈레톤 분석 영상"(h264 있으면 우선, 없으면 원본 폴백), 우 "🤖 AI 스윙 정밀 분석".
- 스윙별 헤더 `#### 🎾 스윙 {i+1} ({frame/fps:.2f}초)`, 구질 `- **구질 분석**: {stype}`, 문제 없으면 `st.info` 칭찬, 있으면 `st.warning`.
- 초기(미업로드) 안내: "👈 좌측 사이드바에서 테니스 스윙 영상(.mp4)을 업로드해 주세요."

### 4.2 진단/처방 문구 (정확히 이 텍스트)
- Use Hip First: `**운동 체인 붕괴 (Use Hip First)**: 하체보다 상체(어깨)가 먼저 또는 동시에 회전하고 있습니다. 하체 회전 후 상체가 따라오는 꼬임(Separation)을 만들어야 합니다.`
- Late Wrist: `**손목 릴리스 지연 (Late Wrist)**: 팔(손목)의 가속이 어깨 회전과 분리되지 않았습니다. 임팩트 직전 라켓 헤드를 던지듯 뿌려주세요.`
- Arm Bent: `**타점 오류 (Arm Bent)**: 타격 시 팔이 너무 구부러져 있습니다 (각도 {arm_angle:.1f}도). 타점이 몸에 너무 가깝거나 타이밍이 늦습니다. 타점을 앞에서 잡으세요.`
- Low Path: `**스윙 궤적 (Low Path)**: 상향 스윙(Low-to-High) 궤적이 부족하여 네트에 걸리거나 아웃될 위험이 큽니다. 라켓을 더 아래로 떨어뜨렸다가(Drop) 올려치세요.`
- 처방(최빈 문제별): `운동 체인(하체->상체 순서)`→메디신 볼/골반 먼저 빈스윙, `팔/손목 가속`→그립 힘빼고 Whip, `타점(팔 각도)`→스텝으로 거리 확보·앞발 앞 타격, `상향 스윙 궤적`→헤드 Drop 후 와이퍼 스윙.

### 4.3 상세 역학 그래프 (하단 Expander, `expanded=False`)
- `st.expander("📊 상세 역학 그래프 보기 (참고사항)")` 내부 `st.columns(2)`.
- 그래프1(각도): 팔 펴짐/무릎 굽힘 시계열, X `Time (Seconds)`(=frame/fps), Y `Angle (Degrees)`, 높이 350, 임팩트마다 `add_vline(dash="dash", color="red", annotation_text="Impact {i+1}")`.
- 그래프2(운동체인): 하체/몸통/팔 속도 3선 + peak star 마커, 임팩트마다 `add_vline(dash="dot", color="gray", opacity=0.5)`, Y `Velocity (Relative)`, 높이 350. `chain_data` 없으면 "데이터가 부족하여 시각화할 수 없습니다.".

> 사용자가 신규 UI/UX 요구사항을 전달하면 본 §4에 반영·갱신한다.

## 5. Non-Functional Requirements
- 의존성: `streamlit`, `opencv-python`, `plotly`, `numpy`, `moviepy`, 내부 `src/*`.
- 성능: 전체 분석 1~2분 목표, 진행 상태 표시.

## 6. Error Handling & Edge Cases
- EH-1 `process_video`가 `None` → `st.error` 후 `st.stop()`.
- EH-2 FPS 실패 → 30.0.
- EH-3 임팩트 0개 → 우측 경고: "유의미한 임팩트(스윙)를 감지하지 못했습니다. 전신이 잘 나오게 촬영된 영상인지 확인해 주세요.".
- EH-4 H.264 변환 실패 → `st.warning` 후 원본 렌더링 영상 폴백.
- EH-5 `chain_data is None` → 운동체인 그래프 대신 경고.

## 7. Acceptance Criteria
- [ ] 업로드 → 5단계 파이프라인이 순서대로 실행된다.
- [ ] 진단/피드백은 TASK-006 모듈을 재사용한다(중복 구현 없음).
- [ ] 결과가 좌(영상)/우(텍스트) 1:1, 세로 영상 65vh로 표시된다.
- [ ] 진단 텍스트가 §4.2 문구 및 영상 툴팁 영문과 1:1 매칭된다.
- [ ] 최종 평가에 올바른 운동체인 횟수와 최빈 문제 처방이 포함된다.
- [ ] 하단 Expander에 각도·운동체인 그래프가 임팩트 수직선과 함께 표시된다.
- [ ] `process_video`가 `None`이면 에러 후 중단, 임팩트 0개/`chain_data` 없음이 안전 처리된다.
- [ ] H.264 변환 실패 시 원본 영상으로 폴백한다.

## 8. Testing Instructions
`tennis-vision-analyzer/AI_README.md`를 따른다. 조율/집계 로직은 단위 테스트로, UI 표시는 Acceptance Criteria 리뷰로 검증한다. `tennis-vision-analyzer/`에서:
```bash
source .venv/bin/activate && python -m unittest discover tests/
```
