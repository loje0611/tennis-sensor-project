import json
import numpy as np
from impact_detector import calculate_velocity, detect_impact_frame

def generate_fixture():
    fixtures = []
    
    # 1. 60 프레임 단일 스윙 가속 (원래 테스트와 동일)
    dummy_pose1 = np.zeros((60, 33, 4))
    for i in range(60):
        if 25 <= i <= 35:
            dummy_pose1[i, 16, :3] = [0.01 * (i-25)**2, 0.5, 0.0]
        elif i > 35:
            dummy_pose1[i, 16, :3] = [0.01 * 100, 0.5, 0.0]
    
    impact1, vels1 = detect_impact_frame(dummy_pose1)
    fixtures.append({
        "name": "single_swing",
        "pose": dummy_pose1.tolist(),
        "expected_impact_frames": impact1,
        "expected_velocities": vels1.tolist()
    })

    # 2. 다중 스윙 감지 (2초(60프레임) 간격 이상의 두 번의 스윙)
    dummy_pose2 = np.zeros((150, 33, 4))
    for i in range(150):
        # 첫 번째 스윙 (20~30 프레임)
        if 20 <= i <= 30:
            dummy_pose2[i, 16, :3] = [0.01 * (i-20)**2, 0.0, 0.0]
        elif 30 < i <= 90:
            dummy_pose2[i, 16, :3] = [0.01 * 100, 0.0, 0.0]
        # 두 번째 스윙 (100~110 프레임) - 위치가 다시 움직임
        elif 100 <= i <= 110:
            dummy_pose2[i, 16, :3] = [0.01 * 100 + 0.01 * (i-100)**2, 0.0, 0.0]
        elif i > 110:
            dummy_pose2[i, 16, :3] = [0.01 * 100 + 0.01 * 100, 0.0, 0.0]

    impact2, vels2 = detect_impact_frame(dummy_pose2)
    fixtures.append({
        "name": "multi_swing",
        "pose": dummy_pose2.tolist(),
        "expected_impact_frames": impact2,
        "expected_velocities": vels2.tolist()
    })

    # 3. 빈 프레임 배열 (2프레임 미만)
    dummy_pose3 = np.zeros((1, 33, 4))
    impact3, vels3 = detect_impact_frame(dummy_pose3)
    fixtures.append({
        "name": "too_short",
        "pose": dummy_pose3.tolist(),
        "expected_impact_frames": impact3 if impact3 is not None else [],
        "expected_velocities": vels3.tolist()
    })

    # 4. 결측치(NaN)가 포함된 스윙
    dummy_pose4 = np.zeros((60, 33, 4))
    for i in range(60):
        if 25 <= i <= 35:
            dummy_pose4[i, 16, :3] = [0.01 * (i-25)**2, 0.5, 0.0]
        elif i > 35:
            dummy_pose4[i, 16, :3] = [0.01 * 100, 0.5, 0.0]
    
    # 30번 프레임 전체를 NaN으로 만듦
    dummy_pose4[30, 16, :3] = [np.nan, np.nan, np.nan]
    impact4, vels4 = detect_impact_frame(dummy_pose4)
    fixtures.append({
        "name": "with_nan",
        "pose": dummy_pose4.tolist(),
        "expected_impact_frames": impact4,
        "expected_velocities": vels4.tolist()
    })
    
    def sanitize(val):
        if isinstance(val, float) and np.isnan(val):
            return "NaN"
        return val

    sanitized = []
    for f in fixtures:
        sf = {}
        for k, v in f.items():
            if isinstance(v, list) and len(v)>0 and isinstance(v[0], list):
                if isinstance(v[0][0], list): # 3d list
                    sf[k] = [[[sanitize(x) for x in c] for c in p] for p in v]
                else:
                    sf[k] = [[sanitize(x) for x in p] for p in v]
            elif isinstance(v, list):
                sf[k] = [sanitize(x) for x in v]
            else:
                sf[k] = sanitize(v)
        sanitized.append(sf)

    with open('../TennisDocAI/core/vision/src/test/resources/golden_impact_fixture.json', 'w') as f:
        json.dump(sanitized, f, indent=2)

if __name__ == "__main__":
    generate_fixture()
    print("golden_impact_fixture.json generated")
