import cv2
import sys
import os
import numpy as np
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

def process_video(video_path, model_path="../models/pose_landmarker_full.task"):
    # 프로젝트 루트 또는 src 폴더 내에서 실행할 때를 대비한 경로 처리
    if not os.path.exists(model_path):
        model_path = "models/pose_landmarker_full.task"
        if not os.path.exists(model_path):
            print(f"Error: Model not found at {model_path}. Please download it to models/ folder.")
            return
            
    if not os.path.exists(video_path):
        print(f"Error: Video file not found at {video_path}")
        return
        
    # MediaPipe PoseLandmarker 옵션 설정 (Vision API V2)
    base_options = python.BaseOptions(model_asset_path=model_path)
    options = vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        output_segmentation_masks=False)
        
    detector = vision.PoseLandmarker.create_from_options(options)
        
    cap = cv2.VideoCapture(video_path)
    
    if not cap.isOpened():
        print(f"Error: Could not open video {video_path}")
        return
        
    fps = cap.get(cv2.CAP_PROP_FPS)
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    
    print(f"Successfully loaded {video_path}")
    print(f"Resolution: {width}x{height}, FPS: {fps:.2f}, Total Frames: {frame_count}")
    
    # 각 프레임의 관절 데이터를 저장할 리스트
    pose_history = []
    
    current_frame = 0
    while True:
        ret, frame = cap.read()
        if not ret:
            break
            
        current_frame += 1
        
        # BGR -> RGB 변환
        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        
        # MediaPipe Image 객체 생성
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        
        # 프레임 타임스탬프 계산 (ms)
        timestamp_ms = int(cap.get(cv2.CAP_PROP_POS_MSEC))
        # 일부 영상은 timestamp_ms가 제대로 추출되지 않으므로 보완
        if timestamp_ms == 0 and current_frame > 1:
            timestamp_ms = int((current_frame / fps) * 1000)
            
        # 포즈 추론
        try:
            detection_result = detector.detect_for_video(mp_image, timestamp_ms)
        except Exception as e:
            # 타임스탬프 중복 등의 오류 방지용 안전 장치
            timestamp_ms += 1
            detection_result = detector.detect_for_video(mp_image, timestamp_ms)
            
        frame_landmarks = []
        if detection_result.pose_landmarks and len(detection_result.pose_landmarks) > 0:
            # 테니스 영상의 주 인물 1명만 사용
            landmarks = detection_result.pose_landmarks[0]
            for landmark in landmarks:
                # presence, visibility 속성이 포함되어 있음
                vis = landmark.visibility if hasattr(landmark, 'visibility') else 1.0
                frame_landmarks.append([landmark.x, landmark.y, landmark.z, vis])
        else:
            # 관절을 인식하지 못한 경우 결측치(NaN) 처리
            frame_landmarks = [[np.nan, np.nan, np.nan, np.nan]] * 33
            
        pose_history.append(frame_landmarks)
        
        if current_frame % 30 == 0:
            print(f"Processing frame {current_frame}/{frame_count}...")
            
    cap.release()
    detector.close()
    
    # 리스트를 NumPy 배열로 변환: shape = (프레임 수, 33, 4)
    pose_data = np.array(pose_history)
    print("Video processing complete!")
    print(f"Extracted Pose Data Shape: {pose_data.shape} (Frames, Joints, Features)")
    
    # 데이터를 .npy 파일로 저장 (다음 Task에서 재사용)
    output_path = os.path.splitext(video_path)[0] + "_pose.npy"
    np.save(output_path, pose_data)
    print(f"Pose data saved to: {output_path}")
    
    return pose_data

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python pose_extractor.py <path_to_video>")
        print("Example: python pose_extractor.py ../sample_videos/forehand_sample.mp4")
    else:
        process_video(sys.argv[1])
