# AI Context for Tennis Swing Analyzer

이 파일은 AI 에이전트(Developer, Tester 등)가 이 프로젝트에서 작업할 때 필요한 환경 및 명령어 정보를 제공하기 위한 파일입니다.

> ⚠️ 이 서브프로젝트는 **여러 컴포넌트**로 구성됩니다. 작업 대상 컴포넌트를 먼저 식별한 뒤, 해당 컴포넌트의 명령어를 사용하세요.
> - `ESP32_FW/` — Seeed XIAO ESP32-C3 펌웨어 (PlatformIO / Arduino)
> - `data-logger-dashboard/` — Python(Streamlit) 데이터 로거 & 대시보드
> - `smart-network-switcher/` — Python 네트워크 전환 유틸리티 (systemd 서비스)

## 1. Project Environment
- **펌웨어**: C++ / Arduino, PlatformIO (`platformio.ini`, env `seeed_xiao_esp32c3`)
- **대시보드/유틸리티**: Python 3 (가상환경 `venv/` 사용, 커밋되지 않음)

## 2. Execution Commands
각 명령은 해당 컴포넌트 디렉토리 안에서 실행합니다.

- **ESP32 펌웨어 (Tester Agent용 테스트)** — `ESP32_FW/`:
  ```bash
  pio test          # 유닛 테스트 (test/ 디렉토리)
  pio run           # 빌드
  pio run -t upload # 기기 업로드 (하드웨어 연결 시)
  ```
  > 참고: 실기기가 없으면 `pio run`(빌드 성공) 결과로 검증합니다.

- **데이터 로거 대시보드** — `data-logger-dashboard/`:
  ```bash
  python3 -m venv venv && ./venv/bin/pip install -r requirements.txt   # 최초 1회
  ./venv/bin/python3 -m pytest tests/                                  # 테스트
  ./run_logger.sh                                                       # 실행 (streamlit)
  ```

- **네트워크 스위처** — `smart-network-switcher/`:
  ```bash
  python3 wifi-switcher.py
  ```

## 3. Rules & Conventions
- Python 컴포넌트는 반드시 컴포넌트별 `venv`를 사용하고, 시스템 전역 Python을 사용하지 마세요.
- 새 테스트는 각 컴포넌트의 규칙을 따릅니다: 펌웨어는 `ESP32_FW/test/`, Python은 해당 컴포넌트의 `tests/`에 `test_*.py`로 작성합니다.
- `.pio/`, `venv/`, `__pycache__/`는 커밋 대상이 아닙니다(루트 `.gitignore` 참조).
