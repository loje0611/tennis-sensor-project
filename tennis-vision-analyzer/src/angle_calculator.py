import numpy as np

def calculate_3d_angle(a, b, c):
    """
    공간상의 세 점(a, b, c)을 이용하여 b(중심점)에서 형성되는 3D 내적 각도를 계산합니다.
    
    Args:
        a, b, c: [x, y, z] 형태의 리스트 또는 NumPy 배열
                 (예: MediaPipe 랜드마크 데이터)
                 
    Returns:
        angle_degrees: 계산된 3D 내적 각도 (0 ~ 180도)
        만약 좌표가 유효하지 않거나(NaN) 크기가 0인 경우 NaN을 반환합니다.
    """
    a = np.array(a)[:3] # x, y, z만 사용 (visibility 무시)
    b = np.array(b)[:3]
    c = np.array(c)[:3]
    
    # 결측치(NaN)가 포함되어 있는지 확인
    if np.any(np.isnan(a)) or np.any(np.isnan(b)) or np.any(np.isnan(c)):
        return np.nan
        
    # 벡터 계산
    ba = a - b
    bc = c - b
    
    # 벡터의 크기(Norm) 계산
    norm_ba = np.linalg.norm(ba)
    norm_bc = np.linalg.norm(bc)
    
    if norm_ba == 0 or norm_bc == 0:
        return np.nan
        
    # 내적(Dot Product)을 이용한 코사인 값 계산
    cosine_angle = np.dot(ba, bc) / (norm_ba * norm_bc)
    
    # 부동소수점 오차 방지를 위해 값의 범위를 [-1.0, 1.0]으로 클리핑
    cosine_angle = np.clip(cosine_angle, -1.0, 1.0)
    
    # 아크코사인(arccos)을 통해 라디안 각도 계산 후 디그리(Degree)로 변환
    angle_radians = np.arccos(cosine_angle)
    angle_degrees = np.degrees(angle_radians)
    
    return angle_degrees

def get_joint_angles_from_pose(pose_frame):
    """
    MediaPipe의 단일 프레임 포즈 데이터(33, 4)에서 테니스 스윙 분석에 
    필요한 주요 관절 각도를 추출합니다.
    
    주요 분석 포인트:
    1. 팔 펴짐 각도 (오른팔 기준: 오른쪽 어깨(12) - 오른쪽 팔꿈치(14) - 오른쪽 손목(16))
    2. 무릎 굽힘 각도 (오른쪽 다리 기준: 오른쪽 골반(24) - 오른쪽 무릎(26) - 오른쪽 발목(28))
    3. 상체 회전 각도 (왼쪽 어깨(11) - 골반 중앙(23,24 평균) - 오른쪽 어깨(12) 등을 응용할 수 있으나, 
                     여기서는 단순화하여 왼쪽어깨-오른쪽어깨 벡터와 골반 벡터의 비틀림 각도로 추후 확장)
                     현재는 팔과 무릎 각도만 우선 반환합니다.
    """
    if len(pose_frame) < 33:
        return {"right_arm_angle": np.nan, "right_knee_angle": np.nan}
        
    # 오른손잡이 기준 인덱스 (향후 좌/우 설정 가능하도록 확장 가능)
    R_SHOULDER = 12
    R_ELBOW = 14
    R_WRIST = 16
    
    R_HIP = 24
    R_KNEE = 26
    R_ANKLE = 28
    
    right_arm_angle = calculate_3d_angle(
        pose_frame[R_SHOULDER], 
        pose_frame[R_ELBOW], 
        pose_frame[R_WRIST]
    )
    
    right_knee_angle = calculate_3d_angle(
        pose_frame[R_HIP], 
        pose_frame[R_KNEE], 
        pose_frame[R_ANKLE]
    )
    
    return {
        "right_arm_angle": right_arm_angle,
        "right_knee_angle": right_knee_angle
    }

if __name__ == "__main__":
    # 간단한 단독 실행 테스트
    print("--- 3D 관절 각도 계산 테스트 ---")
    
    # 1. 일직선인 경우 (180도)
    p1 = [0, 1, 0]
    p2 = [0, 0, 0]
    p3 = [0, -1, 0]
    print(f"일직선 각도: {calculate_3d_angle(p1, p2, p3):.2f}° (기대값: 180.00°)")
    
    # 2. 직각인 경우 (90도)
    p1 = [1, 0, 0]
    p2 = [0, 0, 0]
    p3 = [0, 1, 0]
    print(f"직각 각도: {calculate_3d_angle(p1, p2, p3):.2f}° (기대값: 90.00°)")
