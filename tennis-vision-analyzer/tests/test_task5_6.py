import unittest
import numpy as np
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))
from kinetic_chain import analyze_kinetic_chain
from swing_path import classify_swing_path

class TestTasks(unittest.TestCase):
    
    def test_kinetic_chain_correct_order(self):
        # 30프레임, 33개 관절, 4개 피처
        dummy_pose = np.zeros((30, 33, 4))
        
        # 골반(24)은 10프레임, 어깨(12)는 15프레임, 손목(16)은 20프레임에서 최대 이동
        dummy_pose[9:11, 24, :3] = [[0,0,0], [0.5,0,0]]    # 10프레임에서 거리 0.5
        dummy_pose[14:16, 12, :3] = [[0,0,0], [0.6,0,0]]   # 15프레임에서 거리 0.6
        dummy_pose[19:21, 16, :3] = [[0,0,0], [0.8,0,0]]   # 20프레임에서 거리 0.8
        
        result = analyze_kinetic_chain(dummy_pose, fps=30)
        
        self.assertEqual(result["peak_frames"]["hip"], 10)
        self.assertEqual(result["peak_frames"]["shoulder"], 15)
        self.assertEqual(result["peak_frames"]["wrist"], 20)
        
        self.assertTrue(result["is_correct_chain"])
        self.assertAlmostEqual(result["timing_ms"]["hip_to_shoulder"], 5 * (1000/30))

    def test_classify_swing_path_topspin(self):
        # 20프레임 데이터
        dummy_pose = np.zeros((20, 33, 4))
        
        # Y값이 0.8에서 0.2로 점차 감소 -> 위로 올라가는 상향 스윙 (Topspin)
        for i in range(20):
            dummy_pose[i, 16, 1] = 0.8 - (i * 0.03) 
            
        swing_type = classify_swing_path(dummy_pose, impact_frame=10, analysis_window=5)
        self.assertEqual(swing_type, "Topspin")
        
    def test_classify_swing_path_slice(self):
        dummy_pose = np.zeros((20, 33, 4))
        
        # Y값이 0.2에서 0.8로 점차 증가 -> 아래로 내려가는 하향 스윙 (Slice)
        for i in range(20):
            dummy_pose[i, 16, 1] = 0.2 + (i * 0.03) 
            
        swing_type = classify_swing_path(dummy_pose, impact_frame=10, analysis_window=5)
        self.assertEqual(swing_type, "Slice")

if __name__ == '__main__':
    unittest.main()
