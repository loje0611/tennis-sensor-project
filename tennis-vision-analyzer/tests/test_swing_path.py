import unittest
import numpy as np
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from swing_path import classify_swing_path, get_swing_trajectory_3d


def _pose_with_wrist_y(n_frames, joint_index, y_values):
    pose = np.zeros((n_frames, 33, 4))
    for i, y in enumerate(y_values):
        pose[i, joint_index, 1] = y
    return pose


class TestSwingPathClassification(unittest.TestCase):

    def test_topspin_when_y_decreases(self):
        """AC: Y decrease (upward) → Topspin; slope < -0.005."""
        n = 30
        # Δy/frame = -0.02 → slope clearly below -0.005
        y = [0.8 - i * 0.02 for i in range(n)]
        pose = _pose_with_wrist_y(n, 16, y)
        self.assertEqual(
            classify_swing_path(pose, impact_frame=15, hand='right', analysis_window=10),
            "Topspin",
        )

    def test_slice_when_y_increases(self):
        """AC: Y increase (downward) → Slice; slope > 0.005."""
        n = 30
        y = [0.2 + i * 0.02 for i in range(n)]
        pose = _pose_with_wrist_y(n, 16, y)
        self.assertEqual(
            classify_swing_path(pose, impact_frame=15, hand='right', analysis_window=10),
            "Slice",
        )

    def test_flat_when_slope_within_threshold(self):
        """AC: |slope| ≤ 0.005 → Flat."""
        n = 30
        # Δy/frame = 0.001 → within ±0.005
        y = [0.5 + i * 0.001 for i in range(n)]
        pose = _pose_with_wrist_y(n, 16, y)
        self.assertEqual(
            classify_swing_path(pose, impact_frame=15, hand='right', analysis_window=10),
            "Flat",
        )

    def test_threshold_boundaries(self):
        """AC: slope<-0.005=Topspin, >0.005=Slice, between=Flat."""
        n = 21
        impact = 10
        window = 10

        # Exactly constant → Flat
        pose_flat = _pose_with_wrist_y(n, 16, [0.5] * n)
        self.assertEqual(classify_swing_path(pose_flat, impact, analysis_window=window), "Flat")

        # Strong negative / positive already covered; near-threshold Flat
        y_near = [0.5 + i * 0.004 for i in range(n)]  # |slope|≈0.004 < 0.005
        pose_near = _pose_with_wrist_y(n, 16, y_near)
        self.assertEqual(classify_swing_path(pose_near, impact, analysis_window=window), "Flat")

        y_over = [0.5 + i * 0.006 for i in range(n)]  # |slope|≈0.006 > 0.005
        pose_over = _pose_with_wrist_y(n, 16, y_over)
        self.assertEqual(classify_swing_path(pose_over, impact, analysis_window=window), "Slice")

        y_under = [0.5 - i * 0.006 for i in range(n)]
        pose_under = _pose_with_wrist_y(n, 16, y_under)
        self.assertEqual(classify_swing_path(pose_under, impact, analysis_window=window), "Topspin")

    def test_hand_left_uses_index_15(self):
        """AC: hand='left' uses wrist index 15."""
        n = 30
        # Left wrist rising (Topspin), right wrist falling (would be Slice)
        pose = np.zeros((n, 33, 4))
        for i in range(n):
            pose[i, 15, 1] = 0.8 - i * 0.02
            pose[i, 16, 1] = 0.2 + i * 0.02

        self.assertEqual(
            classify_swing_path(pose, impact_frame=15, hand='left', analysis_window=10),
            "Topspin",
        )
        self.assertEqual(
            classify_swing_path(pose, impact_frame=15, hand='right', analysis_window=10),
            "Slice",
        )

    def test_unknown_when_impact_none_or_empty(self):
        """AC / EH-1: impact None or empty pose → Unknown."""
        pose = np.zeros((10, 33, 4))
        self.assertEqual(classify_swing_path(pose, impact_frame=None), "Unknown")
        self.assertEqual(classify_swing_path(np.zeros((0, 33, 4)), impact_frame=5), "Unknown")

    def test_unknown_when_insufficient_valid_samples(self):
        """AC / EH-2 / FR-2: valid samples < 2 → Unknown."""
        n = 20
        pose = np.full((n, 33, 4), np.nan)
        # Only one valid Y in the analysis window around impact=10
        pose[10, 16, 1] = 0.5
        self.assertEqual(
            classify_swing_path(pose, impact_frame=10, analysis_window=10),
            "Unknown",
        )

        # All NaN in window
        pose2 = np.full((n, 33, 4), np.nan)
        self.assertEqual(
            classify_swing_path(pose2, impact_frame=10, analysis_window=10),
            "Unknown",
        )

    def test_analysis_window_bounds(self):
        """FR-1: window clamped to [0, len)."""
        n = 15
        y = [0.8 - i * 0.03 for i in range(n)]
        pose = _pose_with_wrist_y(n, 16, y)
        # impact near start / end should not raise
        self.assertEqual(classify_swing_path(pose, impact_frame=0, analysis_window=10), "Topspin")
        self.assertEqual(classify_swing_path(pose, impact_frame=n - 1, analysis_window=10), "Topspin")

    def test_get_swing_trajectory_3d_filters_nan_x(self):
        """FR-5: returns rows where x is non-NaN."""
        pose = np.zeros((5, 33, 4))
        pose[0, 16, :3] = [0.1, 0.2, 0.3]
        pose[1, 16, :3] = [np.nan, 0.2, 0.3]
        pose[2, 16, :3] = [0.2, 0.3, 0.4]
        pose[3, 16, :3] = [np.nan, np.nan, np.nan]
        pose[4, 16, :3] = [0.3, 0.4, 0.5]

        traj = get_swing_trajectory_3d(pose, WRIST_IDX=16)
        self.assertEqual(traj.shape, (3, 3))
        np.testing.assert_array_almost_equal(traj[0], [0.1, 0.2, 0.3])
        np.testing.assert_array_almost_equal(traj[-1], [0.3, 0.4, 0.5])


if __name__ == '__main__':
    unittest.main()
