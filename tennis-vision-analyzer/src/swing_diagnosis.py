import numpy as np

def build_swing_feedbacks(impact_frames, swing_types, arm_angles, chain_velocities, fps):
    """
    스윙별로 관절 각도, 구질, 운동체인 속도를 입력받아 진단 결과와 피드백 데이터를 생성합니다.
    """
    swing_feedbacks = {}
    all_problems = []
    
    for i, frame in enumerate(impact_frames):
        stype = swing_types[i]
        arm_angle = arm_angles[frame] if frame < len(arm_angles) else 0
        feedbacks = []
        
        if chain_velocities:
            start_f = max(0, int(frame - fps * 1.0))
            end_f = min(len(chain_velocities['hip']), int(frame + fps * 0.5))
            if start_f < end_f:
                peak_hip = start_f + int(np.argmax(chain_velocities['hip'][start_f:end_f]))
                peak_shoulder = start_f + int(np.argmax(chain_velocities['shoulder'][start_f:end_f]))
                peak_wrist = start_f + int(np.argmax(chain_velocities['wrist'][start_f:end_f]))
                
                if peak_hip >= peak_shoulder:
                    feedbacks.append({"text": "Use Hip First", "target_joint": 24})
                    all_problems.append("운동 체인(하체->상체 순서)")
                elif peak_shoulder >= peak_wrist:
                    feedbacks.append({"text": "Late Wrist", "target_joint": 16})
                    all_problems.append("팔/손목 가속")
                    
        if arm_angle < 120:
            feedbacks.append({"text": f"Arm Bent({arm_angle:.0f})", "target_joint": 14})
            all_problems.append("타점(팔 각도)")
            
        if stype in ['Flat', 'Slice']:
            feedbacks.append({"text": "Low Path", "target_joint": 16})
            all_problems.append("상향 스윙 궤적")
            
        if not feedbacks:
            feedbacks.append({"text": "Good Swing!", "target_joint": 12})
            
        swing_feedbacks[frame] = feedbacks
        
    return swing_feedbacks, all_problems
