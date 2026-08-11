import json
import numpy as np
from swing_path import classify_swing_path

def generate_fixture():
    fixtures = []

    # 1. Topspin (Y decreasing, slope < -0.005)
    dummy_topspin = np.zeros((30, 33, 4))
    for i in range(30):
        dummy_topspin[i, 16, 1] = 0.5 - 0.01 * i # decreasing
    
    slope_topspin = np.polyfit(np.arange(20), dummy_topspin[5:25, 16, 1], 1)[0]
    cls_topspin = classify_swing_path(dummy_topspin, impact_frame=15, analysis_window=10)
    fixtures.append({
        "name": "topspin",
        "pose": dummy_topspin.tolist(),
        "impact_frame": 15,
        "expected_slope": float(slope_topspin),
        "expected_class": cls_topspin
    })

    # 2. Slice (Y increasing, slope > 0.005)
    dummy_slice = np.zeros((30, 33, 4))
    for i in range(30):
        dummy_slice[i, 16, 1] = 0.2 + 0.01 * i # increasing

    slope_slice = np.polyfit(np.arange(20), dummy_slice[5:25, 16, 1], 1)[0]
    cls_slice = classify_swing_path(dummy_slice, impact_frame=15, analysis_window=10)
    fixtures.append({
        "name": "slice",
        "pose": dummy_slice.tolist(),
        "impact_frame": 15,
        "expected_slope": float(slope_slice),
        "expected_class": cls_slice
    })

    # 3. Flat (Flat, |slope| <= 0.005)
    dummy_flat = np.zeros((30, 33, 4))
    for i in range(30):
        dummy_flat[i, 16, 1] = 0.5 + 0.001 * i # slightly increasing but < 0.005
    
    slope_flat = np.polyfit(np.arange(20), dummy_flat[5:25, 16, 1], 1)[0]
    cls_flat = classify_swing_path(dummy_flat, impact_frame=15, analysis_window=10)
    fixtures.append({
        "name": "flat",
        "pose": dummy_flat.tolist(),
        "impact_frame": 15,
        "expected_slope": float(slope_flat),
        "expected_class": cls_flat
    })

    # 4. Unknown (Impact None)
    dummy_unknown = np.zeros((30, 33, 4))
    cls_unknown = classify_swing_path(dummy_unknown, impact_frame=None, analysis_window=10)
    fixtures.append({
        "name": "unknown_impact_none",
        "pose": dummy_unknown.tolist(),
        "impact_frame": None,
        "expected_slope": "NaN",
        "expected_class": cls_unknown
    })

    # 5. Unknown (NaN in trajectory, <2 points left)
    dummy_nan = np.zeros((30, 33, 4))
    dummy_nan[:, 16, 1] = np.nan
    dummy_nan[10, 16, 1] = 0.5 # only 1 valid point
    cls_nan = classify_swing_path(dummy_nan, impact_frame=15, analysis_window=10)
    fixtures.append({
        "name": "unknown_not_enough_points",
        "pose": dummy_nan.tolist(),
        "impact_frame": 15,
        "expected_slope": "NaN",
        "expected_class": cls_nan
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

    with open('../TennisDocAI/core/vision/src/test/resources/golden_swing_path_fixture.json', 'w') as f:
        json.dump(sanitized, f, indent=2)

if __name__ == "__main__":
    generate_fixture()
    print("golden_swing_path_fixture.json generated")
