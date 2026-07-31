# 🎾 테니스 센서 스윙 분석 프로젝트 (Tennis Sensor Project)

본 저장소는 **테니스 라켓 버트캡(Butt Cap)에 장착되는 초소형 센서를 통해 사용자의 스윙 데이터를 실시간 수집·분석하고 코칭 피드백을 제공하는 통합 IoT 프로젝트**입니다.

하드웨어(HW) 회로 및 사양, 3D 프린팅 인클로저(Case), MCU 펌웨어(FW), 그리고 안드로이드 모바일 앱(App)까지 프로젝트 전체 자산이 하나의 모노레포(Monorepo) 저장소로 통합 관리됩니다.

---

## 🏗️ 전체 시스템 아키텍처

```text
  [ 라켓 버트캡 센서 단말기 ]
   ├── 하드웨어 : XIAO ESP32-C3 + MPU-6050 IMU 센서 + 602030 LiPo 배터리
   └── 인클로저 : 스냅핏 3D 케이스 (OpenSCAD 설계 v3.2, 3M Dual Lock 마운트)
          │
          │ (BLE 블루투스 실시간 스윙 데이터 송신 @ 50Hz)
          ▼
  [ 분석 및 모니터링 애플리케이션 ]
   ├── Android 앱 (SwingSenseAI) : Kotlin/NDK C++ Edge Impulse AI 추론 & 코칭
   └── 데이터 로거 대시보드      : Streamlit Python BLE 수신기 & 6축 시각화

  [ 비전 기반 보완 분석 (센서리스) ]
   └── tennis-vision-analyzer : 스마트폰 영상 → MediaPipe 관절 포즈 추출 → 스윙 역학 분석
                                (하드웨어 없이 영상만으로 분석, 향후 SwingSenseAI에 통합)
```

---

## 📂 디렉토리 구조 및 파트별 설명

```text
tennis-sensor-project/
├── 🔌 tennis-sensor-hardware/    # 하드웨어 회로, BOM, 핀배치, 실측 사양, v2.0 로드맵
├── ⚙️ tennis-sensor-case/        # 3D 인클로저 설계(OpenSCAD v3.2), STL 파일, 검증 스크립트
├── 💻 tennis-swing-analyzer/     # ESP32_FW (C++ 펌웨어) & data-logger-dashboard (Python)
├── 🎥 tennis-vision-analyzer/    # 스마트폰 영상 기반 비전 AI 스윙 분석 (MediaPipe / Python)
└── 📱 SwingSenseAI/              # 안드로이드 실시간 스윙 분석 앱 (Kotlin / C++ NDK)
```

### 1. 🔌 `tennis-sensor-hardware/` (하드웨어 저장소)
* **[README.md](file:///home/keunu/tennis-sensor-project/tennis-sensor-hardware/README.md)**: 전체 하드웨어 스펙 및 시스템 다이어그램
* **[BOM.md](file:///home/keunu/tennis-sensor-project/tennis-sensor-hardware/BOM.md)**: 사용 부품 명세서 (Seeed XIAO ESP32-C3, GY-521 MPU-6050, 602030 LiPo 배터리 등)
* **[PINOUT_AND_SCHEMATIC.md](file:///home/keunu/tennis-sensor-project/tennis-sensor-hardware/PINOUT_AND_SCHEMATIC.md)**: 핀 연결배치도 (I2C SDA:D4, SCL:D5 등)
* **[MECHANICAL_SPEC.md](file:///home/keunu/tennis-sensor-project/tennis-sensor-hardware/MECHANICAL_SPEC.md)**: 부품 실측 치수 및 Z-Stack 적층 분석
* **[NEXT_GEN_HARDWARE_SPEC_v2.0.md](file:///home/keunu/tennis-sensor-project/tennis-sensor-hardware/NEXT_GEN_HARDWARE_SPEC_v2.0.md)**: 차세대 초슬림(100원 동전 크기 22×24×13mm) v2.0 하드웨어 명세서

### 2. ⚙️ `tennis-sensor-case/` (3D 케이스 저장소)
* **[tennis_sensor_case.scad](file:///home/keunu/tennis-sensor-project/tennis-sensor-case/tennis_sensor_case.scad)**: 초소형 무나사 스냅핏 인클로저 OpenSCAD 설계 소스 (v3.2)
* **3D 프린터 출력 STL 파일들**: `base.stl`, `cover.stl`, `shutter.stl`, `assembly.stl`
* **[verify_geometry.py](file:///home/keunu/tennis-sensor-project/tennis-sensor-case/verify_geometry.py)**: 부품 간섭 및 3D 프린터 제조사 규정(벽두께 > 1.2mm 등) 자동 검증 스크립트

### 3. 💻 `tennis-swing-analyzer/` (펌웨어 & 대시보드 저장소)
* **`ESP32_FW/`**: PlatformIO C++ 펌웨어 (50Hz MPU-6050 센서 데이터 수집 및 BLE 실시간 스트리밍)
* **`data-logger-dashboard/`**: Streamlit 기반 Python BLE 로거, 6축 시각화 대시보드 및 실시간 TTS 피드백
* **`models/`**: 스윙 인식용 Edge Impulse TFLite AI 모델 (.eim)

### 4. 🎥 `tennis-vision-analyzer/` (비전 AI 분석 저장소)
하드웨어 센서 없이 **스마트폰으로 촬영한 영상 1개**만으로 스윙 품질을 분석하는 Python 프로토타입입니다. 알고리즘을 빠르게 검증한 뒤 `SwingSenseAI` 앱에 새 "비전 분석 탭"으로 통합하는 것을 목표로 합니다.
* **[app.py](file:///home/keunu/personal-project/tennis-sensor-project/tennis-vision-analyzer/app.py)**: MP4 영상 업로드 → 분석 결과를 시각화하는 Streamlit 앱
* **`src/pose_extractor.py`**: MediaPipe Pose Landmarker 기반 관절 포즈 추출
* **`src/angle_calculator.py` · `kinetic_chain.py` · `swing_path.py`**: 관절 각도·운동 사슬(Kinetic Chain)·스윙 궤적 정량 분석
* **`src/impact_detector.py`**: 임팩트 시점 검출
* **`src/overlay_renderer.py`**: 원본 영상에 분석 오버레이 렌더링
* **`tests/`**: `test_*.py` 단위 테스트 (`python -m unittest discover tests/`)

### 5. 📱 `SwingSenseAI/` (안드로이드 모바일 앱 저장소)
* **AI 스윙 추론**: NDK C++17 기반 2단계 스윙 분류 (Edge Impulse TFLite Micro 800ms 윈도우 추론)
* **6축 운동학 분석**: Power, Spin, Timing, Fluidity, Stability, Consistency 육각형 점수 계산
* **사이버펑크 UI**: Jetpack Compose 기반 미래지향적 실시간 코칭 UI

---

## ⚙️ 하드웨어 & 3D 케이스 사양 요약

| 항목 | 현재 버전 (v3.2) | 🏆 차세대 버전 (v2.0 로드맵) |
|---|---|---|
| **메인 MCU** | Seeed Studio XIAO ESP32-C3 | **Nordic nRF52840** (초저전력) |
| **IMU 센서** | GY-521 MPU-6050 | **LSM6DS3TR-C** (보드 온보드 통합) |
| **배터리** | 602030 LiPo (300mAh, 6.0mm) | **401520 LiPo (100mAh, 4.0mm)** |
| **외형 크기** | 가로 28.5mm × 세로 36.0mm × **높이 27.5mm** | 가로 22.0mm × 세로 24.0mm × **높이 13.0mm** |
| **마운팅** | 3M Dual Lock 바닥 함몰 마운트 | 3M Dual Lock 바닥 함몰 마운트 |
| **충전/전원** | 온보드 USB-C + 방수 고무 마개 | 온보드 USB-C + 방수 고무 마개 |

---

## 🗺️ 제품 비전 및 단계별 로드맵

본 프로젝트의 최종 목표는 **하드웨어 센서(IMU)와 스마트폰 비전(관절 포즈) 데이터를 결합**하여, 라켓의 미시적 타격과 신체의 거시적 움직임을 함께 분석하는 완결형 스윙 코칭 엔진을 완성하는 것입니다. 이를 위해 각 자산을 독립적으로 검증한 뒤 단계적으로 통합합니다.

### 최종 제품 구조 (SwingSenseAI 통합 목표)

```text
┌─────────────────────────────────────────────────┐
│  SwingSenseAI (Android)                         │
│                                                 │
│  ┌─────────────┐     ┌────────────────────────┐│
│  │ 🔌 센서 탭   │     │ 🎥 비전 분석 탭 (신규)  ││
│  │             │     │                        ││
│  │ BLE 연결    │     │ 카메라 촬영 or          ││
│  │ IMU 데이터  │     │ 갤러리에서 영상 선택     ││
│  │ 실시간 분석  │     │        │               ││
│  │ 6축 점수    │     │        ▼               ││
│  │             │     │ MediaPipe Android SDK   ││
│  │             │     │ 관절 포즈 추출           ││
│  │             │     │        │               ││
│  │             │     │        ▼               ││
│  │             │     │ 스윙 분석 엔진           ││
│  │             │     │ (비전 알고리즘 포팅)     ││
│  │             │     │        │               ││
│  │             │     │        ▼               ││
│  │             │     │ 결과 UI (Compose)       ││
│  │             │     │ • 스켈레톤 오버레이 재생  ││
│  │             │     │ • 관절 각도 그래프       ││
│  │             │     │ • 운동 체인 분석         ││
│  └──────┬──────┘     └───────┬────────────────┘│
│         │                    │                  │
│         └────────┬───────────┘                  │
│                  ▼                              │
│     ┌────────────────────────┐                  │
│     │ 🔗 Fusion 탭 (Phase 3) │                  │
│     │                        │                  │
│     │ 센서 + 비전 동기화 분석 │                  │
│     │ → AI 코칭 리포트 생성   │                  │
│     └────────────────────────┘                  │
└─────────────────────────────────────────────────┘
```

### 단계별 로드맵

| 단계 | 내용 | 상태 |
|---|---|---|
| **Phase 1** | 센서 앱(`SwingSenseAI`)과 비전 프로토타입(`tennis-vision-analyzer`)을 **각각 독립 검증** | 진행 중 |
| **Phase 2** | Phase 1에서 검증된 비전 알고리즘(수학 공식·임계값·타이밍 로직)을 Kotlin으로 포팅하여 **`SwingSenseAI` 앱에 "비전 분석 탭"으로 통합** (Android MediaPipe SDK + Jetpack Compose) | 예정 |
| **Phase 3** | 라켓 버트캡 IMU 센서 데이터와 스마트폰 비전(포즈) 데이터를 타임스탬프 기준으로 동기화하는 **Multi-Modal Sensor Fusion** — 미시적 라켓 타격 + 거시적 몸 움직임 결합 | 예정 |
| **Phase 4** | 분석된 관절 각도·타이밍 데이터를 LLM(Azure OpenAI 등)에 전달해 자연어 기반 개인화 **"AI 코치 피드백" 리포트 자동 생성** | 예정 |

> 각 서브프로젝트의 세부 기획·전략은 해당 프로젝트의 `PROJECT_CHARTER.md`를, feature 단위 구현 명세는 [`docs/specs/`](file:///home/keunu/personal-project/tennis-sensor-project/docs/specs/)를 참조하세요.

---

## 🤖 에이전트 기반 개발 워크플로우

본 저장소는 **PM · Developer · Tester 세 개의 AI 에이전트**가 파일 기반 핸드오프로 협업하여 기능을 개발하는 자동화 파이프라인을 갖추고 있습니다. task 등록부터 명세 작성, 구현, QA, 커밋까지의 상태 머신과 각 에이전트의 역할은 아래 문서를 참조하세요.

* **[docs/AGENT_WORKFLOW.md](file:///home/keunu/personal-project/tennis-sensor-project/docs/AGENT_WORKFLOW.md)**: 에이전트 협업 워크플로우 전체 설명
* **[prompts/](file:///home/keunu/personal-project/tennis-sensor-project/prompts/)**: 각 에이전트(PM/Developer/Tester) 지시문

---

## 📜 라이선스

본 프로젝트는 개인 및 학습 목적으로 제작된 오픈소스 테니스 센서 프로젝트입니다.
