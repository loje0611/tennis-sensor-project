import streamlit as st
import os
import sys
import tempfile
import cv2
import plotly.graph_objects as go
import numpy as np

# 내부 모듈 임포트
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), 'src')))
from pose_extractor import process_video
from impact_detector import detect_impact_frame
from kinetic_chain import analyze_kinetic_chain
from swing_path import classify_swing_path
from swing_diagnosis import build_swing_feedbacks
from overlay_renderer import render_overlay
from angle_calculator import get_joint_angles_from_pose

st.set_page_config(page_title="Tennis Vision Analyzer", layout="wide")

# 세로 영상 재생 시 세로 스크롤 길어짐 방지 CSS
st.markdown("""
    <style>
        video {
            max-height: 65vh; /* 화면 높이의 65%까지만 렌더링 */
        }
    </style>
""", unsafe_allow_html=True)

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
            
            # 단계 2: 임팩트 감지 (다중 스윙 지원)
            st.text("2. 임팩트 프레임 감지 중...")
            # detect_impact_frame은 이제 impact_frames 리스트를 반환합니다.
            impact_frames, _ = detect_impact_frame(pose_data, fps=fps, hand='right')
            
            # 단계 3: 샷 궤적 분류
            st.text("3. 스윙 궤적 분류 중...")
            swing_types = []
            for frame in impact_frames:
                stype = classify_swing_path(pose_data, frame, hand='right')
                swing_types.append(stype)
            
            # 단계 4: 관절 각도 및 운동 체인 역학 계산
            st.text("4. 관절 각도 및 운동 체인 역학 계산 중...")
            arm_angles = []
            knee_angles = []
            for frame_joints in pose_data:
                angles = get_joint_angles_from_pose(frame_joints)
                arm_angles.append(angles.get("right_arm_angle", 0))
                knee_angles.append(angles.get("right_knee_angle", 0))
                
            chain_data = analyze_kinetic_chain(pose_data, fps=fps, hand='right')
            
            # 단계 4.5: 오토 일시정지 및 툴팁용 피드백 데이터 생성
            chain_velocities = chain_data["velocities"] if chain_data else None
            swing_feedbacks, _ = build_swing_feedbacks(impact_frames, swing_types, arm_angles, chain_velocities, fps)
            
            # 단계 5: 스켈레톤 영상 렌더링
            st.text("5. 스켈레톤 영상 렌더링 및 툴팁 합성 중...")
            output_video_path = video_path.replace(".mp4", "_analyzed.mp4")
            render_overlay(video_path, pose_data, impact_frames=impact_frames, swing_feedbacks=swing_feedbacks, output_path=output_video_path)
            
            # h264 변환 (Streamlit 웹 호환성)
            h264_video_path = video_path.replace(".mp4", "_analyzed_h264.mp4")
            try:
                from moviepy import VideoFileClip
                clip = VideoFileClip(output_video_path)
                clip.write_videofile(h264_video_path, codec="libx264", audio=False, logger=None)
                clip.close()
            except Exception as e:
                st.warning(f"Video conversion failed: {e}")
            
            st.success("분석이 성공적으로 완료되었습니다!")
            
            # ---------------------------------------------------------
            # 분석 결과 메인 화면 출력 (UI 재구성)
            # ---------------------------------------------------------
            st.markdown("---")
            
            # 레이아웃 분할: 좌측 영상(1), 우측 분석(1)
            col1, col2 = st.columns([1, 1])
            
            with col1:
                st.subheader("🎥 AI 스켈레톤 분석 영상")
                if os.path.exists(h264_video_path):
                    st.video(h264_video_path)
                else:
                    st.video(output_video_path)
                    
            with col2:
                st.subheader("🤖 AI 스윙 정밀 분석")
                
                if len(impact_frames) == 0:
                    st.warning("유의미한 임팩트(스윙)를 감지하지 못했습니다. 전신이 잘 나오게 촬영된 영상인지 확인해 주세요.")
                else:
                    st.success(f"총 {len(impact_frames)}번의 스윙이 감지되었습니다.")
                    
                    correct_chain_count = 0
                    chain_velocities = chain_data["velocities"] if chain_data else None
                    _, all_problems = build_swing_feedbacks(impact_frames, swing_types, arm_angles, chain_velocities, fps)
                    
                    # 각 스윙별 로컬 분석
                    for i, frame in enumerate(impact_frames):
                        st.markdown(f"#### 🎾 스윙 {i+1} ({(frame/fps):.2f}초)")
                        
                        stype = swing_types[i]
                        feedbacks = swing_feedbacks[frame]
                        
                        problems = []
                        has_chain_problem = False
                        
                        for fb in feedbacks:
                            if fb["text"] == "Use Hip First":
                                problems.append("**운동 체인 붕괴 (Use Hip First)**: 하체보다 상체(어깨)가 먼저 또는 동시에 회전하고 있습니다. 하체 회전 후 상체가 따라오는 꼬임(Separation)을 만들어야 합니다.")
                                has_chain_problem = True
                            elif fb["text"] == "Late Wrist":
                                problems.append("**손목 릴리스 지연 (Late Wrist)**: 팔(손목)의 가속이 어깨 회전과 분리되지 않았습니다. 임팩트 직전 라켓 헤드를 던지듯 뿌려주세요.")
                                has_chain_problem = True
                            elif fb["text"].startswith("Arm Bent"):
                                arm_angle = float(fb["text"].split("(")[1].split(")")[0])
                                problems.append(f"**타점 오류 (Arm Bent)**: 타격 시 팔이 너무 구부러져 있습니다 (각도 {arm_angle:.1f}도). 타점이 몸에 너무 가깝거나 타이밍이 늦습니다. 타점을 앞에서 잡으세요.")
                            elif fb["text"] == "Low Path":
                                problems.append(f"**스윙 궤적 (Low Path)**: 상향 스윙(Low-to-High) 궤적이 부족하여 네트에 걸리거나 아웃될 위험이 큽니다. 라켓을 더 아래로 떨어뜨렸다가(Drop) 올려치세요.")
                                
                        if chain_velocities and not has_chain_problem:
                            correct_chain_count += 1
                            
                        # 결과 출력
                        st.write(f"- **구질 분석**: {stype}")
                        
                        if not problems:
                            st.info("💡 **AI 피드백**: 훌륭한 스윙입니다! 운동 체인과 타점, 궤적이 모두 양호합니다.")
                        else:
                            for p in problems:
                                st.warning(f"⚠️ {p}")
                                
                    st.markdown("---")
                    st.subheader("🎯 최종 평가 및 처방")
                    if correct_chain_count == len(impact_frames) and len(all_problems) == 0:
                        st.success("모든 스윙에서 완벽한 운동 역학을 보여주고 있습니다! 현재의 폼을 유지하는 데 집중하세요.")
                    else:
                        st.error(f"전체 스윙 {len(impact_frames)}번 중 운동 체인이 올바르게 작동한 횟수는 {correct_chain_count}번입니다.")
                        
                        if all_problems:
                            from collections import Counter
                            most_common_problem = Counter(all_problems).most_common(1)[0][0]
                            st.markdown(f"**🚨 가장 시급히 개선해야 할 포인트: `{most_common_problem}`**")
                            
                            if most_common_problem == "운동 체인(하체->상체 순서)":
                                st.write("✅ **개선 방법**: 메디신 볼 던지기 연습이나, 라켓을 놓고 빈손으로 허리(골반)를 먼저 돌린 후 팔이 튕겨져 나오는 느낌을 찾는 빈스윙 연습을 추천합니다.")
                            elif most_common_problem == "팔/손목 가속":
                                st.write("✅ **개선 방법**: 그립을 너무 꽉 쥐고 있지 않은지 확인하세요. 라켓 무게를 느끼며 손목에 힘을 빼고 헤드가 먼저 돌아나가도록(Whip) 스윙해야 합니다.")
                            elif most_common_problem == "타점(팔 각도)":
                                st.write("✅ **개선 방법**: 스텝을 더 빨리 밟아 공과의 거리를 미리 확보하세요. 앞발(왼발) 앞쪽에서 타격이 이루어지도록 의식해야 합니다.")
                            elif most_common_problem == "상향 스윙 궤적":
                                st.write("✅ **개선 방법**: 테이크백 후 라켓 헤드를 공보다 아래로 충분히 떨어뜨린(Drop) 후, 와이퍼 스윙(Wiper Swing)을 통해 공을 긁어올리는 연습을 하세요.")
            
            # ---------------------------------------------------------
            # 상세 역학 그래프 (하단으로 이동)
            # ---------------------------------------------------------
            with st.expander("📊 상세 역학 그래프 보기 (참고사항)", expanded=False):
                chart_col1, chart_col2 = st.columns(2)
                
                times_x = [i / fps for i in range(len(arm_angles))]
                
                with chart_col1:
                    st.markdown("**1. 영상 전체 관절 펴짐/굽힘 (Angles)**")
                    fig_angle = go.Figure()
                    fig_angle.add_trace(go.Scatter(x=times_x, y=arm_angles, mode='lines', name='팔 펴짐 각도 (어깨-팔꿈치-손목)'))
                    fig_angle.add_trace(go.Scatter(x=times_x, y=knee_angles, mode='lines', name='무릎 굽힘 각도 (골반-무릎-발목)'))
                    
                    for idx, frame in enumerate(impact_frames):
                        fig_angle.add_vline(x=frame/fps, line_dash="dash", line_color="red", annotation_text=f"Impact {idx+1}")
                        
                    fig_angle.update_layout(xaxis_title="Time (Seconds)", yaxis_title="Angle (Degrees)", height=350, margin=dict(l=0, r=0, t=30, b=0))
                    st.plotly_chart(fig_angle, use_container_width=True)
                    
                with chart_col2:
                    st.markdown("**2. 관절별 운동 체인 속도 전이 (Kinetic Chain)**")
                    if chain_data:
                        fig_chain = go.Figure()
                        vel_hip = chain_data["velocities"]["hip"]
                        vel_shoulder = chain_data["velocities"]["shoulder"]
                        vel_wrist = chain_data["velocities"]["wrist"]
                        
                        fig_chain.add_trace(go.Scatter(x=times_x, y=vel_hip, mode='lines', name='1. 하체 (Hip)'))
                        fig_chain.add_trace(go.Scatter(x=times_x, y=vel_shoulder, mode='lines', name='2. 몸통 (Shoulder)'))
                        fig_chain.add_trace(go.Scatter(x=times_x, y=vel_wrist, mode='lines', name='3. 팔 (Wrist)'))
                        
                        peaks = chain_data["peak_frames"]
                        fig_chain.add_trace(go.Scatter(
                            x=[peaks["hip"]/fps, peaks["shoulder"]/fps, peaks["wrist"]/fps],
                            y=[vel_hip[peaks["hip"]], vel_shoulder[peaks["shoulder"]], vel_wrist[peaks["wrist"]]],
                            mode='markers', marker=dict(size=10, symbol='star'), name='Overall Peak'
                        ))
                        
                        for idx, frame in enumerate(impact_frames):
                            fig_chain.add_vline(x=frame/fps, line_dash="dot", line_color="gray", opacity=0.5)
                            
                        fig_chain.update_layout(xaxis_title="Time (Seconds)", yaxis_title="Velocity (Relative)", height=350, margin=dict(l=0, r=0, t=30, b=0))
                        st.plotly_chart(fig_chain, use_container_width=True)
                    else:
                        st.warning("데이터가 부족하여 시각화할 수 없습니다.")
else:
    st.info("👈 좌측 사이드바에서 테니스 스윙 영상(.mp4)을 업로드해 주세요.")
