import unittest
import numpy as np
import sys
import os

# src 폴더를 패스에 추가
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from angle_calculator import calculate_3d_angle, get_joint_angles_from_pose

class TestAngleCalculator(unittest.TestCase):
    
    def test_straight_line(self):
        # 180도 일직선
        a = [1.0, 0.0, 0.0]
        b = [0.0, 0.0, 0.0]
        c = [-1.0, 0.0, 0.0]
        angle = calculate_3d_angle(a, b, c)
        self.assertAlmostEqual(angle, 180.0, places=2)
        
    def test_right_angle(self):
        # 90도 직각 (x축, y축)
        a = [1.0, 0.0, 0.0]
        b = [0.0, 0.0, 0.0]
        c = [0.0, 1.0, 0.0]
        angle = calculate_3d_angle(a, b, c)
        self.assertAlmostEqual(angle, 90.0, places=2)
        
    def test_acute_angle_3d(self):
        # 3D 공간상의 45도 예측 (xy 평면 대각선)
        a = [1.0, 0.0, 0.0]
        b = [0.0, 0.0, 0.0]
        c = [1.0, 1.0, 0.0]
        angle = calculate_3d_angle(a, b, c)
        self.assertAlmostEqual(angle, 45.0, places=2)

    def test_nan_handling(self):
        # MediaPipe가 관절을 놓쳐서 NaN 값이 들어왔을 때 안전하게 NaN을 반환하는지
        a = [np.nan, np.nan, np.nan]
        b = [0.0, 0.0, 0.0]
        c = [1.0, 1.0, 0.0]
        angle = calculate_3d_angle(a, b, c)
        self.assertTrue(np.isnan(angle))
        
    def test_zero_vector(self):
        # 점 a와 b가 완전히 동일하여 벡터의 크기가 0이 될 때
        a = [0.0, 0.0, 0.0]
        b = [0.0, 0.0, 0.0]
        c = [1.0, 1.0, 0.0]
        angle = calculate_3d_angle(a, b, c)
        self.assertTrue(np.isnan(angle))

    def test_get_joint_angles_from_pose(self):
        """AC: get_joint_angles_from_pose가 팔/무릎 각도 dict를 반환한다."""
        pose_frame = np.zeros((33, 4))
        # right arm 12-14-16: straight line → 180°
        pose_frame[12, :3] = [1.0, 0.0, 0.0]
        pose_frame[14, :3] = [0.0, 0.0, 0.0]
        pose_frame[16, :3] = [-1.0, 0.0, 0.0]
        # right knee 24-26-28: right angle → 90°
        pose_frame[24, :3] = [1.0, 0.0, 0.0]
        pose_frame[26, :3] = [0.0, 0.0, 0.0]
        pose_frame[28, :3] = [0.0, 1.0, 0.0]

        result = get_joint_angles_from_pose(pose_frame)

        self.assertEqual(set(result.keys()), {"right_arm_angle", "right_knee_angle"})
        self.assertAlmostEqual(result["right_arm_angle"], 180.0, places=2)
        self.assertAlmostEqual(result["right_knee_angle"], 90.0, places=2)

    def test_get_joint_angles_insufficient_joints(self):
        """EH-2: 관절 33개 미만이면 NaN dict."""
        pose_frame = np.zeros((10, 4))
        result = get_joint_angles_from_pose(pose_frame)
        self.assertEqual(set(result.keys()), {"right_arm_angle", "right_knee_angle"})
        self.assertTrue(np.isnan(result["right_arm_angle"]))
        self.assertTrue(np.isnan(result["right_knee_angle"]))

if __name__ == '__main__':
    unittest.main()
