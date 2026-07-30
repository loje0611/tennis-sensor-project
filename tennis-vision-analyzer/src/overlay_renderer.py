import cv2
import numpy as np
import os

# MediaPipe 33-point 랜드마크 연결 정의 (수동 정의로 의존성 제거)
POSE_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7), (0, 4), (4, 5), (5, 6), (6, 8), (9, 10),
    (11, 12), (11, 13), (13, 15), (15, 17), (15, 19), (15, 21), (17, 19),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22), (18, 20),
    (11, 23), (12, 24), (23, 24),
    (23, 25), (24, 26), (25, 27), (26, 28), (27, 29), (28, 30),
    (29, 31), (30, 32), (27, 31), (28, 32)
]

def render_overlay(video_path, pose_data, impact_frame=None, output_path=None):
    """
    원본 영상 위에 3D 스켈레톤 포즈를 오버레이하여 새로운 영상으로 저장합니다.
    임팩트 프레임이 주어지면 붉은색 테두리와 'IMPACT!' 텍스트 효과를 추가합니다.
    """
    if not os.path.exists(video_path):
        print(f"Error: Video file not found at {video_path}")
        return None
        
    if output_path is None:
        # 기본 출력 파일명 설정
        base, ext = os.path.splitext(video_path)
        output_path = base + "_analyzed" + ext
        
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print("Error: Could not open video for rendering.")
        return None
        
    fps = cap.get(cv2.CAP_PROP_FPS)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
    
    current_frame = 0
    while True:
        ret, frame = cap.read()
        if not ret:
            break
            
        if current_frame < len(pose_data):
            frame_joints = pose_data[current_frame]
            
            # 유효한(결측치가 아닌) 데이터만 렌더링
            if not np.any(np.isnan(frame_joints[0])):
                
                # 1. 랜드마크 점 그리기
                points = []
                for idx, joint in enumerate(frame_joints):
                    # 좌표 정규화 값(0.0~1.0)을 실제 픽셀 좌표로 변환
                    px = int(joint[0] * width)
                    py = int(joint[1] * height)
                    points.append((px, py))
                    
                    # 가시성(Visibility)이 0.5 이상인 관절만 그림
                    vis = joint[3] if len(joint) > 3 else 1.0
                    if vis > 0.5:
                        cv2.circle(frame, (px, py), 4, (0, 255, 0), -1)
                        
                # 2. 뼈대 연결선 그리기
                for connection in POSE_CONNECTIONS:
                    idx1, idx2 = connection
                    if idx1 < len(points) and idx2 < len(points):
                        # 가시성 체크
                        vis1 = frame_joints[idx1][3] if len(frame_joints[idx1]) > 3 else 1.0
                        vis2 = frame_joints[idx2][3] if len(frame_joints[idx2]) > 3 else 1.0
                        
                        if vis1 > 0.5 and vis2 > 0.5:
                            cv2.line(frame, points[idx1], points[idx2], (255, 255, 255), 2)
                            
        # 임팩트 효과 렌더링
        if impact_frame is not None and current_frame == impact_frame:
            # 붉은 테두리
            cv2.rectangle(frame, (0, 0), (width, height), (0, 0, 255), 15)
            # 텍스트
            cv2.putText(frame, "IMPACT!", (50, 100), cv2.FONT_HERSHEY_SIMPLEX, 
                        2, (0, 0, 255), 5, cv2.LINE_AA)
                        
        out.write(frame)
        current_frame += 1
        
        if current_frame % 50 == 0:
            print(f"Rendering frame {current_frame}/{total_frames}...")
            
    cap.release()
    out.release()
    print(f"Rendered video saved successfully to {output_path}")
    
    return output_path

if __name__ == "__main__":
    print("This module is intended to be imported. For full pipeline, see app.py")
