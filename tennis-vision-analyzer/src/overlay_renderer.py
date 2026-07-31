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

def render_overlay(video_path, pose_data, impact_frames=None, swing_feedbacks=None, output_path=None):
    """
    원본 영상 위에 3D 스켈레톤 포즈를 오버레이하여 새로운 영상으로 저장합니다.
    임팩트 프레임이 주어지면 붉은색 테두리와 'IMPACT!' 텍스트 효과를 추가합니다.
    swing_feedbacks가 제공되면 해당 프레임에서 영상을 일시정지(프레임 반복)하고 툴팁을 표시합니다.
    """
    if not os.path.exists(video_path):
        print(f"Error: Video file not found at {video_path}")
        return None
        
    if output_path is None:
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
            
        base_frame = frame.copy()
        frame_joints = None
        
        if current_frame < len(pose_data):
            frame_joints = pose_data[current_frame]
            
            if not np.any(np.isnan(frame_joints[0])):
                # 1. 랜드마크 점 그리기
                points = []
                for idx, joint in enumerate(frame_joints):
                    if np.isnan(joint[0]) or np.isnan(joint[1]):
                        points.append(None)
                        continue
                        
                    px = int(joint[0] * width)
                    py = int(joint[1] * height)
                    points.append((px, py))
                    
                    vis = joint[3] if len(joint) > 3 else 1.0
                    if vis > 0.5:
                        cv2.circle(base_frame, (px, py), 4, (0, 255, 0), -1)
                        
                # 2. 뼈대 연결선 그리기
                for connection in POSE_CONNECTIONS:
                    idx1, idx2 = connection
                    if idx1 < len(points) and idx2 < len(points):
                        if points[idx1] is None or points[idx2] is None:
                            continue
                            
                        vis1 = frame_joints[idx1][3] if len(frame_joints[idx1]) > 3 else 1.0
                        vis2 = frame_joints[idx2][3] if len(frame_joints[idx2]) > 3 else 1.0
                        
                        if vis1 > 0.5 and vis2 > 0.5:
                            cv2.line(base_frame, points[idx1], points[idx2], (255, 255, 255), 2)
                            
        # 일반 프레임 렌더링
        normal_frame = base_frame.copy()
        if impact_frames is not None and current_frame in impact_frames:
            cv2.rectangle(normal_frame, (0, 0), (width, height), (0, 0, 255), 10)
            cv2.putText(normal_frame, "IMPACT!", (50, 80), cv2.FONT_HERSHEY_SIMPLEX, 1.5, (0, 0, 255), 4, cv2.LINE_AA)
            
        out.write(normal_frame)
        
        # 오토 일시정지 및 툴팁 렌더링 (임팩트 프레임에서 2.5초간 반복)
        if impact_frames is not None and current_frame in impact_frames and swing_feedbacks and current_frame in swing_feedbacks:
            pause_frames = int(fps * 2.5)
            feedbacks = swing_feedbacks[current_frame]
            
            pause_frame = normal_frame.copy()
            # 반투명 오버레이
            overlay = pause_frame.copy()
            cv2.rectangle(overlay, (0, 0), (width, height), (0, 0, 0), -1)
            cv2.addWeighted(overlay, 0.5, pause_frame, 0.5, 0, pause_frame)
            
            for i, fb in enumerate(feedbacks):
                text = fb["text"]
                target_idx = fb["target_joint"]
                
                # 타겟 관절 좌표 확인
                if frame_joints is not None and not np.isnan(frame_joints[target_idx][0]):
                    tx = int(frame_joints[target_idx][0] * width)
                    ty = int(frame_joints[target_idx][1] * height)
                    
                    # 툴팁 박스 위치 (관절 근처, 글씨 크기가 커졌으므로 간격 넓힘)
                    box_x = min(max(tx + 50, 50), width - 600)
                    box_y = min(max(ty - 50 + (i * 100), 100), height - 100)
                    
                    # 지시선 그리기
                    cv2.line(pause_frame, (tx, ty), (box_x, box_y), (0, 255, 255), 2)
                    cv2.circle(pause_frame, (tx, ty), 8, (0, 255, 255), -1)
                    cv2.circle(pause_frame, (tx, ty), 15, (0, 255, 255), 2)
                    
                    # 텍스트 배경 박스 (글씨 2배 키움)
                    font_scale = 1.6
                    thickness = 3
                    (tw, th), _ = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, font_scale, thickness)
                    cv2.rectangle(pause_frame, (box_x - 15, box_y - th - 15), (box_x + tw + 15, box_y + 15), (0, 0, 0), -1)
                    cv2.rectangle(pause_frame, (box_x - 15, box_y - th - 15), (box_x + tw + 15, box_y + 15), (0, 255, 255), 2)
                    
                    # 텍스트
                    cv2.putText(pause_frame, text, (box_x, box_y), cv2.FONT_HERSHEY_SIMPLEX, font_scale, (0, 255, 255), thickness, cv2.LINE_AA)
            
            # 정지 프레임 반복 쓰기
            for _ in range(pause_frames):
                out.write(pause_frame)
                
        current_frame += 1
        
        if current_frame % 50 == 0:
            print(f"Rendering frame {current_frame}/{total_frames}...")
            
    cap.release()
    out.release()
    print(f"Rendered video saved successfully to {output_path}")
    
    return output_path

if __name__ == "__main__":
    print("This module is intended to be imported. For full pipeline, see app.py")
