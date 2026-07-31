"""
TASK-008: App orchestration & UI — unit tests for aggregation/mapping logic
plus static Acceptance Criteria review of app.py (Streamlit UI is not E2E-run).
"""
import ast
import os
import re
import unittest
from collections import Counter
from unittest.mock import MagicMock, patch, call

APP_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'app.py'))


def _read_app_source():
    with open(APP_PATH, encoding='utf-8') as f:
        return f.read()


# §4.2 diagnosis text templates (exact strings from spec)
DIAGNOSIS_TEXTS = {
    "Use Hip First": (
        "**운동 체인 붕괴 (Use Hip First)**: 하체보다 상체(어깨)가 먼저 또는 동시에 회전하고 있습니다. "
        "하체 회전 후 상체가 따라오는 꼬임(Separation)을 만들어야 합니다."
    ),
    "Late Wrist": (
        "**손목 릴리스 지연 (Late Wrist)**: 팔(손목)의 가속이 어깨 회전과 분리되지 않았습니다. "
        "임팩트 직전 라켓 헤드를 던지듯 뿌려주세요."
    ),
    "Low Path": (
        "**스윙 궤적 (Low Path)**: 상향 스윙(Low-to-High) 궤적이 부족하여 네트에 걸리거나 아웃될 위험이 큽니다. "
        "라켓을 더 아래로 떨어뜨렸다가(Drop) 올려치세요."
    ),
}

ARM_BENT_TEMPLATE = (
    "**타점 오류 (Arm Bent)**: 타격 시 팔이 너무 구부러져 있습니다 (각도 {arm_angle:.1f}도). "
    "타점이 몸에 너무 가깝거나 타이밍이 늦습니다. 타점을 앞에서 잡으세요."
)

PRESCRIPTION_KEYS = {
    "운동 체인(하체->상체 순서)": "메디신 볼",
    "팔/손목 가속": "Whip",
    "타점(팔 각도)": "스텝",
    "상향 스윙 궤적": "Drop",
}


def map_feedback_to_diagnosis(fb_text):
    """Mirrors app.py diagnosis mapping (pure logic under test)."""
    if fb_text == "Use Hip First":
        return DIAGNOSIS_TEXTS["Use Hip First"]
    if fb_text == "Late Wrist":
        return DIAGNOSIS_TEXTS["Late Wrist"]
    if fb_text.startswith("Arm Bent"):
        arm_angle = float(fb_text.split("(")[1].split(")")[0])
        return ARM_BENT_TEMPLATE.format(arm_angle=arm_angle)
    if fb_text == "Low Path":
        return DIAGNOSIS_TEXTS["Low Path"]
    return None


def compute_final_evaluation(impact_frames, correct_chain_count, all_problems):
    """Mirrors app.py final evaluation aggregation."""
    if correct_chain_count == len(impact_frames) and len(all_problems) == 0:
        return {"perfect": True, "most_common": None}
    most_common = Counter(all_problems).most_common(1)[0][0] if all_problems else None
    return {
        "perfect": False,
        "correct_chain_count": correct_chain_count,
        "total": len(impact_frames),
        "most_common": most_common,
    }


def count_correct_chains(swing_feedbacks, impact_frames, chain_velocities):
    """Mirrors app.py correct_chain_count loop."""
    correct = 0
    for frame in impact_frames:
        feedbacks = swing_feedbacks[frame]
        has_chain_problem = any(
            fb["text"] in ("Use Hip First", "Late Wrist") for fb in feedbacks
        )
        if chain_velocities and not has_chain_problem:
            correct += 1
    return correct


class TestAppSourceReview(unittest.TestCase):
    """Static AC review of app.py source."""

    @classmethod
    def setUpClass(cls):
        cls.src = _read_app_source()
        cls.tree = ast.parse(cls.src)

    def test_pipeline_order_five_stages(self):
        """AC: upload → 5-stage pipeline executes in order."""
        # Call order in analysis block
        names = []
        for node in ast.walk(self.tree):
            if isinstance(node, ast.Call):
                func = node.func
                if isinstance(func, ast.Name):
                    names.append(func.id)
                elif isinstance(func, ast.Attribute):
                    names.append(func.attr)

        pipeline = [
            "process_video",
            "detect_impact_frame",
            "classify_swing_path",
            "get_joint_angles_from_pose",
            "analyze_kinetic_chain",
            "build_swing_feedbacks",
            "render_overlay",
        ]
        positions = []
        for name in pipeline:
            self.assertIn(name, names, f"Missing pipeline call: {name}")
            positions.append(self.src.find(name + "(") if name != "classify_swing_path"
                             else self.src.find("classify_swing_path("))

        # build_swing_feedbacks appears twice; use first occurrence for order vs render
        first_diag = self.src.find("build_swing_feedbacks(")
        render_pos = self.src.find("render_overlay(")
        self.assertLess(self.src.find("process_video("), self.src.find("detect_impact_frame("))
        self.assertLess(self.src.find("detect_impact_frame("), self.src.find("classify_swing_path("))
        self.assertLess(self.src.find("classify_swing_path("), self.src.find("get_joint_angles_from_pose("))
        self.assertLess(self.src.find("analyze_kinetic_chain("), first_diag)
        self.assertLess(first_diag, render_pos)

        # Explicit stage labels 1..5
        for i in range(1, 6):
            self.assertIn(f"{i}. ", self.src)

    def test_reuses_task006_diagnosis_module(self):
        """AC: diagnosis/feedback uses TASK-006 module (no duplicate builder)."""
        self.assertIn("from swing_diagnosis import build_swing_feedbacks", self.src)
        # Must not redefine build_swing_feedbacks locally
        defs = [n.name for n in self.tree.body if isinstance(n, ast.FunctionDef)]
        self.assertNotIn("build_swing_feedbacks", defs)

    def test_layout_left_right_and_65vh(self):
        """AC: 1:1 columns and video max-height 65vh."""
        self.assertIn('st.columns([1, 1])', self.src)
        self.assertIn("max-height: 65vh", self.src)
        self.assertIn("🎥 AI 스켈레톤 분석 영상", self.src)
        self.assertIn("🤖 AI 스윙 정밀 분석", self.src)
        self.assertIn('page_title="Tennis Vision Analyzer"', self.src)
        self.assertIn('layout="wide"', self.src)

    def test_diagnosis_texts_match_section_4_2(self):
        """AC: diagnosis texts match §4.2 and English tooltip tags."""
        for key, text in DIAGNOSIS_TEXTS.items():
            self.assertIn(text, self.src, f"Missing §4.2 text for {key}")
            self.assertIn(f'"{key}"', self.src)
        self.assertIn("**타점 오류 (Arm Bent)**", self.src)
        self.assertIn("각도 {arm_angle:.1f}도", self.src)

    def test_final_eval_and_prescriptions(self):
        """AC: final eval includes correct-chain count and most-common prescription."""
        self.assertIn("correct_chain_count", self.src)
        self.assertIn("Counter(all_problems).most_common(1)", self.src)
        self.assertIn("전체 스윙", self.src)
        self.assertIn("운동 체인이 올바르게 작동한 횟수", self.src)
        for problem_key, hint in PRESCRIPTION_KEYS.items():
            self.assertIn(problem_key, self.src)
            self.assertIn(hint, self.src)

    def test_mechanics_graphs_in_expander(self):
        """AC: bottom expander with angle & kinetic-chain graphs + impact vlines."""
        self.assertIn('st.expander("📊 상세 역학 그래프 보기 (참고사항)", expanded=False)', self.src)
        self.assertIn('xaxis_title="Time (Seconds)"', self.src)
        self.assertIn('yaxis_title="Angle (Degrees)"', self.src)
        self.assertIn('yaxis_title="Velocity (Relative)"', self.src)
        self.assertIn('line_dash="dash"', self.src)
        self.assertIn('annotation_text=f"Impact {idx+1}"', self.src)
        self.assertIn('line_dash="dot"', self.src)
        self.assertIn("symbol='star'", self.src)
        self.assertIn("height=350", self.src)

    def test_error_handling_paths(self):
        """AC: process_video None, zero impacts, missing chain_data handled safely."""
        self.assertIn("pose_data is None", self.src)
        self.assertIn("st.error", self.src)
        self.assertIn("st.stop()", self.src)
        self.assertIn("len(impact_frames) == 0", self.src)
        self.assertIn(
            "유의미한 임팩트(스윙)를 감지하지 못했습니다. 전신이 잘 나오게 촬영된 영상인지 확인해 주세요.",
            self.src,
        )
        self.assertIn("데이터가 부족하여 시각화할 수 없습니다.", self.src)
        # H.264 fallback
        self.assertIn("_analyzed_h264.mp4", self.src)
        self.assertIn("libx264", self.src)
        self.assertIn("os.path.exists(h264_video_path)", self.src)
        self.assertIn("st.video(output_video_path)", self.src)

    def test_upload_trigger_and_idle_message(self):
        """FR-1/2 / UI: uploader, button, idle hint."""
        self.assertIn("file_uploader", self.src)
        self.assertIn("mp4", self.src)
        self.assertIn("mov", self.src)
        self.assertIn("NamedTemporaryFile", self.src)
        self.assertIn("AI 분석 시작하기", self.src)
        self.assertIn("st.spinner", self.src)
        self.assertIn(
            "👈 좌측 사이드바에서 테니스 스윙 영상(.mp4)을 업로드해 주세요.",
            self.src,
        )


class TestAggregationLogic(unittest.TestCase):
    """Unit tests for orchestration aggregation (synthetic inputs)."""

    def test_map_feedback_to_diagnosis_all_tags(self):
        self.assertEqual(
            map_feedback_to_diagnosis("Use Hip First"),
            DIAGNOSIS_TEXTS["Use Hip First"],
        )
        self.assertEqual(
            map_feedback_to_diagnosis("Late Wrist"),
            DIAGNOSIS_TEXTS["Late Wrist"],
        )
        self.assertEqual(
            map_feedback_to_diagnosis("Arm Bent(110)"),
            ARM_BENT_TEMPLATE.format(arm_angle=110.0),
        )
        self.assertEqual(
            map_feedback_to_diagnosis("Low Path"),
            DIAGNOSIS_TEXTS["Low Path"],
        )
        self.assertIsNone(map_feedback_to_diagnosis("Good Swing!"))

    def test_correct_chain_count(self):
        frames = [10, 40]
        feedbacks = {
            10: [{"text": "Good Swing!", "target_joint": 12}],
            40: [{"text": "Use Hip First", "target_joint": 24}],
        }
        vels = {"hip": [0], "shoulder": [0], "wrist": [0]}
        self.assertEqual(count_correct_chains(feedbacks, frames, vels), 1)
        self.assertEqual(count_correct_chains(feedbacks, frames, None), 0)

    def test_final_evaluation_perfect_and_most_common(self):
        perfect = compute_final_evaluation([10, 20], 2, [])
        self.assertTrue(perfect["perfect"])

        problems = [
            "상향 스윙 궤적",
            "타점(팔 각도)",
            "상향 스윙 궤적",
        ]
        result = compute_final_evaluation([10, 20, 30], 1, problems)
        self.assertFalse(result["perfect"])
        self.assertEqual(result["correct_chain_count"], 1)
        self.assertEqual(result["most_common"], "상향 스윙 궤적")

    def test_build_swing_feedbacks_is_callable_dependency(self):
        """Integration smoke: TASK-006 builder still importable for app orchestration."""
        import sys
        src_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src'))
        sys.path.insert(0, src_dir)
        from swing_diagnosis import build_swing_feedbacks

        n = 40
        import numpy as np
        arm = [150.0] * n
        vels = {
            "hip": np.zeros(n),
            "shoulder": np.zeros(n),
            "wrist": np.zeros(n),
        }
        vels["hip"][10] = 1
        vels["shoulder"][15] = 1
        vels["wrist"][20] = 1
        fb, problems = build_swing_feedbacks([20], ["Topspin"], arm, vels, 30)
        self.assertIn(20, fb)
        self.assertIsInstance(problems, list)


class TestFpsFallback(unittest.TestCase):
    def test_fps_default_pattern_in_source(self):
        """EH-2: FPS defaults to 30.0 before VideoCapture read."""
        src = _read_app_source()
        self.assertRegex(src, r"fps\s*=\s*30\.0")
        self.assertIn("CAP_PROP_FPS", src)


if __name__ == '__main__':
    unittest.main()
