import json
import numpy as np
from kinetic_chain import analyze_kinetic_chain

def generate_fixture():
    fixtures = []

    # 1. 정상 운동 체인 (hip -> shoulder -> wrist)
    dummy_normal = np.zeros((60, 33, 4))
    # Hip peak at 10
    dummy_normal[9:12, 24, 0] = [0.0, 1.0, 2.0] # 0 to 1 dist = 1, 1 to 2 dist = 1
    # Shoulder peak at 20
    dummy_normal[19:22, 12, 0] = [0.0, 1.5, 3.0]
    # Wrist peak at 30
    dummy_normal[29:32, 16, 0] = [0.0, 2.0, 4.0]
    
    # Need to simulate distance properly for calculate_velocity
    # vel[i] is distance between i-1 and i
    # To get peak at i, distance from i-1 to i must be max.
    
    def set_peak(arr, idx, joint, dist):
        # set positions so that diff between idx-1 and idx is dist
        arr[:idx, joint, 0] = 0.0
        arr[idx:, joint, 0] = dist
        # all other distances 0

    dummy_normal = np.zeros((60, 33, 4))
    set_peak(dummy_normal, 10, 24, 1.0)
    set_peak(dummy_normal, 20, 12, 2.0)
    set_peak(dummy_normal, 30, 16, 3.0)

    res_normal = analyze_kinetic_chain(dummy_normal)
    fixtures.append({
        "name": "normal_chain",
        "pose": dummy_normal.tolist(),
        "expected_peak_frames": res_normal["peak_frames"],
        "expected_timing_ms": res_normal["timing_ms"],
        "expected_is_correct_chain": res_normal["is_correct_chain"],
        "expected_velocities": res_normal["velocities"]
    })

    # 2. 순서 역전 체인 (wrist -> shoulder -> hip)
    dummy_reverse = np.zeros((60, 33, 4))
    set_peak(dummy_reverse, 10, 16, 3.0)
    set_peak(dummy_reverse, 20, 12, 2.0)
    set_peak(dummy_reverse, 30, 24, 1.0)
    
    res_reverse = analyze_kinetic_chain(dummy_reverse)
    fixtures.append({
        "name": "reverse_chain",
        "pose": dummy_reverse.tolist(),
        "expected_peak_frames": res_reverse["peak_frames"],
        "expected_timing_ms": res_reverse["timing_ms"],
        "expected_is_correct_chain": res_reverse["is_correct_chain"],
        "expected_velocities": res_reverse["velocities"]
    })

    # 3. 동시 발생 체인 (hip = shoulder = wrist)
    dummy_same = np.zeros((60, 33, 4))
    set_peak(dummy_same, 15, 24, 1.0)
    set_peak(dummy_same, 15, 12, 2.0)
    set_peak(dummy_same, 15, 16, 3.0)
    
    res_same = analyze_kinetic_chain(dummy_same)
    fixtures.append({
        "name": "simultaneous_chain",
        "pose": dummy_same.tolist(),
        "expected_peak_frames": res_same["peak_frames"],
        "expected_timing_ms": res_same["timing_ms"],
        "expected_is_correct_chain": res_same["is_correct_chain"],
        "expected_velocities": res_same["velocities"]
    })

    # 4. 결측치(NaN)
    dummy_nan = np.zeros((60, 33, 4))
    set_peak(dummy_nan, 10, 24, 1.0)
    set_peak(dummy_nan, 20, 12, 2.0)
    set_peak(dummy_nan, 30, 16, 3.0)
    dummy_nan[20, 12, 0] = np.nan # This makes distance from 19 to 20 NaN, and 20 to 21 NaN.
    
    res_nan = analyze_kinetic_chain(dummy_nan)
    fixtures.append({
        "name": "with_nan",
        "pose": dummy_nan.tolist(),
        "expected_peak_frames": res_nan["peak_frames"],
        "expected_timing_ms": res_nan["timing_ms"],
        "expected_is_correct_chain": res_nan["is_correct_chain"],
        "expected_velocities": res_nan["velocities"]
    })

    # 5. 짧은 프레임 (<2)
    dummy_short = np.zeros((1, 33, 4))
    res_short = analyze_kinetic_chain(dummy_short)
    fixtures.append({
        "name": "too_short",
        "pose": dummy_short.tolist(),
        "expected_peak_frames": None,
        "expected_timing_ms": None,
        "expected_is_correct_chain": None,
        "expected_velocities": None
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
            elif isinstance(v, dict):
                sf[k] = {dk: ([sanitize(x) for x in dv] if isinstance(dv, np.ndarray) else dv) for dk, dv in v.items()}
            else:
                sf[k] = sanitize(v)
        sanitized.append(sf)

    with open('../TennisDocAI/core/vision/src/test/resources/golden_kinetic_chain_fixture.json', 'w') as f:
        json.dump(sanitized, f, indent=2)

if __name__ == "__main__":
    generate_fixture()
    print("golden_kinetic_chain_fixture.json generated")
