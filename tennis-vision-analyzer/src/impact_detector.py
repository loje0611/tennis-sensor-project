import numpy as np
from scipy.signal import find_peaks

def calculate_velocity(pose_data, joint_index, fps=30):
    """
    특정 관절의 3D 공간 상 이동 속도를 계산합니다.
    
    Args:
        pose_data: (Frames, 33, 4) 형태의 NumPy 배열
        joint_index: 추적할 관절 인덱스 (예: 오른쪽 손목 = 16)
        fps: 영상의 초당 프레임 수
        
    Returns:
        velocities: 프레임별 속도(거리 변화율)를 담은 1D 배열
    """
    if len(pose_data) < 2:
        return np.array([])
        
    # 특정 관절의 (x, y, z) 궤적만 추출, Shape: (Frames, 3)
    joint_trajectory = pose_data[:, joint_index, :3]
    
    # 프레임 간 3D 좌표 차이 계산 (유클리디안 거리), Shape: (Frames-1, 3)
    diff = np.diff(joint_trajectory, axis=0)
    
    # 속도(유클리디안 거리) = sqrt(dx^2 + dy^2 + dz^2), Shape: (Frames-1,)
    distances = np.linalg.norm(diff, axis=1)
    
    # 초당 이동 거리(속도) 변환
    velocities = distances * fps
    
    # 속도 배열 크기를 전체 프레임 수와 맞추기 위해 첫 프레임의 속도를 0으로 삽입
    velocities = np.insert(velocities, 0, 0.0)
    
    return velocities

def detect_impact_frame(pose_data, fps=30, hand='right'):
    """
    라켓을 쥔 손목 관절의 최대 속도(각속도 포함) 지점을 
    임팩트 프레임으로 추정합니다.
    """
    WRIST_IDX = 16 if hand == 'right' else 15
    
    # 속도 계산
    velocities = calculate_velocity(pose_data, WRIST_IDX, fps)
    
    if len(velocities) == 0:
        return None, velocities
        
    # 관절을 놓쳐서 발생한 결측치(NaN)는 0으로 치환하여 피크 분석에 방해되지 않도록 처리
    velocities_clean = np.nan_to_num(velocities, nan=0.0)
    
    # 노이즈를 방지하기 위해 SciPy의 find_peaks를 사용하여 유의미한 피크들을 찾음
    # height: 최대 속도의 40% 이상인 지점들만 피크로 간주
    # distance: 최소 10 프레임 이상 떨어져 있어야 새로운 스윙으로 간주
    min_peak_height = np.max(velocities_clean) * 0.4
    if min_peak_height == 0:
        return 0, velocities
        
    peaks, properties = find_peaks(velocities_clean, height=min_peak_height, distance=10)
    
    if len(peaks) > 0:
        # 여러 피크 중 가장 속도가 높은(강한) 피크를 메인 임팩트 프레임으로 선택
        impact_frame = int(peaks[np.argmax(properties['peak_heights'])])
    else:
        # 피크를 찾지 못한 경우 단순 최대값 프레임 반환
        impact_frame = int(np.argmax(velocities_clean))
        
    return impact_frame, velocities

if __name__ == "__main__":
    print("--- 임팩트 프레임 감지 테스트 ---")
    # 더미 데이터 생성 테스트 (60프레임)
    dummy_pose = np.zeros((60, 33, 4))
    
    # 30번 프레임 부근에서 손목(16번)이 급격히 이동하도록 설정
    for i in range(60):
        if 25 <= i <= 35:
            # 25~35 프레임 구간에서 가속(2차 함수)
            dummy_pose[i, 16, :3] = [0.01 * (i-25)**2, 0.5, 0.0]
        elif i > 35:
            # 타격 이후 감속 또는 정지 상태 가정
            dummy_pose[i, 16, :3] = [0.01 * 100, 0.5, 0.0]
            
    impact_frame, vels = detect_impact_frame(dummy_pose)
    print(f"Calculated Impact Frame: {impact_frame} (Expected around 35)")
    print(f"Max Velocity at Impact: {vels[impact_frame]:.4f}")
