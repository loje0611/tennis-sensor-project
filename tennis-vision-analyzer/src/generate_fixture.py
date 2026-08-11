import json
import numpy as np
from angle_calculator import calculate_3d_angle, get_joint_angles_from_pose

def generate_fixture():
    fixtures = []

    # 1. 일직선 (180도)
    fixtures.append({
        "name": "straight_line",
        "a": [0.0, 1.0, 0.0],
        "b": [0.0, 0.0, 0.0],
        "c": [0.0, -1.0, 0.0],
        "expected": calculate_3d_angle([0.0, 1.0, 0.0], [0.0, 0.0, 0.0], [0.0, -1.0, 0.0])
    })

    # 2. 직각 (90도)
    fixtures.append({
        "name": "right_angle",
        "a": [1.0, 0.0, 0.0],
        "b": [0.0, 0.0, 0.0],
        "c": [0.0, 1.0, 0.0],
        "expected": calculate_3d_angle([1.0, 0.0, 0.0], [0.0, 0.0, 0.0], [0.0, 1.0, 0.0])
    })

    # 3. 임의의 3D 관절 좌표
    fixtures.append({
        "name": "random_3d",
        "a": [1.5, 2.5, -0.5],
        "b": [0.1, 0.2, 0.3],
        "c": [-1.0, 3.0, 1.5],
        "expected": calculate_3d_angle([1.5, 2.5, -0.5], [0.1, 0.2, 0.3], [-1.0, 3.0, 1.5])
    })

    # 4. NaN 결측치
    fixtures.append({
        "name": "with_nan",
        "a": [1.0, np.nan, 0.0],
        "b": [0.0, 0.0, 0.0],
        "c": [0.0, 1.0, 0.0],
        "expected": calculate_3d_angle([1.0, np.nan, 0.0], [0.0, 0.0, 0.0], [0.0, 1.0, 0.0])
    })

    # 5. 0 길이 벡터
    fixtures.append({
        "name": "zero_length_vector",
        "a": [0.0, 0.0, 0.0],
        "b": [0.0, 0.0, 0.0],
        "c": [0.0, 1.0, 0.0],
        "expected": calculate_3d_angle([0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [0.0, 1.0, 0.0])
    })
    
    # 6. PoseFrame 추출 (33개 이상)
    pose = np.zeros((33, 4))
    # 어깨, 팔꿈치, 손목 (오른팔) - 직각
    pose[12] = [1.0, 0.0, 0.0, 1.0]
    pose[14] = [0.0, 0.0, 0.0, 1.0]
    pose[16] = [0.0, 1.0, 0.0, 1.0]
    
    # 골반, 무릎, 발목 (오른무릎) - 일직선
    pose[24] = [0.0, 1.0, 0.0, 1.0]
    pose[26] = [0.0, 0.0, 0.0, 1.0]
    pose[28] = [0.0, -1.0, 0.0, 1.0]

    angles = get_joint_angles_from_pose(pose)
    fixtures.append({
        "name": "pose_frame_valid",
        "pose": pose.tolist(),
        "expected_right_arm": angles["right_arm_angle"],
        "expected_right_knee": angles["right_knee_angle"]
    })

    # NaN 처리를 위해 json 출력 시 문자열로 처리하거나 그냥 NaN으로 남김 (JSON은 NaN 미지원)
    # 파이썬의 math.isnan 처리를 이용해 null로 변환
    def sanitize(val):
        if isinstance(val, float) and np.isnan(val):
            return "NaN"
        return val

    sanitized = []
    for f in fixtures:
        sf = {}
        for k, v in f.items():
            if isinstance(v, list) and len(v)>0 and isinstance(v[0], list):
                sf[k] = [[sanitize(x) for x in p] for p in v]
            elif isinstance(v, list):
                sf[k] = [sanitize(x) for x in v]
            else:
                sf[k] = sanitize(v)
        sanitized.append(sf)

    with open('../TennisDocAI/core/vision/src/test/resources/golden_angles_fixture.json', 'w') as f:
        json.dump(sanitized, f, indent=2)

if __name__ == "__main__":
    generate_fixture()
    print("golden_angles_fixture.json generated")
