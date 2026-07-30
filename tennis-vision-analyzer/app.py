import streamlit as st
import os
import sys
import tempfile
import cv2
import plotly.graph_objects as go

# 내부 모듈 임포트
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), 'src')))
from pose_extractor import process_video
from impact_detector import detect_impact_frame
from kinetic_chain import analyze_kinetic_chain
from swing_path import classify_swing_path
from overlay_renderer import render_overlay
from angle_calculator import get_joint_angles_from_pose

st.set_page_config(page_title="Tennis Vision Analyzer", layout="wide")

st.title("🎾 테니스 비전 AI 스윙 분석기")
st.markdown("스마트폰으로 촬영한 테니스 스윙 영상을 업로드하면 AI가 자세와 운동 역학을 분석합니다.")

# 사이드바 구성
st.sidebar.header("1. 영상 업로드")
uploaded_file = st.sidebar.file_uploader("MP4 비디오 파일을 선택하세요", type=['mp4', 'mov'])

if uploaded_file is not None:
    tfile = tempfile.NamedTemporaryFile(delete=False, suffix='.mp4')
    tfile.write(uploaded_file.read())
    video_path = tfile.name
    
    st.sidebar.success("영상 업로드 완료!")
    st.sidebar.video(video_path)
    
    if st.sidebar.button("AI 분석 시작하기"):
        with st.spinner("AI가 영상을 심층 분석 중입니다... (1~2분 소요)"):
            
            # 단계 1: MediaPipe 포즈 추출
            st.text("1. 관절 랜드마크 추출 중...")
            pose_data = process_video(video_path, model_path="models/pose_landmarker_full.task")
            
            if pose_data is None:
                st.error("포즈 추출에 실패했습니다. 영상을 다시 확인해주세요.")
                st.stop()
                
            fps = 30.0
            cap = cv2.VideoCapture(video_path)
            if cap.isOpened():
                fps = cap.get(cv2.CAP_PROP_FPS)
            cap.release()
            
            # 단계 2: 임팩트 감지
            st.text("2. 임팩트 프레임 감지 중...")
            impact_frame, _ = detect_impact_frame(pose_data, fps=fps, hand='right')
            
            # 단계 3: 샷 궤적 분류
            st.text("3. 스윙 궤적 분류 중...")
            swing_type = classify_swing_path(pose_data, impact_frame, hand='right')
            
            # 단계 4: 운동 체인 및 각도 추출
            st.text("4. 관절 각도 및 운동 체인 역학 계산 중...")
            arm_angles = []
            knee_angles = []
            for frame_joints in pose_data:
                angles = get_joint_angles_from_pose(frame_joints)
                arm_angles.append(angles.get("right_arm_angle", 0))
                knee_angles.append(angles.get("right_knee_angle", 0))
                
            chain_data = analyze_kinetic_chain(pose_data, fps=fps, hand='right')
            
            # 단계 5: 스켈레톤 영상 렌더링
            st.text("5. 스켈레톤 영상 렌더링 중...")
            output_video_path = video_path.replace(".mp4", "_analyzed.mp4")
            render_overlay(video_path, pose_data, impact_frame=impact_frame, output_path=output_video_path)
            
            # h264 변환 (Streamlit 웹 호환성)
            h264_video_path = video_path.replace(".mp4", "_analyzed_h264.mp4")
            try:
                from moviepy.editor import VideoFileClip
                clip = VideoFileClip(output_video_path)
                clip.write_videofile(h264_video_path, codec="libx264", audio=False, logger=None)
            except Exception as e:
                st.warning(f"Video conversion failed: {e}")
            
            st.success("분석이 성공적으로 완료되었습니다!")
            
            # ---------------------------------------------------------
            # 분석 결과 메인 화면 출력 (Task 9)
            # ---------------------------------------------------------
            st.markdown("---")
            col1, col2 = st.columns([1, 1])
            
            with col1:
                st.subheader("🎥 AI 스켈레톤 분석 영상")
                if os.path.exists(h264_video_path):
                    st.video(h264_video_path)
                else:
                    st.video(output_video_path)
                    
            with col2:
                st.subheader("📊 스윙 핵심 지표 (Key Metrics)")
                
                # 핵심 지표를 카드 형태로 표시
                metric_col1, metric_col2 = st.columns(2)
                metric_col1.metric("감지된 샷 유형", swing_type)
                metric_col2.metric("임팩트 프레임", f"{impact_frame} 프레임")
                
                if chain_data:
                    is_correct = "✅ 올바름" if chain_data["is_correct_chain"] else "⚠️ 주의 (순서 꼬임)"
                    metric_col1.metric("운동 체인 (Kinetic Chain)", is_correct)
                    metric_col2.metric("하체->어깨 에너지 전달", f"{chain_data['timing_ms']['hip_to_shoulder']:.1f} ms")
                    
            st.markdown("---")
            st.subheader("📈 상세 역학 그래프")
            
            chart_col1, chart_col2 = st.columns(2)
            
            # 1. 관절 각도 그래프 (Plotly)
            with chart_col1:
                st.markdown("**1. 임팩트 시점의 관절 펴짐/굽힘 (Angles)**")
                frames_x = list(range(len(arm_angles)))
                
                fig_angle = go.Figure()
                fig_angle.add_trace(go.Scatter(x=frames_x, y=arm_angles, mode='lines', name='팔 펴짐 각도 (어깨-팔꿈치-손목)'))
                fig_angle.add_trace(go.Scatter(x=frames_x, y=knee_angles, mode='lines', name='무릎 굽힘 각도 (골반-무릎-발목)'))
                
                if impact_frame is not None:
                    fig_angle.add_vline(x=impact_frame, line_dash="dash", line_color="red", annotation_text="IMPACT")
                    
                fig_angle.update_layout(xaxis_title="Frame", yaxis_title="Angle (Degrees)", height=350, margin=dict(l=0, r=0, t=30, b=0))
                st.plotly_chart(fig_angle, use_container_width=True)
                
            # 2. 운동 체인 (속도 피크) 그래프
            with chart_col2:
                st.markdown("**2. 관절별 운동 체인 속도 전이 (Kinetic Chain)**")
                if chain_data:
                    fig_chain = go.Figure()
                    vel_hip = chain_data["velocities"]["hip"]
                    vel_shoulder = chain_data["velocities"]["shoulder"]
                    vel_wrist = chain_data["velocities"]["wrist"]
                    
                    fig_chain.add_trace(go.Scatter(x=frames_x, y=vel_hip, mode='lines', name='1. 하체 (Hip)'))
                    fig_chain.add_trace(go.Scatter(x=frames_x, y=vel_shoulder, mode='lines', name='2. 몸통 (Shoulder)'))
                    fig_chain.add_trace(go.Scatter(x=frames_x, y=vel_wrist, mode='lines', name='3. 팔 (Wrist)'))
                    
                    # 피크 지점 마킹
                    peaks = chain_data["peak_frames"]
                    fig_chain.add_trace(go.Scatter(
                        x=[peaks["hip"], peaks["shoulder"], peaks["wrist"]],
                        y=[vel_hip[peaks["hip"]], vel_shoulder[peaks["shoulder"]], vel_wrist[peaks["wrist"]]],
                        mode='markers', marker=dict(size=10, symbol='star'), name='Peak (Max Velocity)'
                    ))
                    
                    fig_chain.update_layout(xaxis_title="Frame", yaxis_title="Velocity (Relative)", height=350, margin=dict(l=0, r=0, t=30, b=0))
                    st.plotly_chart(fig_chain, use_container_width=True)
                else:
                    st.warning("데이터가 부족하여 운동 체인을 시각화할 수 없습니다.")
else:
    st.info("👈 좌측 사이드바에서 테니스 스윙 영상(.mp4)을 업로드해 주세요.")
