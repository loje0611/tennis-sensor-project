# 📌 Pinout & Schematic - 핀배치 및 회로 스키매틱

본 문서는 테니스 센서 프로젝트의 **TENNIS SENSOR COMPLETE SCHEMATIC (종합 회로 스키매틱)** 및 배선 사양입니다. (분압 회로 제외)

---

## 🖼️ 테니스 센서 종합 회로 스키매틱 (Complete Schematic Diagram)

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

## 🔌 상세 핀배치 및 결선 명세 (Pinout Table)

| 구문 (Component) | 핀 명칭 (Pin) | 결선 대상 (Connected To) | 역할 및 신호 설명 |
|---|---|---|---|
| **MPU6050** | VCC | XIAO ESP32C3 `3.3V` | 3.3V DC 센서 전원 입력 |
| **MPU6050** | GND | XIAO ESP32C3 `GND` | 공통 그라운드 (Common Ground) |
| **MPU6050** | SDA | XIAO ESP32C3 `D4/SDA` (GPIO6) | I2C 데이터 신호선 (400kHz Fast Mode) |
| **MPU6050** | SCL | XIAO ESP32C3 `D5/SCL` (GPIO7) | I2C 클럭 신호선 (400kHz Fast Mode) |
| **전원 스위치** | MINI SLIDE SWITCH | LiPo (+) ↔ XIAO `BAT+` | 배터리 메인 양극 전원 온/오프 스위치 |
| **배터리** | LiPo 3.7V 300mAh (-) | XIAO `BAT-` | 배터리 음극 직결 그라운드 |

---

## 📡 센서 레지스터 설정 사양 (Firmware Settings)

- **I2C Bus Clock**: `400 kHz` (Fast Mode)
- **I2C Target Address**: `0x68`
- **Accelerometer Range**: `±16g` (`REG_ACCEL_CONFIG` = 0x18, Scale = 2048 LSB/g)
- **Gyroscope Range**: `±2000 dps` (`REG_GYRO_CONFIG` = 0x18, Scale = 16.4 LSB/dps)
- **Sampling Rate**: `50 Hz` (20ms)

---

## 🧭 센서 부착 좌표축 정렬 (Axis Orientation)

- **Sensor Y-Axis (`+Y`)**: 라켓 자루(Handle) 및 헤드(Head)를 향하는 전방 방향
- **Sensor X-Axis (`+X`)**: 라켓면의 측면 방향
- **Sensor Z-Axis (`+Z`)**: 라켓면 수직(버트캡 밖) 방향
