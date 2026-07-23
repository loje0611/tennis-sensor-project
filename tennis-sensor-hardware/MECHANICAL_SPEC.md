# 📐 Mechanical Specification - 메카니컬 사양 및 3D 하우징 구조

본 문서는 Seeed Studio XIAO ESP32-C3 MCU, GY-521 MPU-6050 센서 모듈 및 **602030 3.7V 300mAh LiPo 배터리** 기반 테니스 라켓 버트캡 장착용 하드웨어 인클로저(Case)의 실측 치수, 내부 적층(Z-Stack) 구조, 공차 및 케이스 설계 파라미터를 정의합니다.

---

## 🔬 하드웨어 부품 실측 치수 및 케이스 여유공간 (Clearance Analysis)

| 구분 | MCU (XIAO ESP32-C3) | IMU 센서 (GY-521) | 배터리 (**602030 LiPo 300mAh**) | 케이스 설계 치수 (SCAD Envelope) | 공차 및 설계 반영 사항 |
|---|---|---|---|---|---|
| **길이 (Y축)** | **21.0 mm** | **21.0 mm** | **30.0 mm** (PCM포함 ~32.0mm) | **33.0 mm** (`bat_l` / `inner_l`) | +1.0mm 여유 (전선 인출 및 방열 유격) |
| **너비 (X축)** | **17.5 mm** | **15.6 mm** | **20.0 mm** | **20.0~22.0 mm** (`bat_w` / `inner_w`)| 맞춤 폭 안착 (옆면 벽면 가이드) |
| **두께 (Z축)** | **3.5 mm** | **3.0 mm** | **6.0 mm** | **6.5 mm** (`bat_h` / `base_cavity_h`) | +0.5mm 두께 스웰링(Swelling) 공차 |
| **적층 위치** | 상단 보드 쉘프 위 | 상단 보드 쉘프 위 | **하단 베이스 카비티 내부** | Base 9.0mm / Cover 17.5mm | 배터리와 회로부 물리적격리 파티션 |

---

## 📏 하우징 외형 및 주요 치수 (Overall Housing Dimensions)

- **전체 외형 치수**: `28.5mm` (가로 X) × `36.0mm` (세로 Y) × `26.5mm` (높이 Z)
  - 하단 베이스 (Base): 28.5mm × 36.0mm × 9.0mm (내부 배터리 카비티 20x33x6.5mm + 바닥 2.5mm)
  - 상단 커버 (Cover): 28.5mm × 36.0mm × 17.5mm (내부 보드 적층 12.0mm + 쉘프 2.0mm + 갭 2.0mm + 천장 1.5mm)
- **전체 중량 (Estimated Weight)**: 602030 배터리(약 6g), MCU(1.8g), MPU-6050(2.1g) 및 케이스 포함 약 **18 ~ 21g**
- **외벽 두께 (Wall Thickness)**: 1.5mm (모서리 R1.5mm 라운딩 처리)
- **결합 방식**: 무나사 스냅핏 (Screwless Snap-fit v3.1, Lip height 3.0mm, Detent bump 0.3mm)

---

## 🧱 내부 적층 구조 (Z-Stack Diagram)

```text
+-------------------------------------------------------------+  ▲ 26.5mm (Cover Top)
|              Cover Ceiling (1.5mm)                          |
+-------------------------------------------------------------+
|              Air Gap & Antenna Pocket (2.0mm + 0.5mm)       |
+-------------------------------------------------------------+
|              MPU-6050 Sensor Board (3.0mm)                  |
|              Wiring / Spacer Interconnect (5.5mm)           |
|              Seeed XIAO ESP32-C3 MCU Board (3.5mm)          |
+-------------------------------------------------------------+  ▲ 9.0mm (Shelf Level)
|              Board Shelf / Partition Wall (1.0mm)           |
+-------------------------------------------------------------+
|              602030 LiPo Battery 300mAh Cavity (6.5mm)      |
+-------------------------------------------------------------+
|              Base Floor (2.5mm)                             |
|  [ Bottom Recess: 3M Dual Lock Pocket (24.5 x 20.0 x 2.0) ] |
+-------------------------------------------------------------+  ▼ 0.0mm (Bottom)
```

---

## 🔌 인터페이스 개구부 (Ports & Openings)

1. **USB Type-C 충전 포트**:
   - 위치: 앞쪽 벽 (-Y 방향)
   - 치수: 폭 **9.5mm** × 높이 **3.5mm** (XIAO ESP32-C3 온보드 USB-C 규격 맞춤)
   - 위치 높이: Shelf 기준 +1.0mm (`usbc_z = shelf_z + 1.0`)
2. **전원 토글 스위치 베이**:
   - 위치: 뒤쪽 벽 (+Y 방향)
   - 치수: 폭 **6.0mm** × 높이 **4.0mm**

---

## 🏸 버트캡 마운팅 포켓 (3M Dual Lock Pocket)

- **위치**: Base 하우징 바닥면 외부 음각 리세스
- **치수**: 가로 24.5mm × 세로 20.0mm × 깊이 2.0mm
- **용도**: 3M Dual Lock(SJ3550/SJ3551) 패브릭 테이프를 안착시켜 테니스 라켓의 버트캡(Butt Cap) 하단에 강한 진동에도 이탈 없이 결합.
