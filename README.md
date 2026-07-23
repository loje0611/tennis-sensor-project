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
```

---

## 📂 디렉토리 구조 및 파트별 설명

```text
tennis-sensor-project/
├── 🔌 tennis-sensor-hardware/    # 하드웨어 회로, BOM, 핀배치, 실측 사양, v2.0 로드맵
├── ⚙️ tennis-sensor-case/        # 3D 인클로저 설계(OpenSCAD v3.2), STL 파일, 검증 스크립트
├── 💻 tennis-swing-analyzer/     # ESP32_FW (C++ 펌웨어) & data-logger-dashboard (Python)
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

### 4. 📱 `SwingSenseAI/` (안드로이드 모바일 앱 저장소)
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

## 📜 라이선스

본 프로젝트는 개인 및 학습 목적으로 제작된 오픈소스 테니스 센서 프로젝트입니다.
