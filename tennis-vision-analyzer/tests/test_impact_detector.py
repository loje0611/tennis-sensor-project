import unittest
from unittest.mock import patch
import numpy as np
import sys
import os

# src 폴더를 패스에 추가
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from impact_detector import calculate_velocity, detect_impact_frame


def _make_pose(n_frames, joint_index, positions_xyz):
    """positions_xyz: list/array of (x,y,z) length n_frames."""
    pose = np.zeros((n_frames, 33, 4))
    for i, xyz in enumerate(positions_xyz):
        pose[i, joint_index, :3] = xyz
    return pose


def _constant_speed_then_spike(n_frames, joint_index, spike_frames, spike_step=0.5, base_step=0.01):
    """Build wrist trajectory with sharp position jumps at spike_frames."""
    positions = []
    x = 0.0
    spike_set = set(spike_frames)
    for i in range(n_frames):
        if i in spike_set:
            x += spike_step
        else:
            x += base_step
        positions.append([x, 0.0, 0.0])
    return _make_pose(n_frames, joint_index, positions)


class TestImpactDetector(unittest.TestCase):

    def test_calculate_velocity_length_and_first_zero(self):
        """AC: calculate_velocity returns same-length array with first value 0."""
        pose = np.zeros((10, 33, 4))
        for i in range(10):
            pose[i, 16, :3] = [i * 0.1, 0.0, 0.0]

        vels = calculate_velocity(pose, 16, fps=30)

        self.assertEqual(len(vels), 10)
        self.assertEqual(vels[0], 0.0)
        # distance 0.1 * fps 30 = 3.0
        self.assertAlmostEqual(vels[1], 3.0)
        self.assertAlmostEqual(vels[9], 3.0)

    def test_calculate_velocity_fewer_than_two_frames(self):
        """EH-1: frames < 2 → empty array."""
        pose = np.zeros((1, 33, 4))
        vels = calculate_velocity(pose, 16, fps=30)
        self.assertEqual(len(vels), 0)

        impact_frames, velocities = detect_impact_frame(pose, fps=30, hand='right')
        self.assertIsNone(impact_frames)
        self.assertEqual(len(velocities), 0)

    def test_hand_left_uses_index_15(self):
        """AC: hand='left' uses wrist index 15."""
        n = 10
        pose = np.zeros((n, 33, 4))
        # Only left wrist (15) moves with a spike at frame 5
        for i in range(n):
            if i < 5:
                pose[i, 15, :3] = [i * 0.1, 0.0, 0.0]
            else:
                pose[i, 15, :3] = [(i - 1) * 0.1 + 0.5 + (i - 5) * 0.1, 0.0, 0.0]
            # Right wrist stays still
            pose[i, 16, :3] = [0.0, 0.0, 0.0]

        impact_frames, _ = detect_impact_frame(pose, fps=30, hand='left')
        self.assertIsNotNone(impact_frames)
        self.assertEqual(impact_frames[0], 5)

        # Right hand on same pose should not see the left-wrist spike as motion
        impact_right, vels_right = detect_impact_frame(pose, fps=30, hand='right')
        self.assertEqual(impact_right, [0])  # EH-2 all-zero path
        self.assertTrue(np.allclose(vels_right, 0.0))

    def test_peak_params_height_prominence_distance(self):
        """AC: Height 50% / Prominence 30% / Distance 2.0s applied to find_peaks."""
        fps = 30
        n = 120
        pose = _constant_speed_then_spike(n, 16, spike_frames=[40], spike_step=1.0)

        captured = {}

        def fake_find_peaks(smooth, height=None, distance=None, prominence=None):
            captured['height'] = height
            captured['distance'] = distance
            captured['prominence'] = prominence
            max_vel = float(np.max(smooth))
            self.assertAlmostEqual(height, max_vel * 0.5, places=6)
            self.assertAlmostEqual(prominence, max_vel * 0.3, places=6)
            self.assertEqual(distance, int(fps * 2.0))
            return np.array([40]), {}

        with patch('impact_detector.find_peaks', side_effect=fake_find_peaks):
            impact_frames, _ = detect_impact_frame(pose, fps=fps, hand='right')

        self.assertEqual(impact_frames, [40])
        self.assertIn('height', captured)
        self.assertEqual(captured['distance'], 60)

    def test_multi_swing_separate_impacts(self):
        """AC: Multiple swings are separated as individual impacts."""
        fps = 30
        # distance = 60 frames; place spikes ~70 frames apart
        n = 200
        spike_a, spike_b = 40, 120
        pose = _constant_speed_then_spike(
            n, 16, spike_frames=[spike_a, spike_b], spike_step=1.0, base_step=0.005
        )

        impact_frames, _ = detect_impact_frame(pose, fps=fps, hand='right')

        self.assertIsNotNone(impact_frames)
        self.assertGreaterEqual(len(impact_frames), 2)
        # Peaks near the spike frames (gaussian smoothing may shift slightly)
        self.assertTrue(any(abs(f - spike_a) <= 5 for f in impact_frames))
        self.assertTrue(any(abs(f - spike_b) <= 5 for f in impact_frames))

    def test_no_peak_fallback_to_argmax(self):
        """AC / EH-3: No peaks → return single max-velocity frame."""
        # Monotonic ramp: after smoothing, find_peaks often finds nothing useful;
        # force empty peaks via mock to verify fallback path.
        n = 30
        pose = np.zeros((n, 33, 4))
        for i in range(n):
            pose[i, 16, :3] = [i * 0.05, 0.0, 0.0]

        with patch('impact_detector.find_peaks', return_value=(np.array([]), {})):
            impact_frames, vels = detect_impact_frame(pose, fps=30, hand='right')

        expected = int(np.argmax(np.nan_to_num(vels, nan=0.0)))
        self.assertEqual(impact_frames, [expected])
        self.assertEqual(len(impact_frames), 1)

    def test_all_zero_velocity_returns_frame_zero(self):
        """EH-2: all-zero velocity → [0]."""
        pose = np.zeros((20, 33, 4))
        impact_frames, vels = detect_impact_frame(pose, fps=30, hand='right')
        self.assertEqual(impact_frames, [0])
        self.assertTrue(np.allclose(vels, 0.0))

    def test_nan_in_velocity_path(self):
        """FR-2: NaN joint coords are handled (nan_to_num) without crash."""
        n = 40
        pose = np.full((n, 33, 4), np.nan)
        for i in range(n):
            pose[i, 16, :3] = [i * 0.1, 0.0, 0.0]
        # Inject NaN at one frame
        pose[10, 16, :3] = [np.nan, np.nan, np.nan]

        impact_frames, vels = detect_impact_frame(pose, fps=30, hand='right')
        self.assertIsNotNone(impact_frames)
        self.assertIsInstance(impact_frames, list)
        self.assertEqual(len(vels), n)

    def test_detect_impact_single_spike(self):
        """Basic single-impact detection (legacy)."""
        pose = np.zeros((10, 33, 4))
        for i in range(10):
            if i < 5:
                pose[i, 16, :3] = [i * 0.1, 0.0, 0.0]
            else:
                pose[i, 16, :3] = [(i - 1) * 0.1 + 0.5 + (i - 5) * 0.1, 0.0, 0.0]

        impact_frames, vels = detect_impact_frame(pose, fps=30, hand='right')
        self.assertEqual(impact_frames[0], 5)
        self.assertEqual(len(vels), 10)

    def test_returns_raw_velocities_not_smoothed(self):
        """Interface: velocities are pre-smoothing raw speeds."""
        pose = np.zeros((10, 33, 4))
        for i in range(10):
            pose[i, 16, :3] = [i * 0.1, 0.0, 0.0]

        _, vels = detect_impact_frame(pose, fps=30, hand='right')
        expected = calculate_velocity(pose, 16, fps=30)
        np.testing.assert_array_almost_equal(vels, expected)


    def test_left_hand_detection(self):
        # 15번(왼쪽 손목) 관절이 4번 프레임에서 급격히 이동
        left_pose = np.zeros((10, 33, 4))
        for i in range(10):
            if i < 4:
                left_pose[i, 15, :3] = [i * 0.1, 0.0, 0.0]
            else:
                left_pose[i, 15, :3] = [(i-1) * 0.1 + 0.8 + (i-4)*0.1, 0.0, 0.0]
        
        impact_frames, vels = detect_impact_frame(left_pose, fps=30, hand='left')
        self.assertEqual(impact_frames[0], 4)

    def test_multi_swing_detection(self):
        # 100프레임 (fps=30) 내에서 두 개의 피크 생성 (프레임 20과 프레임 85, distance=60 초과)
        fps = 30
        frames = 100
        multi_pose = np.zeros((frames, 33, 4))
        
        # 프레임 20 및 85 근처에 이동 급증
        x_pos = 0.0
        for i in range(frames):
            if i == 20 or i == 85:
                x_pos += 1.0
            else:
                x_pos += 0.01
            multi_pose[i, 16, :3] = [x_pos, 0.0, 0.0]
            
        impact_frames, vels = detect_impact_frame(multi_pose, fps=fps, hand='right')
        self.assertIn(20, impact_frames)
        self.assertIn(85, impact_frames)

    def test_fallback_argmax(self):
        # 피크 조건(prominence/height)을 미충족시키는 완만한 변화 환경에서도 max_vel 프레임 반환
        flat_pose = np.zeros((10, 33, 4))
        for i in range(10):
            flat_pose[i, 16, :3] = [i * 0.01, 0.0, 0.0]
        flat_pose[5, 16, :3] = [4 * 0.01 + 0.05, 0.0, 0.0]  # 약한 돌출
        
        impact_frames, vels = detect_impact_frame(flat_pose, fps=30, hand='right')
        self.assertTrue(len(impact_frames) > 0)
        self.assertIn(5, impact_frames)

if __name__ == '__main__':
    unittest.main()

