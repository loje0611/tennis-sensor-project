import unittest
from unittest.mock import patch
import numpy as np
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from kinetic_chain import analyze_kinetic_chain
from impact_detector import calculate_velocity


def _spike_at(pose, frame, joint_index, step=0.5):
    """Create a position jump into `frame` so velocity peaks at that frame."""
    # Keep joint at origin until frame-1, then jump at frame
    if frame <= 0:
        pose[0, joint_index, :3] = [step, 0.0, 0.0]
        return
    pose[frame - 1, joint_index, :3] = [0.0, 0.0, 0.0]
    pose[frame, joint_index, :3] = [step, 0.0, 0.0]
    # Hold after spike so later frames don't create larger velocities
    for i in range(frame + 1, len(pose)):
        pose[i, joint_index, :3] = [step, 0.0, 0.0]


class TestKineticChainAnalysis(unittest.TestCase):

    def test_returns_expected_dict_keys(self):
        """AC: returns dict with peak_frames, timing_ms, is_correct_chain, velocities."""
        n = 30
        pose = np.zeros((n, 33, 4))
        _spike_at(pose, 10, 24)
        _spike_at(pose, 15, 12)
        _spike_at(pose, 20, 16)

        result = analyze_kinetic_chain(pose, fps=30, hand='right')

        self.assertIsInstance(result, dict)
        self.assertIn("peak_frames", result)
        self.assertIn("timing_ms", result)
        self.assertIn("is_correct_chain", result)
        self.assertIn("velocities", result)
        for key in ("hip", "shoulder", "wrist"):
            self.assertIn(key, result["peak_frames"])
            self.assertIn(key, result["velocities"])
            self.assertEqual(len(result["velocities"][key]), n)
        self.assertIn("hip_to_shoulder", result["timing_ms"])
        self.assertIn("shoulder_to_wrist", result["timing_ms"])

    def test_correct_chain_order(self):
        """AC: is_correct_chain True when hip ≤ shoulder ≤ wrist."""
        pose = np.zeros((30, 33, 4))
        _spike_at(pose, 10, 24)
        _spike_at(pose, 15, 12)
        _spike_at(pose, 20, 16)

        result = analyze_kinetic_chain(pose, fps=30)
        self.assertEqual(result["peak_frames"]["hip"], 10)
        self.assertEqual(result["peak_frames"]["shoulder"], 15)
        self.assertEqual(result["peak_frames"]["wrist"], 20)
        self.assertTrue(result["is_correct_chain"])

        ms = 1000.0 / 30
        self.assertAlmostEqual(result["timing_ms"]["hip_to_shoulder"], 5 * ms)
        self.assertAlmostEqual(result["timing_ms"]["shoulder_to_wrist"], 5 * ms)

    def test_incorrect_chain_order(self):
        """AC: is_correct_chain False when order is wrong."""
        pose = np.zeros((30, 33, 4))
        # Wrist first, then shoulder, hip last
        _spike_at(pose, 8, 16)
        _spike_at(pose, 14, 12)
        _spike_at(pose, 22, 24)

        result = analyze_kinetic_chain(pose, fps=30)
        self.assertFalse(result["is_correct_chain"])
        self.assertGreater(result["peak_frames"]["hip"], result["peak_frames"]["shoulder"])

    def test_equal_peaks_still_correct(self):
        """FR-4: equal peaks (≤) still count as correct chain."""
        pose = np.zeros((20, 33, 4))
        # Same peak frame for all three
        _spike_at(pose, 10, 24, step=0.5)
        _spike_at(pose, 10, 12, step=0.5)
        _spike_at(pose, 10, 16, step=0.5)

        result = analyze_kinetic_chain(pose, fps=30)
        self.assertTrue(result["is_correct_chain"])
        self.assertEqual(result["timing_ms"]["hip_to_shoulder"], 0.0)
        self.assertEqual(result["timing_ms"]["shoulder_to_wrist"], 0.0)

    def test_reuses_calculate_velocity(self):
        """AC: reuses calculate_velocity from TASK-003."""
        pose = np.zeros((20, 33, 4))
        _spike_at(pose, 5, 24)
        _spike_at(pose, 8, 12)
        _spike_at(pose, 12, 16)

        with patch('kinetic_chain.calculate_velocity', wraps=calculate_velocity) as mocked:
            result = analyze_kinetic_chain(pose, fps=30, hand='right')
            self.assertIsNotNone(result)
            self.assertEqual(mocked.call_count, 3)
            # Right-hand indices: hip 24, shoulder 12, wrist 16
            called_joints = [c.args[1] for c in mocked.call_args_list]
            self.assertEqual(called_joints, [24, 12, 16])

    def test_hand_left_uses_left_indices(self):
        """Left hand: hip 23, shoulder 11, wrist 15."""
        pose = np.zeros((30, 33, 4))
        _spike_at(pose, 10, 23)
        _spike_at(pose, 15, 11)
        _spike_at(pose, 20, 15)
        # Right-side joints stay still — should not affect left analysis
        result = analyze_kinetic_chain(pose, fps=30, hand='left')

        self.assertEqual(result["peak_frames"]["hip"], 10)
        self.assertEqual(result["peak_frames"]["shoulder"], 15)
        self.assertEqual(result["peak_frames"]["wrist"], 20)
        self.assertTrue(result["is_correct_chain"])

        with patch('kinetic_chain.calculate_velocity', wraps=calculate_velocity) as mocked:
            analyze_kinetic_chain(pose, fps=30, hand='left')
            called_joints = [c.args[1] for c in mocked.call_args_list]
            self.assertEqual(called_joints, [23, 11, 15])

    def test_returns_none_when_velocity_empty(self):
        """AC / EH-1: empty velocity → None."""
        pose = np.zeros((1, 33, 4))
        self.assertIsNone(analyze_kinetic_chain(pose, fps=30))

        pose0 = np.zeros((0, 33, 4))
        self.assertIsNone(analyze_kinetic_chain(pose0, fps=30))

    def test_nan_velocity_replaced_with_zero(self):
        """EH-2: NaN in joint coords does not break analysis."""
        pose = np.zeros((20, 33, 4))
        _spike_at(pose, 10, 24)
        _spike_at(pose, 12, 12)
        _spike_at(pose, 14, 16)
        pose[5, 24, :3] = [np.nan, np.nan, np.nan]

        result = analyze_kinetic_chain(pose, fps=30)
        self.assertIsNotNone(result)
        self.assertFalse(np.any(np.isnan(result["velocities"]["hip"])))
        self.assertFalse(np.any(np.isnan(result["velocities"]["shoulder"])))
        self.assertFalse(np.any(np.isnan(result["velocities"]["wrist"])))


if __name__ == '__main__':
    unittest.main()
