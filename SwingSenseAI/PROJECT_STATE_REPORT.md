# SwingSenseAI - Project State Report

> **작성일**: 2026-04-21  
> **앱 버전**: v1.0 (targetSdk 36, compileSdk 36r1)  
> **목적**: PM 대상 현재 개발 진척도 공유

---

## 프로젝트 개요

| 항목 | 값 |
|---|---|
| Kotlin 소스 파일 | 53개 |
| 유닛/통합 테스트 파일 | 7개 |
| 분류 가능 구종 | 6 실전 (AI 4종 + 물리 2종) + 1 Idle |
| 앱 버전 | v1.0 |

---

## 1. Tech Stack & Architecture

MVVM 아키텍처 기반의 **100% Kotlin** 프로젝트. UI는 **Jetpack Compose**, ML 추론은 **NDK(C++17) Edge Impulse TFLite Micro**를 사용한다.

### 1.1 핵심 라이브러리

| 영역 | 라이브러리 | 버전 |
|---|---|---|
| UI | Jetpack Compose (BOM) | 2026.03.00 |
| UI | Material 3 + Material Icons Extended | BOM 연동 |
| UI | Navigation Compose | 2.9.7 |
| 상태관리 | Lifecycle ViewModel Compose | 2.10.0 |
| 상태관리 | Lifecycle Runtime Compose | 2.10.0 |
| 비동기 | Kotlinx Coroutines Android | 1.10.2 |
| DB | Room (Runtime + KTX + Compiler via KSP) | 2.8.4 |
| ML/추론 | Edge Impulse SDK (C++ NDK, TFLite Micro) | 네이티브 |
| BLE | Android Bluetooth API (직접 구현) | - |
| 권한 | Accompanist Permissions | 0.37.3 |
| 빌드 | AGP 9.1.1 / Kotlin 2.2.10 / KSP 2.3.2 | - |

### 1.2 아키텍처 구조

| 레이어 | 구성 요소 | 설명 |
|---|---|---|
| Presentation | PracticeScreen, HistoryScreen, SettingsScreen, SessionDetailScreen, DeveloperSettingsScreen | Compose UI + ViewModel |
| ViewModel | MainViewModel, HistoryViewModel, SettingsViewModel, SessionDetailViewModel, DeveloperSettingsViewModel | StateFlow 기반 상태 관리 |
| Service | SwingAnalysisForegroundService | BLE 수신 + 추론 파이프라인 운영 |
| Analysis | SwingInferenceBuffer, VolleyDetector, KinematicAnalyzer, CoachingEngine | Two-Stage 분류 + 운동학 분석 |
| Data | Room DB (SwingSenseDatabase v6), CalibrationStore (DataStore) | 로컬 영구 저장, 캘리브레이션/파라미터 설정 |
| Hardware | BleManager | BLE 스캔/연결/재연결/알림 수신 |
| Native | ei_jni_bridge.cpp + Edge Impulse SDK | JNI 경유 TFLite Micro 추론 |

---

## 2. Database Schema (데이터 모델)

- **ORM**: Room v2.8.4
- **저장 위치**: 로컬 전용 (`swingsense.db`)
- **클라우드 연동**: Firebase 등 **미구현**
- **DB 버전**: 6 (exportSchema = true)
- **Entity 수**: 4개 / **DAO 수**: 2개

### 2.1 `swing_sessions` (세션 — 1:N 부모)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `sessionId` | String | @PrimaryKey (UUID) |
| `sessionName` | String | 세션 이름 |
| `startTime` | Long | 시작 타임스탬프 |
| `endTime` | Long? | 종료 타임스탬프 (nullable) |
| `totalSwingCount` | Int | 기본값 0 |
| `durationMillis` | Long | 세션 지속 시간 |
| `forehandVolleyCount` | Int | 포핸드 발리 횟수 |
| `backhandVolleyCount` | Int | 백핸드 발리 횟수 |

### 2.2 `swing_events` (스윙 이벤트 — N:1 자식)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | Long | @PrimaryKey(autoGenerate) |
| `sessionId` | String | FK → swing_sessions (CASCADE) |
| `categoryKey` | String | 구종 키 |
| `timestampMillis` | Long | 이벤트 타임스탬프 |
| `power` | Int | 0-100 |
| `spin` | Int | 0-100 |
| `timing` | Int | 0-100 |
| `fluidity` | Int | 0-100 |
| `stability` | Int | 0-100 |
| `consistency` | Int | 0-100 |

### 2.3 `session_swing_counts` (구종별 집계 — N:1)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `sessionId` | String | 복합 PK 1 / FK → swing_sessions |
| `categoryKey` | String | 복합 PK 2 |
| `count` | Int | 해당 구종 스윙 횟수 |

### 2.4 `global_statistics` (전역 누적 통계 — 독립)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `categoryKey` | String | @PrimaryKey |
| `count` | Long | 누적 스윙 횟수 |
| `avgPower` | Double | 누적 이동 평균 |
| `avgSpin` | Double | 누적 이동 평균 |
| `avgTiming` | Double | 누적 이동 평균 |
| `avgFluidity` | Double | 누적 이동 평균 |
| `avgStability` | Double | 누적 이동 평균 |
| `avgConsistency` | Double | 누적 이동 평균 |

> 모든 데이터는 **로컬 Room DB에만 저장**됨. 클라우드(Firebase 등) 연동은 현재 미구현 상태.

---

## 3. Data Pipeline & ML

### 3.1 센서 데이터 사양

| 항목 | 값 |
|---|---|
| 샘플링 주파수 | **50 Hz** |
| IMU 축 | **6축** (ax, ay, az, gx, gy, gz) |
| 추론 윈도 | **800ms** (40샘플) |
| 추론 쿨다운 | **1,500ms** |

### 3.2 버퍼 구조

| 버퍼 | 크기 | 용도 |
|---|---|---|
| `SwingInferenceBuffer` | 40샘플 (800ms) | 슬라이딩 윈도 + Two-Stage 추론 + 1.5초 쿨다운 |
| `SwingKinematicsBuffer` | 100샘플 (2000ms) | 운동학 분석 전용 링 버퍼 (추론과 독립 운영) |
| `flatScratch` | 240 floats | GC 최소화용 재사용 배열 |

### 3.3 Two-Stage 분류 아키텍처

#### Stage 1: VolleyDetector (물리 기반 게이트키퍼)

JNI 호출 없이 물리 휴리스틱만으로 **발리를 선별**하는 전처리 단계.

| 판별 기준 | 임계값 | 설명 |
|---|---|---|
| 임팩트 가속도 | `ACCEL_THRESHOLD_SQ = 600` | 2.5g² 이상의 충격 감지 |
| 최소 임팩트 강도 | `MIN_IMPACT_ACCEL_SQ = 200` | 너무 약한 동작 제외 |
| 임팩트 지속시간 | `MAX_VOLLEY_DURATION_MS = 280ms` | 짧은 충격 = 발리 특성 |
| Follow-through 회전 | `GYRO_FOLLOW_THROUGH_THRESHOLD_SQ = 40000` | 발리는 라켓 정지 → 낮은 회전 |
| 포핸드/백핸드 구분 | Gyro Z축 부호 | 양수 = 포핸드, 음수 = 백핸드 |

**출력**: `Forehand_Volley` 또는 `Backhand_Volley` (발리 확정 시 JNI 스킵, 즉시 라벨 반환)

#### Stage 2: Edge Impulse TFLite Micro (AI 모델)

Stage 1에서 스트로크(null)로 판정된 경우에만 **JNI 경유 네이티브 추론** 수행.

| 속성 | 값 |
|---|---|
| 모델 파일 | `tflite_learn_909575_8.cpp` |
| 입력 크기 | 240 floats (40 samples x 6 axes) |
| DSP | Spectral Analysis (FFT 64, 6축) |
| 출력 라벨 수 | 5개 (`EI_CLASSIFIER_LABEL_COUNT`) |
| 라벨 목록 | `Backhand_Slice`, `Backhand_Topspin`, `Forehand_Slice`, `Forehand_Topspin`, `Idle` |

### 3.4 최종 분류 가능 구종: 총 7개 (6 실전 + 1 Idle)

| 구종 | 분류 단계 | 방식 |
|---|---|---|
| Forehand Volley | Stage 1 | 물리 휴리스틱 |
| Backhand Volley | Stage 1 | 물리 휴리스틱 |
| Forehand Topspin | Stage 2 | AI 모델 (TFLite) |
| Forehand Slice | Stage 2 | AI 모델 (TFLite) |
| Backhand Topspin | Stage 2 | AI 모델 (TFLite) |
| Backhand Slice | Stage 2 | AI 모델 (TFLite) |
| Idle | Stage 2 | AI 모델 (무동작 필터) |

### 3.5 운동학 분석 (KinematicAnalyzer)

분류 성공 시 별도 **2초 버퍼**(SwingKinematicsBuffer)에서 **6개 지표(0-100)**를 산출한다. 피크(임팩트) 인덱스 기준 **±30샘플(600ms)** 대칭 윈도를 잘라내어 분석.

| 지표 | 알고리즘 요약 |
|---|---|
| **Power** | 피크 윈도 내 최대 가속도 벡터합 → min-max 정규화 (0~40 g) |
| **Spin** | 피크 윈도 내 최대 \|Gyro Z\| → min-max 정규화 (0~2000) |
| **Timing** | 가속도가 평균x1.5 초과 시점 ~ 피크 시점 간 시간차(ms) → 역정규화 |
| **Fluidity** | 가속도 jerk(미분) 분산 → 역정규화 (낮을수록 고득점) |
| **Stability** | 피크 ±5샘플 구간 Gyro X/Y 표준편차 평균 → 역정규화 |
| **Consistency** | 피크 전후 대칭 구간 Pearson 상관계수 → 0-100 매핑 |

### 3.6 코칭 엔진 (CoachingEngine)

휴리스틱 규칙 기반의 코칭 코멘트 생성기.

- **Part A** (비교 분석): 과거 글로벌 평균 대비 현재 세션 지표 변화를 분석 (파워 15%+ 향상, 안정성 15%+ 하락, 타이밍 밀림, 일관성 향상 등)
- **Part B** (구종별 절대 폼 분석): 구종(Topspin/Slice/Volley)에 특화된 폼 진단 코멘트 (한국어)
- **출력**: `"[Part A 코멘트] [Part B 코멘트]"` 형태의 조합 문자열

---

## 4. Bluetooth & Hardware 연결 상태

### 4.1 BLE 연결 사양

| 항목 | 값 |
|---|---|
| 대상 디바이스 이름 | `Tennis_Sensor_V1` |
| Service UUID | `4fafc201-1fb5-459e-8fcc-c5c9c331914b` |
| Characteristic UUID | `beb5483e-36e1-4688-b7f5-ea07361b26a8` |
| 프로토콜 | BLE Notification (UTF-8 문자열) |
| MTU | 512 |
| 데이터 포맷 | `"ax,ay,az,gx,gy,gz"` (6개 float, 콤마 구분) |

### 4.2 연결 생명주기

| 단계 | 타임아웃/설정 | 동작 |
|---|---|---|
| 스캔 | 15,000ms | 디바이스 이름 매칭으로 필터링 |
| 연결 | 10,000ms | GATT 연결 시도 |
| 재연결 | 최대 5회 | 지수 백오프 (2초 ~ 15초) |
| 알림 수신 | - | UTF-8 디코딩 → ImuPayloadParser → 6축 FloatArray |
| 오류 처리 | sealed class | BluetoothOff, PermissionDenied, ScanFailed, ConnectionTimeout, MaxReconnectReached |

### 4.3 데이터 파싱 (ImuPayloadParser)

- BLE Notification의 `ByteArray`를 **UTF-8 문자열**로 디코딩
- 콤마(`,`)로 분리하여 **6개 float**를 파싱
- `"ERR:"` 접두사 또는 빈 문자열은 `null` 반환
- 축 수가 6이 아니거나, `toFloatOrNull()` 실패 시 `null` 반환

> ESP32(Arduino) 측에서 센서 데이터를 50Hz로 BLE Notify하는 구조.

---

## 5. UI / UX 현황

### 5.1 화면 구성 (5개 라우트/화면)

| 화면 | 라우트 | 구현 상태 | 주요 기능 |
|---|---|---|---|
| Practice (메인) | `practice` | **완료** | BLE 연결/해제, 실시간 스윙 라벨 표시, 세션 타이머, 구종별 카운트, 사이버펑크 UI |
| History | `history` | **완료** | 세션 목록 카드, 세션 상세 진입, Mock 데이터 FAB (DEBUG 빌드만) |
| Settings | `settings` | **완료** | 다크모드 토글, 센서 영점 조절(Calibration) 다이얼로그, 개발자/엔지니어 모드 진입점 |
| Session Detail | `session_detail/{id}` | **완료** | 구종별 메트릭스, 분석 바텀시트, 레이더 차트, 델타 요약 칩, 세션 삭제 |
| Developer Settings| `engineering_mode` | **완료** | Engineering Mode: Mock BLE 시뮬레이터, 파라미터 튜닝, CSV 익스포트, 라이브 디버그 콘솔 |

### 5.2 주요 UI 컴포넌트

| 컴포넌트 | 위치 | 설명 |
|---|---|---|
| `HexagonalRadarChart` | SessionDetailScreen | 6축 지표를 육각형 레이더 차트로 시각화 (Canvas 드로잉) |
| `AnalysisBottomSheet` | SessionDetailScreen | ModalBottomSheet + HorizontalPager로 구종별 상세 분석 |
| `DeltaSummaryChips` | SessionDetailScreen | 과거 대비 변화량을 컬러 칩으로 표시 |
| `NeonProgressBar` | SessionDetailScreen | 6개 지표별 네온 스타일 프로그레스 바 |
| `CyberpunkBackground` | PracticeScreen | 사이버펑크 테마 배경 |
| `MinimalSwingHero` | PracticeScreen | 현재 스윙 라벨 대형 표시 영역 |

### 5.3 테마 시스템

- **다크 모드 / 라이트 모드** 지원 (토글 전환)
- `SwingColorScheme` 커스텀 토큰 시스템: 기본 색상 + `danger`, `success`, `warningChipBg/Fg`, `successChipBg/Fg` 등
- `SharedPreferences` 기반 테마 저장 (`ThemePreferencesRepository`)
- Edge-to-Edge + 동적 `SystemBarStyle` 적용 완료

---

## 6. Technical Debt & TODOs

코드 내 명시적 `TODO` / `FIXME` 주석은 **0건**. 아래는 코드 분석 기반 잠재적 기술 부채 항목.

### 6.1 높은 우선순위

| 항목 | 위치 | 설명 |
|---|---|---|
| Gyro Z축 부호 기반 포핸드/백핸드 판별 | `VolleyDetector.classifyVolleyHand()` | 센서 부착 방향에 따라 부호 반전 가능. 코드 주석에도 **"실제 배포 전 부호/축 수정 필요"** 명시됨 |
| 클라우드 동기화 미구현 | 전체 | Firebase/서버 연동 없음. 데이터는 로컬에만 존재. 기기 분실 시 데이터 유실 위험 |

### 6.2 중간 우선순위

| 항목 | 위치 | 설명 |
|---|---|---|
| CoachingEngine 한국어만 지원 | `CoachingEngine.kt` | 코칭 코멘트가 한국어 하드코딩. 다국어 지원 시 리소스 분리 필요 |
| DB 마이그레이션 전략 부재 | `SwingSenseDatabase` | version=6이지만 Migration 객체가 없음. `fallbackToDestructiveMigration` 의존 가능성 |
| DI 프레임워크 미사용 | 전체 | Hilt/Koin 없이 수동 의존성 주입. `SwingSenseApplication`에서 직접 생성. 규모 확대 시 관리 복잡도 증가 |
| Edge Impulse 모델 4구종 제한 | `model_variables.h` | AI 모델이 Topspin/Slice만 분류. Flat 등 추가 구종 인식 불가 |
| applicationId가 예제 네임스페이스 | `build.gradle.kts` | `com.example.swingsenseai` → 스토어 배포 전 실제 도메인으로 변경 필요 |

### 6.3 낮은 우선순위

| 항목 | 설명 |
|---|---|
| UI 테스트 부족 | Compose UI 테스트가 없음. 핵심 로직 유닛 테스트는 7개 파일 존재 |
| Proguard 규칙 최소 | 기본 `proguard-android-optimize.txt`만 사용. JNI/Room에 대한 커스텀 keep 규칙 확인 필요 |
| 접근성(A11y) 부분 적용 | 일부 `contentDescription` 추가됨. 전체 커버리지는 미확인 |

---

## 종합 요약

### 완료된 핵심 기능

- BLE 센서 연결 + 자동 재연결 (지수 백오프, 최대 5회)
- Two-Stage 스윙 분류 (물리 발리 2종 + AI 4구종)
- 6축 운동학 분석 (Power, Spin, Timing, Fluidity, Stability, Consistency)
- 휴리스틱 코칭 코멘트 자동 생성
- 세션 기록/조회/삭제 (Room DB)
- 레이더 차트 + 바텀시트 상세 분석 UI
- 다크/라이트 테마 + Edge-to-Edge
- Foreground Service 기반 백그라운드 분석

### 미구현 / 향후 과제

- 클라우드 동기화 (Firebase / 서버)
- 다국어 코칭 코멘트
- Flat / Kick Serve 등 추가 구종
- DI 프레임워크 (Hilt / Koin)
- DB Migration 전략
- Compose UI 테스트
- 센서 부착 방향별 축 보정 자동화

---

*Generated from codebase analysis on 2026-04-18. SwingSenseAI v1.0 (targetSdk 36, compileSdk 36r1).*
