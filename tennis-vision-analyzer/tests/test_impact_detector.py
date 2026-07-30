import unittest
import numpy as np
import sys
import os

# src 폴더를 패스에 추가
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from impact_detector import calculate_velocity, detect_impact_frame

class TestImpactDetector(unittest.TestCase):
    
    def setUp(self):
        # 10프레임, 33개 관절, 4개 피처(x, y, z, vis)
        self.dummy_pose = np.zeros((10, 33, 4))
        
    def test_calculate_velocity(self):
        # 16번(오른쪽 손목) 관절이 매 프레임 x축으로 0.1씩 동일하게 이동
        for i in range(10):
            self.dummy_pose[i, 16, :3] = [i * 0.1, 0.0, 0.0]
            
        vels = calculate_velocity(self.dummy_pose, 16, fps=30)
        
        # 첫 프레임은 비교 대상이 없으므로 속도 0
        self.assertEqual(vels[0], 0.0)
        
        # 거리 차이(0.1) * fps(30) = 3.0
        self.assertAlmostEqual(vels[1], 3.0)
        self.assertAlmostEqual(vels[9], 3.0)
        self.assertEqual(len(vels), 10)
        
    def test_detect_impact_frame(self):
        # 5번 프레임에서 급격한 이동 발생 (거리가 0.5 뜀)
        for i in range(10):
            if i < 5:
                self.dummy_pose[i, 16, :3] = [i * 0.1, 0.0, 0.0]
            else:
                self.dummy_pose[i, 16, :3] = [(i-1) * 0.1 + 0.5 + (i-5)*0.1, 0.0, 0.0]
                
        impact_frames, vels = detect_impact_frame(self.dummy_pose, fps=30, hand='right')
        
        # 5번 프레임에서 속도 피크가 발생해야 함
        self.assertEqual(impact_frames[0], 5)

if __name__ == '__main__':
    unittest.main()
