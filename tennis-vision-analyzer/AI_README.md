# AI Context for Tennis Vision Analyzer

이 파일은 AI 에이전트(Developer, Tester 등)가 이 프로젝트에서 작업할 때 필요한 환경 및 명령어 정보를 제공하기 위한 파일입니다.

## 1. Project Environment
- **언어 및 런타임**: Python (가상환경 사용)
- **가상환경 디렉토리**: `.venv`
- **주요 라이브러리**: MediaPipe, OpenCV, Streamlit, Plotly, SciPy

## 2. Execution Commands
에이전트가 터미널 명령어를 실행할 때는 반드시 아래의 명령어를 기준 삼아 실행해야 합니다.

- **가상환경 활성화 및 테스트 실행 (Tester Agent용)**:
  ```bash
  source .venv/bin/activate && python -m unittest discover tests/
  ```
  (또는 특정 파일 테스트 시 `python -m unittest tests/test_파일명.py`)

- **애플리케이션 실행**:
  ```bash
  ./run.sh
  ```
  (내부적으로 `streamlit run app.py`를 실행함)

## 3. Rules & Conventions
- 모든 파이썬 스크립트나 모듈 단위 테스트를 실행하기 전에는 반드시 `source .venv/bin/activate`를 통해 가상환경을 활성화해야 합니다.
- 시스템 전역 파이썬(System Python)을 사용하여 의존성 오류가 발생하지 않도록 주의하세요.
- 새로 작성된 테스트 코드는 `tests/` 디렉토리 내에 `test_*.py` 명명 규칙을 따라 저장해야 합니다.
