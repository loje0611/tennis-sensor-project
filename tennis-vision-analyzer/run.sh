#!/bin/bash

# 프로젝트 최상위 디렉토리로 이동 (스크립트 실행 위치와 무관하게 동작)
cd "$(dirname "$0")"

echo "=========================================="
echo "🎾 테니스 비전 AI 스윙 분석기 시작"
echo "=========================================="

# 1. 가상환경 활성화 확인
if [ -d ".venv" ]; then
    echo "[1/2] 가상환경 활성화 중..."
    source .venv/bin/activate
else
    echo "❌ [오류] '.venv' 가상환경 디렉토리를 찾을 수 없습니다."
    echo "프로젝트 초기 설정(설치)이 올바르게 되었는지 확인해 주세요."
    exit 1
fi

# 2. Streamlit 서버 실행
echo "[2/2] Streamlit 서버를 실행합니다..."
echo "서버를 종료하려면 터미널에서 'Ctrl + C'를 누르세요."
echo ""

streamlit run app.py
