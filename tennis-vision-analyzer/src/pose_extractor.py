import cv2
import sys
import os

def process_video(video_path):
    if not os.path.exists(video_path):
        print(f"Error: Video file not found at {video_path}")
        return
        
    # 영상 로딩 (cv2.VideoCapture)
    cap = cv2.VideoCapture(video_path)
    
    if not cap.isOpened():
        print(f"Error: Could not open video {video_path}")
        return
        
    # 영상 메타데이터 추출
    fps = cap.get(cv2.CAP_PROP_FPS)
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    
    print(f"Successfully loaded {video_path}")
    print(f"Resolution: {width}x{height}, FPS: {fps:.2f}, Total Frames: {frame_count}")
    
    current_frame = 0
    while True:
        ret, frame = cap.read()
        if not ret:
            break
            
        current_frame += 1
        
        # [Task 2] MediaPipe 관절 좌표 추출 로직이 여기에 들어갑니다.
        
        # 30프레임마다 진행 상황 출력
        if current_frame % 30 == 0:
            print(f"Processing frame {current_frame}/{frame_count}...")
            
    cap.release()
    print("Video processing complete!")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python pose_extractor.py <path_to_video>")
        print("Example: python pose_extractor.py ../sample_videos/my_swing.mp4")
    else:
        process_video(sys.argv[1])
