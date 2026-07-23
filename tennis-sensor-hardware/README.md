# 🔌 테니스 센서 하드웨어 (Tennis Sensor Hardware)

테니스 라켓 버트캡에 장착되는 스윙 분석 센서의 하드웨어 부품 구성, 제품 실측 치수, 회로 스키매틱(Schematic), 부품 목록(BOM) 및 메카니컬 사양을 보관하는 저장소입니다.

---

## 🖼️ 종합 회로 스키매틱 (Complete Schematic Diagram)

![Tennis Sensor Complete Schematic](file:///home/keunu/tennis-sensor-project/tennis-sensor-hardware/schematic.png)

```text
========================================================================================================
 TENNIS SENSOR COMPLETE SCHEMATIC
========================================================================================================

    +-------------------+                                         +--------------------------+
    | MPU6050 (GY-521)  |                                         |   SEEED XIAO ESP32C3     |
    |                   | VCC ----------------------------------- | 3.3V                     |
    |                   | GND ----------------------------------- | GND                     o)))
    |                   | SDA ----------------------------------- | D4/SDA (GPIO6)   Antenna |
    |                   | SCL ----------------------------------- | D5/SCL (GPIO7)           |
    +-------------------+                                         |                          |
                                                                  | (UNDERSIDE)              |
                                         +-------/  o------------ | BAT+                     |
                                                MINI SLIDE SWITCH |                          |
    +-------------------+                |                        |                          |
    | 3.7V LiPo BATTERY | (+) [RED] -----+                        |                          |
    |    (300mAh)       | (-) [BLK] ----------------------------- | BAT-                     |
    +-------------------+                                         +--------------------------+
========================================================================================================
```

---

## 📁 디렉토리 구조 및 주요 문서

- [BOM.md](file:///home/keunu/tennis-sensor-project/tennis-sensor-hardware/BOM.md) : 부품 목록 (Bill of Materials)
- [PINOUT_AND_SCHEMATIC.md](file:///home/keunu/tennis-sensor-project/tennis-sensor-hardware/PINOUT_AND_SCHEMATIC.md) : `schematic.png` 회로도 및 상세 결선 가이드
- [MECHANICAL_SPEC.md](file:///home/keunu/tennis-sensor-project/tennis-sensor-hardware/MECHANICAL_SPEC.md) : MCU, IMU 센서 및 602030 배터리 실측 크기 분석, 적층 구조, 무게, 버트캡 결합 포켓 사양

---

## ⚡ 주요 하드웨어 핀배치 및 결선 요약

| 연결 전원 / 신호 | 소스 (Source Pin) | 타겟 (Target Pin) | 비고 및 역할 |
|---|---|---|---|
| **센서 전원 (VCC)** | ESP32-C3 `3.3V` | MPU-6050 `VCC` | 3.3V 정류 전원 공급 |
| **그라운드 (GND)** | ESP32-C3 `GND` | MPU-6050 `GND` | 공통 그라운드 (Common Ground) |
| **I2C 데이터 (SDA)** | ESP32-C3 `D4/SDA` (GPIO6) | MPU-6050 `SDA` | I2C 데이터 라인 (400kHz Fast Mode) |
| **I2C 클럭 (SCL)** | ESP32-C3 `D5/SCL` (GPIO7) | MPU-6050 `SCL` | I2C 클럭 라인 (400kHz Fast Mode) |
| **전원 스위치** | LiPo (+) | Slide Switch ──► ESP32 `BAT+` | 메인 양극 전원 온/오프 |
| **배터리 음극** | LiPo (-) | ESP32 `BAT-` | 배터리 음극 직결 |
