import numpy as np

def classify_swing_path(pose_data, impact_frame, hand='right', analysis_window=10):
    """
    임팩트 프레임 전후의 손목 관절 궤적을 분석하여 스윙 종류를 분류합니다.
    
    MediaPipe 좌표계에서 Y축은 상단이 0, 하단이 1이므로, 
    Y값이 점차 작아지는 것이 손목이 위로 올라가는 궤적(Topspin)을 의미합니다.
    """
    if impact_frame is None or len(pose_data) == 0:
        return "Unknown"
        
    WRIST_IDX = 16 if hand == 'right' else 15
    
    # 임팩트 전후 프레임 인덱스 설정
    start_frame = max(0, impact_frame - analysis_window)
    end_frame = min(len(pose_data), impact_frame + analysis_window)
    
    # 해당 구간의 손목 Y좌표 (화면상 높이) 추출
    wrist_y_trajectory = pose_data[start_frame:end_frame, WRIST_IDX, 1]
    
    # 결측치 제거
    wrist_y_trajectory = wrist_y_trajectory[~np.isnan(wrist_y_trajectory)]
    if len(wrist_y_trajectory) < 2:
        return "Unknown"
        
    # 선형 회귀(1차 함수 피팅)로 Y값 변화의 평균 기울기(Slope) 계산
    x = np.arange(len(wrist_y_trajectory))
    slope, _ = np.polyfit(x, wrist_y_trajectory, 1)
    
    # slope < -0.005 : Y값이 점차 작아짐 = 라켓이 위로 올라감 (상향 궤적)
    # slope > 0.005 : Y값이 점차 커짐 = 라켓이 아래로 내려감 (하향 궤적)
    THRESHOLD = 0.005
    if slope < -THRESHOLD:
        return "Topspin"
    elif slope > THRESHOLD:
        return "Slice"
    else:
        return "Flat"

def get_swing_trajectory_3d(pose_data, WRIST_IDX=16):
    """
    손목의 전체 3D 궤적 데이터를 반환하여 Plotly 3D 그래프 등에 사용합니다.
    결측치(NaN)는 제외하고 반환합니다.
    """
    trajectory = pose_data[:, WRIST_IDX, :3]
    # x좌표가 NaN이 아닌 행들만 추출
    valid_mask = ~np.isnan(trajectory[:, 0])
    return trajectory[valid_mask]
