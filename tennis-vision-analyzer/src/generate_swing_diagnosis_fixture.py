import json
import numpy as np
from swing_diagnosis import build_swing_feedbacks

def generate_fixture():
    fixtures = []

    # 1. 정상 스윙 (Topspin, Arm 150, correct chain)
    impact_frames_1 = [30]
    swing_types_1 = ["Topspin"]
    arm_angles_1 = [150.0] * 60
    
    chain_velocities_1 = {
        "hip": np.zeros(60),
        "shoulder": np.zeros(60),
        "wrist": np.zeros(60)
    }
    # frame=30, window [0, 45]. Peak at 10, 20, 30
    chain_velocities_1["hip"][10] = 1.0
    chain_velocities_1["shoulder"][20] = 1.0
    chain_velocities_1["wrist"][30] = 1.0
    
    sf1, ap1 = build_swing_feedbacks(impact_frames_1, swing_types_1, arm_angles_1, chain_velocities_1, 30)
    fixtures.append({
        "name": "normal_swing",
        "impact_frames": impact_frames_1,
        "swing_types": swing_types_1,
        "arm_angles": arm_angles_1,
        "chain_velocities": {
            "hip": chain_velocities_1["hip"].tolist(),
            "shoulder": chain_velocities_1["shoulder"].tolist(),
            "wrist": chain_velocities_1["wrist"].tolist()
        },
        "fps": 30,
        "expected_swing_feedbacks": sf1,
        "expected_all_problems": ap1
    })

    # 2. 팔이 굽은 스윙, Flat 구질
    impact_frames_2 = [20]
    swing_types_2 = ["Flat"]
    arm_angles_2 = [100.0] * 60
    
    chain_velocities_2 = {
        "hip": np.zeros(60),
        "shoulder": np.zeros(60),
        "wrist": np.zeros(60)
    }
    # correct chain
    chain_velocities_2["hip"][5] = 1.0
    chain_velocities_2["shoulder"][10] = 1.0
    chain_velocities_2["wrist"][15] = 1.0
    
    sf2, ap2 = build_swing_feedbacks(impact_frames_2, swing_types_2, arm_angles_2, chain_velocities_2, 30)
    fixtures.append({
        "name": "bent_flat_swing",
        "impact_frames": impact_frames_2,
        "swing_types": swing_types_2,
        "arm_angles": arm_angles_2,
        "chain_velocities": {
            "hip": chain_velocities_2["hip"].tolist(),
            "shoulder": chain_velocities_2["shoulder"].tolist(),
            "wrist": chain_velocities_2["wrist"].tolist()
        },
        "fps": 30,
        "expected_swing_feedbacks": sf2,
        "expected_all_problems": ap2
    })

    # 3. 체인 순서 불량 (hip >= shoulder)
    impact_frames_3 = [40]
    swing_types_3 = ["Topspin"]
    arm_angles_3 = [160.0] * 60
    
    chain_velocities_3 = {
        "hip": np.zeros(60),
        "shoulder": np.zeros(60),
        "wrist": np.zeros(60)
    }
    # frame=40. window=[10, 55]. Peak at 30, 20, 35 => hip(30) >= shoulder(20)
    chain_velocities_3["hip"][30] = 1.0
    chain_velocities_3["shoulder"][20] = 1.0
    chain_velocities_3["wrist"][35] = 1.0
    
    sf3, ap3 = build_swing_feedbacks(impact_frames_3, swing_types_3, arm_angles_3, chain_velocities_3, 30)
    fixtures.append({
        "name": "bad_chain_hip",
        "impact_frames": impact_frames_3,
        "swing_types": swing_types_3,
        "arm_angles": arm_angles_3,
        "chain_velocities": {
            "hip": chain_velocities_3["hip"].tolist(),
            "shoulder": chain_velocities_3["shoulder"].tolist(),
            "wrist": chain_velocities_3["wrist"].tolist()
        },
        "fps": 30,
        "expected_swing_feedbacks": sf3,
        "expected_all_problems": ap3
    })
    
    # 4. 체인 순서 불량 (shoulder >= wrist)
    impact_frames_4 = [40]
    swing_types_4 = ["Slice"]
    arm_angles_4 = [160.0] * 60
    
    chain_velocities_4 = {
        "hip": np.zeros(60),
        "shoulder": np.zeros(60),
        "wrist": np.zeros(60)
    }
    # frame=40. window=[10, 55]. Peak at 15, 30, 25 => shoulder(30) >= wrist(25)
    chain_velocities_4["hip"][15] = 1.0
    chain_velocities_4["shoulder"][30] = 1.0
    chain_velocities_4["wrist"][25] = 1.0
    
    sf4, ap4 = build_swing_feedbacks(impact_frames_4, swing_types_4, arm_angles_4, chain_velocities_4, 30)
    fixtures.append({
        "name": "bad_chain_shoulder",
        "impact_frames": impact_frames_4,
        "swing_types": swing_types_4,
        "arm_angles": arm_angles_4,
        "chain_velocities": {
            "hip": chain_velocities_4["hip"].tolist(),
            "shoulder": chain_velocities_4["shoulder"].tolist(),
            "wrist": chain_velocities_4["wrist"].tolist()
        },
        "fps": 30,
        "expected_swing_feedbacks": sf4,
        "expected_all_problems": ap4
    })
    
    # 5. Null chain velocities
    impact_frames_5 = [30]
    swing_types_5 = ["Topspin"]
    arm_angles_5 = [150.0] * 60
    
    sf5, ap5 = build_swing_feedbacks(impact_frames_5, swing_types_5, arm_angles_5, None, 30)
    fixtures.append({
        "name": "null_chain",
        "impact_frames": impact_frames_5,
        "swing_types": swing_types_5,
        "arm_angles": arm_angles_5,
        "chain_velocities": None,
        "fps": 30,
        "expected_swing_feedbacks": sf5,
        "expected_all_problems": ap5
    })

    with open('../TennisDocAI/core/vision/src/test/resources/golden_swing_diagnosis_fixture.json', 'w') as f:
        json.dump(fixtures, f, indent=2)

if __name__ == "__main__":
    generate_fixture()
    print("golden_swing_diagnosis_fixture.json generated")
