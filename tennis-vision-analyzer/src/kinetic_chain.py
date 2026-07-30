import numpy as np
import sys
import os

# 현재 폴더(src)를 import 경로에 추가
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))
from impact_detector import calculate_velocity

def analyze_kinetic_chain(pose_data, fps=30, hand='right'):
    """
    운동 체인(Kinetic Chain) 역학을 분석하여 골반, 어깨, 손목의 
    최대 속도(피크) 발생 프레임과 그 순서를 반환합니다.
    
    정상적인 운동 체인: 골반(하체) -> 어깨(몸통) -> 손목(팔) 순으로 가속 피크가 발생.
    """
    HIP_IDX = 24 if hand == 'right' else 23
    SHOULDER_IDX = 12 if hand == 'right' else 11
    WRIST_IDX = 16 if hand == 'right' else 15
    
    # 1. 각 관절의 속도 계산
    vel_hip = calculate_velocity(pose_data, HIP_IDX, fps)
    vel_shoulder = calculate_velocity(pose_data, SHOULDER_IDX, fps)
    vel_wrist = calculate_velocity(pose_data, WRIST_IDX, fps)
    
    if len(vel_hip) == 0:
        return None
        
    # 결측치(NaN) 제거
    vel_hip = np.nan_to_num(vel_hip, nan=0.0)
    vel_shoulder = np.nan_to_num(vel_shoulder, nan=0.0)
    vel_wrist = np.nan_to_num(vel_wrist, nan=0.0)
    
    # 2. 피크 프레임 찾기 (최대 속도 발생 지점)
    peak_hip = int(np.argmax(vel_hip))
    peak_shoulder = int(np.argmax(vel_shoulder))
    peak_wrist = int(np.argmax(vel_wrist))
    
    # 3. 시간차(ms) 계산
    ms_per_frame = 1000.0 / fps
    hip_to_shoulder_ms = (peak_shoulder - peak_hip) * ms_per_frame
    shoulder_to_wrist_ms = (peak_wrist - peak_shoulder) * ms_per_frame
    
    # 4. 순서 검증 (골반 -> 어깨 -> 손목 순서로 피크가 오는지 확인)
    is_correct_chain = (peak_hip <= peak_shoulder) and (peak_shoulder <= peak_wrist)
    
    return {
        "peak_frames": {
            "hip": peak_hip,
            "shoulder": peak_shoulder,
            "wrist": peak_wrist
        },
        "timing_ms": {
            "hip_to_shoulder": hip_to_shoulder_ms,
            "shoulder_to_wrist": shoulder_to_wrist_ms
        },
        "is_correct_chain": is_correct_chain,
        "velocities": {
            "hip": vel_hip,
            "shoulder": vel_shoulder,
            "wrist": vel_wrist
        }
    }
