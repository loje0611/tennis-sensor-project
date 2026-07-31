import unittest
import numpy as np
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from swing_diagnosis import build_swing_feedbacks


def _velocities(n, hip_peak, shoulder_peak, wrist_peak):
    """Synthetic velocity arrays with a single peak at the given frames."""
    hip = np.zeros(n)
    shoulder = np.zeros(n)
    wrist = np.zeros(n)
    hip[hip_peak] = 1.0
    shoulder[shoulder_peak] = 1.0
    wrist[wrist_peak] = 1.0
    return {"hip": hip, "shoulder": shoulder, "wrist": wrist}


def _texts(feedbacks):
    return [fb["text"] for fb in feedbacks]


class TestSwingDiagnosis(unittest.TestCase):

    def test_local_window_peaks_drive_chain_diagnosis(self):
        """AC: local window (1.0s before ~ 0.5s after) used for peak detection."""
        fps = 30
        n = 90
        frame = 45
        # Peaks inside local window [15, 60): hip late vs shoulder → Use Hip First
        vels = _velocities(n, hip_peak=40, shoulder_peak=30, wrist_peak=50)
        arm = [150.0] * n
        feedbacks, problems = build_swing_feedbacks(
            [frame], ["Topspin"], arm, vels, fps
        )
        self.assertIn("Use Hip First", _texts(feedbacks[frame]))
        self.assertIn("운동 체인(하체->상체 순서)", problems)

        # Peaks outside window should not be used: global peaks wrong order,
        # but local window peaks correct → Good Swing! (with Topspin + arm ok)
        vels2 = {
            "hip": np.zeros(n),
            "shoulder": np.zeros(n),
            "wrist": np.zeros(n),
        }
        # Outside window (before start_f=15): wrong order spikes
        vels2["wrist"][5] = 10.0
        vels2["shoulder"][8] = 10.0
        vels2["hip"][10] = 10.0
        # Inside window: correct hip < shoulder < wrist
        vels2["hip"][25] = 1.0
        vels2["shoulder"][35] = 1.0
        vels2["wrist"][50] = 1.0

        feedbacks2, problems2 = build_swing_feedbacks(
            [frame], ["Topspin"], arm, vels2, fps
        )
        self.assertEqual(_texts(feedbacks2[frame]), ["Good Swing!"])
        self.assertEqual(problems2, [])

    def test_fr3_priority_five_tags(self):
        """AC: FR-3 generates Use Hip First / Late Wrist / Arm Bent / Low Path / Good Swing!."""
        fps = 30
        n = 60
        frame = 30
        arm_ok = [150.0] * n

        # 1) Use Hip First
        vels = _velocities(n, 35, 25, 40)
        fb, probs = build_swing_feedbacks([frame], ["Topspin"], arm_ok, vels, fps)
        self.assertEqual(fb[frame][0], {"text": "Use Hip First", "target_joint": 24})
        self.assertIn("운동 체인(하체->상체 순서)", probs)

        # 2) Late Wrist (hip < shoulder but shoulder >= wrist)
        vels = _velocities(n, 20, 35, 30)
        fb, probs = build_swing_feedbacks([frame], ["Topspin"], arm_ok, vels, fps)
        self.assertEqual(fb[frame][0], {"text": "Late Wrist", "target_joint": 16})
        self.assertIn("팔/손목 가속", probs)

        # 3) Arm Bent
        arm_bent = [150.0] * n
        arm_bent[frame] = 110.0
        vels = _velocities(n, 20, 25, 35)
        fb, probs = build_swing_feedbacks([frame], ["Topspin"], arm_bent, vels, fps)
        self.assertTrue(any(t.startswith("Arm Bent") for t in _texts(fb[frame])))
        self.assertIn("타점(팔 각도)", probs)

        # 4) Low Path for Flat / Slice
        vels = _velocities(n, 20, 25, 35)
        fb, probs = build_swing_feedbacks([frame], ["Flat"], arm_ok, vels, fps)
        self.assertIn("Low Path", _texts(fb[frame]))
        self.assertIn("상향 스윙 궤적", probs)

        fb, probs = build_swing_feedbacks([frame], ["Slice"], arm_ok, vels, fps)
        self.assertIn("Low Path", _texts(fb[frame]))

        # 5) Good Swing!
        fb, probs = build_swing_feedbacks([frame], ["Topspin"], arm_ok, vels, fps)
        self.assertEqual(fb[frame], [{"text": "Good Swing!", "target_joint": 12}])
        self.assertEqual(probs, [])

    def test_arm_bent_includes_angle(self):
        """AC: arm_angle < 120 → Arm Bent({angle:.0f})."""
        n = 40
        frame = 20
        arm = [150.0] * n
        arm[frame] = 110.5
        vels = _velocities(n, 10, 15, 25)
        fb, probs = build_swing_feedbacks([frame], ["Topspin"], arm, vels, 30)
        self.assertIn({"text": "Arm Bent(110)", "target_joint": 14}, fb[frame])
        self.assertIn("타점(팔 각도)", probs)

    def test_problem_tags_accumulated_for_aggregation(self):
        """AC: all_problems list accumulates tags across swings."""
        n = 80
        fps = 30
        frames = [20, 50]
        types = ["Flat", "Slice"]
        arm = [150.0] * n
        arm[20] = 100.0
        # Swing 1: hip late + arm bent + flat
        # Swing 2: correct chain + slice
        hip = np.zeros(n)
        shoulder = np.zeros(n)
        wrist = np.zeros(n)
        hip[25] = 1.0
        shoulder[15] = 1.0
        wrist[30] = 1.0
        # local for frame 50 [20, 65): correct
        hip[40] = 1.0
        shoulder[45] = 1.0
        wrist[55] = 1.0
        # For frame 20 window [0, 35): hip@25 > shoulder@15 → Use Hip First
        vels = {"hip": hip, "shoulder": shoulder, "wrist": wrist}

        _, problems = build_swing_feedbacks(frames, types, arm, vels, fps)
        self.assertIn("운동 체인(하체->상체 순서)", problems)
        self.assertIn("타점(팔 각도)", problems)
        self.assertEqual(problems.count("상향 스윙 궤적"), 2)

    def test_missing_chain_velocities_skips_chain_diagnosis(self):
        """AC / EH-1: no chain_velocities → angle/path only."""
        n = 30
        frame = 10
        arm = [150.0] * n
        arm[frame] = 100.0

        fb, problems = build_swing_feedbacks(
            [frame], ["Flat"], arm, chain_velocities=None, fps=30
        )
        texts = _texts(fb[frame])
        self.assertNotIn("Use Hip First", texts)
        self.assertNotIn("Late Wrist", texts)
        self.assertIn("Arm Bent(100)", texts)
        self.assertIn("Low Path", texts)
        self.assertNotIn("Good Swing!", texts)

        # Empty dict is also falsy → skip chain
        fb2, _ = build_swing_feedbacks([frame], ["Topspin"], [150.0] * n, {}, 30)
        self.assertEqual(_texts(fb2[frame]), ["Good Swing!"])

    def test_arm_angle_oob_defaults_to_zero(self):
        """EH-2: frame >= len(arm_angles) → arm_angle=0 → Arm Bent(0)."""
        n = 20
        frame = 15
        arm = [150.0] * 10  # shorter than frame
        vels = _velocities(n, 5, 8, 12)
        fb, problems = build_swing_feedbacks([frame], ["Topspin"], arm, vels, 30)
        self.assertIn({"text": "Arm Bent(0)", "target_joint": 14}, fb[frame])
        self.assertIn("타점(팔 각도)", problems)

    def test_start_ge_end_skips_chain(self):
        """EH-3: start_f >= end_f → skip chain diagnosis."""
        # Very short velocity arrays relative to impact frame + window
        frame = 5
        vels = {
            "hip": np.array([0.0]),
            "shoulder": np.array([0.0]),
            "wrist": np.array([0.0]),
        }
        # start_f = max(0, 5-30)=0, end_f = min(1, 5+15)=1 → start < end actually
        # Need start_f >= end_f: e.g. empty slice when len is 0... but then end_f=0, start_f=0
        vels_empty_len = {
            "hip": np.array([]),
            "shoulder": np.array([]),
            "wrist": np.array([]),
        }
        # end_f = min(0, ...) = 0, start_f = 0 → start_f >= end_f → skip
        fb, problems = build_swing_feedbacks(
            [frame], ["Topspin"], [150.0] * 20, vels_empty_len, 30
        )
        self.assertEqual(_texts(fb[frame]), ["Good Swing!"])
        self.assertEqual(problems, [])

    def test_use_hip_first_takes_priority_over_late_wrist(self):
        """FR-3: when peak_hip >= peak_shoulder, Late Wrist is not also emitted."""
        n = 60
        frame = 30
        # hip >= shoulder AND shoulder >= wrist — only Use Hip First for chain
        vels = _velocities(n, 40, 35, 30)
        arm = [150.0] * n
        fb, problems = build_swing_feedbacks([frame], ["Topspin"], arm, vels, 30)
        texts = _texts(fb[frame])
        self.assertIn("Use Hip First", texts)
        self.assertNotIn("Late Wrist", texts)
        self.assertEqual(problems, ["운동 체인(하체->상체 순서)"])


if __name__ == '__main__':
    unittest.main()
