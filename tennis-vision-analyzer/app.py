import streamlit as st
import os
import sys
import tempfile
import cv2

# 내부 모듈 임포트
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), 'src')))
from pose_extractor import process_video
from impact_detector import detect_impact_frame
from kinetic_chain import analyze_kinetic_chain
from swing_path import classify_swing_path
from overlay_renderer import render_overlay

st.set_page_config(page_title="Tennis Vision Analyzer", layout="wide")

st.title("🎾 테니스 비전 AI 스윙 분석기")
st.markdown("스마트폰으로 촬영한 테니스 스윙 영상을 업로드하면 AI가 자세를 분석합니다.")

# 사이드바 구성
st.sidebar.header("1. 영상 업로드")
uploaded_file = st.sidebar.file_uploader("MP4 비디오 파일을 선택하세요", type=['mp4', 'mov'])

if uploaded_file is not None:
    # 임시 파일로 저장
    tfile = tempfile.NamedTemporaryFile(delete=False, suffix='.mp4')
    tfile.write(uploaded_file.read())
    video_path = tfile.name
    
    st.sidebar.success("영상 업로드 완료!")
    st.sidebar.video(video_path)
    
    # 분석 시작 버튼
    if st.sidebar.button("AI 분석 시작하기"):
        with st.spinner("AI가 영상을 분석 중입니다... (1~2분 소요)"):
            
            # 단계 1: MediaPipe 포즈 추출
            st.text("1. 관절 랜드마크 추출 중...")
            # app.py는 루트에 있으므로 모델 경로는 'models/pose_landmarker_full.task'
            pose_data = process_video(video_path, model_path="models/pose_landmarker_full.task")
            
            if pose_data is None:
                st.error("포즈 추출에 실패했습니다. 영상이나 모델 경로를 확인하세요.")
                st.stop()
                
            # 단계 2: 임팩트 프레임 감지
            st.text("2. 임팩트 지점 감지 중...")
            fps = 30.0
            cap = cv2.VideoCapture(video_path)
            if cap.isOpened():
                fps = cap.get(cv2.CAP_PROP_FPS)
            cap.release()
            
            impact_frame, _ = detect_impact_frame(pose_data, fps=fps, hand='right')
            
            # 단계 3: 샷 궤적 분류
            st.text("3. 스윙 궤적 및 샷 분류 중...")
            swing_type = classify_swing_path(pose_data, impact_frame, hand='right')
            
            # 단계 4: 스켈레톤 영상 렌더링
            st.text("4. 스켈레톤 영상 생성 중...")
            output_video_path = video_path.replace(".mp4", "_analyzed.mp4")
            render_overlay(video_path, pose_data, impact_frame=impact_frame, output_path=output_video_path)
            
            # Streamlit 브라우저 호환을 위해 moviepy로 h264 변환 (mp4v 코덱 호환성 문제 해결)
            h264_video_path = video_path.replace(".mp4", "_analyzed_h264.mp4")
            try:
                from moviepy.editor import VideoFileClip
                clip = VideoFileClip(output_video_path)
                clip.write_videofile(h264_video_path, codec="libx264", audio=False, logger=None)
            except Exception as e:
                st.warning(f"Video conversion failed: {e}")
            
            st.success("분석이 완료되었습니다!")
            
            # 분석 결과 메인 화면 출력
            col1, col2 = st.columns([2, 1])
            
            with col1:
                st.subheader("🎥 AI 분석 결과 영상")
                if os.path.exists(h264_video_path):
                    st.video(h264_video_path)
                else:
                    st.video(output_video_path)
                    
            with col2:
                st.subheader("📊 스윙 분석 리포트")
                st.info(f"**감지된 샷 유형:** {swing_type}")
                st.info(f"**임팩트 지점:** {impact_frame} 프레임")
                
                # 향후 Task 9에서 차트를 이곳에 추가
                st.markdown("---")
                st.markdown("*(Task 9에서 관절 각도 그래프와 운동 체인 타이밍 차트가 추가될 예정입니다)*")
else:
    st.info("👈 좌측 사이드바에서 테니스 스윙 영상을 업로드해 주세요.")
