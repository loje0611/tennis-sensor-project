# TASK-011: `:core:sensor` 모듈 추출 (BLE 연결 · IMU 페이로드 파싱)

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-07 | PM | 최초 작성 |

---

## 1. Overview & Scope

### 1.1 목적
TennisDocAI 앱의 **BLE 센서 통신 계층**을 `:app` 모듈에서 분리하여 독립 Android 라이브러리 모듈 `:core:sensor`로 이전한다.

이 모듈은 "테니스 라켓에 부착된 IMU 센서 하드웨어와 통신하고, 수신한 바이트 스트림을 6축 부동소수점 프레임으로 변환한다"는 **단일 책임**을 갖는다. 센서 데이터를 *해석*하는 일(스윙 분류, 운동학 계산)은 이 모듈의 책임이 아니다.

### 1.2 아키텍처 제약 (필독)
프로젝트 루트 `build.gradle.kts`의 `verifyModuleDependencies` 태스크는 모듈 간 의존 방향을 **강제**한다. 해당 규칙에서 `:core:sensor`에 허용된 모듈 의존성은 **빈 집합(`emptySet()`)** 이다.

```
":core:sensor"   to emptySet()             // 다른 어떤 모듈에도 의존 불가
":core:analysis" to setOf(":core:sensor")  // analysis가 sensor에 의존 (역방향 아님)
```

**따라서 `:core:sensor`는 `:core:analysis`를 포함한 어떤 프로젝트 모듈도 참조할 수 없다.** 이는 본 작업의 가장 중요한 설계 제약이며, 아래 FR-3(상수 소유권 이전)과 FR-6(범위 제외)의 근거다.

의존 방향의 의미: **센서는 분석을 모른다. 분석이 센서 데이터 규격을 안다.**

### 1.3 In Scope / Out of Scope

| 구분 | 항목 |
|---|---|
| **In Scope** | BLE 연결 관리(`BleManager`), 연결 상태 모델(`BleConnectionState`), 센서 추상화 인터페이스(`SensorDataSource`)와 두 구현체(`RealBleDataSource`, `MockBleDataSource`), IMU 페이로드 파서(`ImuPayloadParser`), IMU 프레임 규격 상수, BLE 관련 권한 선언 |
| **Out of Scope** | `MockSwingDataGenerator` 이전 (FR-6 참조) · 추론/분류 로직 · Room·DataStore 등 영속화 · UI·ViewModel · 포그라운드 서비스 · BLE 프로토콜 자체의 기능 변경 |

### 1.4 본 작업의 성격
**동작 보존 리팩터링(behavior-preserving refactoring)** 이다. 모듈 경계 재배치와 그에 필수적으로 수반되는 패키지/의존성 조정 외에, BLE 연결 절차·재연결 정책·파싱 규칙의 **관찰 가능한 동작을 변경해서는 안 된다**.

---

## 2. Definitions & References

### 2.1 용어

| 용어 | 정의 |
|---|---|
| **IMU 프레임(frame)** | 한 시점의 관성 측정값 1세트. 가속도 3축 + 자이로 3축 = **6개** float. |
| **페이로드(payload)** | BLE Notify로 수신되는 UTF-8 문자열 1줄. 정상 형식은 쉼표로 구분된 6개 실수: `"ax,ay,az,gx,gy,gz"` |
| **에러 페이로드** | 센서 펌웨어가 보내는 오류 통보. `ERR:` 접두사로 시작한다(대소문자 무관). |
| **DataSource** | BLE 하드웨어를 추상화한 계약. 실제 하드웨어용과 목(mock)용 구현이 교체 가능하다. |

### 2.2 관련 문서
- `docs/PHASE2_PLAN.md` — Phase 2 모듈 분해 계획 (A그룹)
- `docs/specs/TASK-010-core-ui-extraction.md` — 선행 모듈 추출 작업의 구조 참고
- `TennisDocAI/build.gradle.kts` — `verifyModuleDependencies` 의존성 규칙 (SSOT)
- `TennisDocAI/AI_README.md` — 빌드/테스트 명령

### 2.3 대상 모듈
- `target_project`: `TennisDocAI`
- 신규 모듈 경로: `TennisDocAI/core/sensor/`
- 신규 패키지 루트: `io.github.loje0611.tennisdoc.core.sensor`
- `settings.gradle.kts`에 `include(":core:sensor")`는 **이미 존재**하며, `core/sensor/build.gradle.kts`도 스캐폴드 상태로 **이미 존재**한다(namespace 설정 완료). 모듈을 새로 생성하는 것이 아니라 **비어 있는 모듈을 채우는** 작업이다.

---

## 3. Functional Requirements

### FR-1. 연결 상태 모델 이전
BLE 연결 상태를 표현하는 sealed 타입을 `:core:sensor`로 이전한다.

- **위치**: `io.github.loje0611.tennisdoc.core.sensor.BleConnectionState`
- **상태 집합** (기존과 동일하게 유지): `Disconnected`, `Scanning`, `Connected`, `Error(reason)`
- **오류 사유 집합** (기존과 동일하게 유지): `BluetoothOff`, `PermissionDenied`, `ScanFailed`, `ConnectionTimeout`, `MaxReconnectReached`
- **파생 속성**: 연결 여부를 알려주는 속성과, 연결이 끊겼거나 오류 상태인지 알려주는 속성을 제공한다.
- 상태나 오류 사유를 **추가·삭제·개명하지 않는다.**

### FR-2. BLE 연결 관리자 및 DataSource 계층 이전
다음 구성요소를 `:core:sensor`의 `io.github.loje0611.tennisdoc.core.sensor` 패키지로 이전한다.

| 구성요소 | 책임 |
|---|---|
| `BleManager` | BLE 스캔·연결·GATT 콜백 처리·재연결·명령 전송 |
| `SensorDataSource` | 센서 하드웨어 추상 계약: `connect()`, `disconnect()`, `release()`, `sendCommand(cmd)` |
| `RealBleDataSource` | `BleManager`를 사용하는 실제 하드웨어 구현 |
| `MockBleDataSource` | 하드웨어 없이 연결 라이프사이클만 흉내내는 구현 |

- 각 타입의 **공개 API 시그니처(메서드명·파라미터 순서·타입·반환 타입)를 변경하지 않는다.** 변경 가능한 것은 패키지 선언과 import 뿐이다.
- `BleManager`의 스캔 필터 UUID, 특성(characteristic) UUID, 타임아웃 값, 재연결 최대 횟수 등 **모든 상수 값을 그대로 보존한다.**

### FR-3. IMU 프레임 규격 상수의 소유권 이전
현재 `ImuPayloadParser`는 축 개수를 `analysis` 패키지의 `EdgeImpulseInputSpec.AXES_PER_SAMPLE`에서 가져온다. §1.2의 의존 규칙상 `:core:sensor`는 `:core:analysis`를 참조할 수 없으므로, 이 결합을 제거해야 한다.

- **축 개수 `6`은 센서 도메인의 사실**(하드웨어가 6축을 전송한다)이지 ML 모델의 사실이 아니다. 따라서 이 상수의 **소유권을 `:core:sensor`로 옮긴다.**
- `:core:sensor`에 IMU 프레임 규격을 표현하는 공개 상수 보유 타입을 정의하고, 축 개수 상수를 값 **6**으로 노출한다.
- `ImuPayloadParser`는 이 상수를 참조한다. 리터럴 `6`을 파서 본문에 직접 하드코딩하지 않는다.
- **상수 이름은 구현자가 결정하되**, `:core:sensor`의 공개 API로 노출되어 `:app`에서 참조 가능해야 한다.

### FR-4. 축 개수 정합성 보장
FR-3로 인해 축 개수를 나타내는 상수가 `:core:sensor`와 `:app`의 `EdgeImpulseInputSpec` 두 곳에 존재하게 된다. 두 값이 **어긋나면 런타임에 파싱이 조용히 실패**하므로, 불일치를 빌드/테스트 시점에 반드시 검출해야 한다.

- `:app`의 `EdgeImpulseInputSpec.AXES_PER_SAMPLE`은 `:core:sensor`가 노출하는 축 개수 상수와 **항상 같은 값**이어야 한다.
- 이 불변식은 **자동 테스트로 검증 가능**해야 한다. 두 상수를 각각 읽어 비교하는 테스트를 `:app`에 둔다(`:app`은 `:core:sensor`에 의존하므로 두 값을 모두 볼 수 있다).
- `EdgeImpulseInputSpec`의 `WINDOW_SAMPLES`(40), `FLAT_SIZE`(= 40 × 6) 값과 그 의미는 **변경하지 않는다.**

> 참고: `EdgeImpulseInputSpec`이 `:core:analysis`로 이전되는 TASK-013 시점에는 `:core:analysis → :core:sensor` 의존이 허용되므로, 그때 상수를 직접 파생시켜 중복을 완전히 제거할 수 있다. 본 작업에서는 정합성 테스트로 충분하다.

### FR-5. IMU 페이로드 파서 이전 및 동작 보존
`ImuPayloadParser`를 `:core:sensor`로 이전한다. 파싱 규칙은 다음과 같으며, **한 항목도 변경해서는 안 된다.**

**입력**: UTF-8 문자열 1줄
**출력**: 성공 시 길이 6의 `FloatArray`, 실패 시 `null`

| 입력 조건 | 요구 동작 |
|---|---|
| 앞뒤 공백이 있는 정상 페이로드 | 공백을 무시하고 정상 파싱 |
| 각 필드에 공백이 섞인 경우 (`"1.0, 2.0, ..."`) | 필드별로 공백을 제거하고 정상 파싱 |
| 빈 문자열 또는 공백만 있는 문자열 | `null` |
| `ERR:` 로 시작 (대소문자 무관) | `null` |
| 쉼표 구분 필드 수가 6이 아님 | `null` |
| 숫자로 변환 불가한 필드 포함 | `null` |
| 음수·0 포함 정상 페이로드 | 부호를 보존하여 정상 파싱 |

- 어떤 입력에 대해서도 **예외를 던지지 않는다.** 실패는 항상 `null` 반환으로 표현한다.

### FR-6. `MockSwingDataGenerator`는 이전 대상에서 제외
`app/.../sensor/MockSwingDataGenerator.kt`는 `:core:sensor`로 **이전하지 않는다.**

- 사유: 이 클래스는 `analysis` 패키지의 `SwingKinematicsBuffer.CAPACITY`에 의존한다. `:core:sensor`로 옮기면 `:core:sensor → :core:analysis` 의존이 발생하여 §1.2의 규칙을 위반한다.
- 또한 이 클래스는 BLE 통신이 아니라 **운동학 샘플 시퀀스를 합성**하므로 센서 통신 계층의 책임이 아니다.
- 조치: `:app` 모듈에 **현재 위치와 패키지 그대로 유지**한다. 이 파일의 내용을 수정하지 않는다.
- `MockBleDataSource`의 KDoc이 `MockSwingDataGenerator`를 언급하고 있으나 이는 **문서 참조일 뿐 코드 의존이 아니다.** 모듈 분리 후 KDoc 링크가 해석되지 않는다면, 링크 표기를 일반 텍스트로 낮추어 표현하되 설명 내용은 유지한다.

### FR-7. 호출부 갱신
이전된 타입을 참조하는 `:app`의 모든 파일이 새 패키지를 import하도록 갱신한다. 최소한 다음 파일들이 영향을 받는다.

- `MainViewModel.kt`
- `service/SwingAnalysisForegroundService.kt`
- `session/SwingAnalysisSessionState.kt`
- `ui/practice/PracticeScreen.kt`
- `sensor/MockSwingDataGenerator.kt` (FR-6에 따라 잔류하나, 잔류 자체로 수정이 불필요하면 손대지 않는다)

**호출부의 로직을 변경하지 않는다.** import 구문 갱신에 한정한다.

### FR-8. 모듈 빌드 구성
`core/sensor/build.gradle.kts`를 이 모듈이 컴파일되도록 구성한다.

- 기존 `plugins`/`namespace` 설정을 유지한다. 이 모듈은 Compose UI를 포함하지 않으므로 Compose 플러그인을 적용하지 않는다.
- 컴파일에 필요한 의존성만 선언하고, 단위 테스트 실행에 필요한 테스트 의존성을 선언한다.
- **어떤 `project(...)` 의존성도 선언하지 않는다.** (§1.2)
- `:app`의 `build.gradle.kts`에 `:core:sensor`에 대한 의존을 추가한다.

### FR-9. BLE 권한 선언의 모듈 지역화
`BleManager`가 동작하려면 Bluetooth 스캔/연결 권한이 필요하다. 이 권한 요구사항은 센서 모듈에 기인하므로, `:core:sensor`가 자신의 `AndroidManifest.xml`에 필요한 `uses-permission`을 선언하여 매니페스트 병합으로 앱에 반영되게 한다.

- 대상 권한: BLE 스캔·연결에 필요한 권한, 및 구버전 Android 호환을 위해 앱이 현재 선언하고 있는 관련 권한.
- **관찰 가능한 요구사항**: 병합된 앱 매니페스트에서 BLE 관련 권한 선언이 이전과 동일하게 존재해야 한다. 즉 **모듈 분리로 인해 사라지는 권한이 없어야 한다.**
- 앱 고유 권한(포그라운드 서비스, 알림 등)은 `:app` 매니페스트에 그대로 둔다.
- 기존 `:app` 매니페스트에서 BLE 권한 선언을 제거할지 여부는 구현자가 결정한다. 매니페스트 병합 결과가 동일하다면 어느 쪽도 허용된다.

### FR-10. 테스트 이전
`app/src/test/.../sensor/ImuPayloadParserTest.kt`가 검증하던 파싱 동작(FR-5의 7개 조건)은 **모듈 이전 후에도 계속 검증되어야 한다.**

- 검증 위치는 `:core:sensor` 모듈의 단위 테스트로 한다. `ImuPayloadParser`가 `:core:sensor`에 있으므로, 그 모듈에서 검증하는 것이 자연스럽다.
- `:app`에는 동일 대상을 중복 검증하는 테스트를 남기지 않는다.
- 테스트의 **구체적 작성 방식과 케이스 분할은 구현/검증 주체의 판단에 맡긴다.** 본 명세는 FR-5의 동작이 관찰 가능하게 검증될 것을 요구할 뿐이다.

---

## 4. Interfaces & Data Structures

### 4.1 `:core:sensor` 공개 API (패키지 `io.github.loje0611.tennisdoc.core.sensor`)

```kotlin
// 연결 상태 (FR-1)
sealed class BleConnectionState {
    data object Disconnected : BleConnectionState()
    data object Scanning : BleConnectionState()
    data object Connected : BleConnectionState()
    data class Error(val reason: ErrorReason) : BleConnectionState()

    enum class ErrorReason {
        BluetoothOff, PermissionDenied, ScanFailed,
        ConnectionTimeout, MaxReconnectReached,
    }

    val isConnected: Boolean
    val isDisconnectedOrError: Boolean
}

// 센서 추상화 (FR-2) — 시그니처 변경 금지
interface SensorDataSource {
    fun connect()
    fun disconnect()
    fun release()
    fun sendCommand(cmd: String)
}

// IMU 프레임 규격 (FR-3) — 타입/상수 이름은 구현자 재량, 값은 6 고정
//   축 개수를 노출하는 공개 상수를 제공할 것

// 페이로드 파서 (FR-5)
//   parseLine(line: String): FloatArray?   ← 시그니처 유지
```

`BleManager`, `RealBleDataSource`, `MockBleDataSource`의 공개 시그니처는 현재 `:app`에 있는 것과 **완전히 동일하게 유지**한다.

### 4.2 모듈 의존 그래프 (본 작업 완료 시점)

```
:app  ──▶  :core:ui
  └──▶  :core:sensor  ──▶  (없음)
```

`:core:sensor`에서 나가는 화살표는 **존재해서는 안 된다.**

---

## 5. UI/UX Requirements

**N/A (백엔드/인프라 모듈).**

단, 본 작업은 동작 보존 리팩터링이므로 **기존 화면의 동작과 표시가 달라져서는 안 된다.** 특히 `PracticeScreen`의 연결 상태 표시는 이전과 동일하게 동작해야 한다.

---

## 6. Non-Functional Requirements

| 항목 | 요구사항 |
|---|---|
| 언어/플랫폼 | Kotlin, Android Library 모듈 |
| 신규 서드파티 라이브러리 | **추가 금지.** 현재 `BleManager`가 사용하는 Android 프레임워크 API와 표준 라이브러리로 충분하다. |
| 버전 카탈로그 | 의존성은 `gradle/libs.versions.toml`의 기존 alias만 사용한다. 새 alias 추가가 불가피하면 그 사유를 커밋 메시지에 남긴다. |
| 컨벤션 플러그인 | `build-logic`의 기존 컨벤션 플러그인을 사용한다. 새 컨벤션 플러그인을 만들지 않는다. |
| minSdk/컴파일 설정 | 컨벤션 플러그인이 정하는 값을 따르며 모듈에서 재정의하지 않는다. |
| 동작 호환성 | BLE 연결·재연결·명령 전송의 **런타임 동작이 변경되지 않아야 한다.** |

---

## 7. Error Handling & Edge Cases

| 상황 | 요구 동작 |
|---|---|
| 파서에 빈/공백 문자열 입력 | `null` 반환, 예외 없음 (FR-5) |
| 파서에 `ERR:` 페이로드 입력 | `null` 반환, 예외 없음. 대소문자를 구분하지 않는다. |
| 파서에 필드 수 불일치 입력 | `null` 반환 |
| 파서에 숫자 아닌 필드 포함 입력 | `null` 반환, `NumberFormatException`이 밖으로 새어나가지 않음 |
| 축 개수 상수 불일치 | 테스트가 **실패**해야 한다 (FR-4) |
| `:core:sensor`에 모듈 의존 추가 | `verifyModuleDependencies`가 **빌드를 실패**시켜야 한다 |
| BLE 권한 미보유 상태에서 스캔 | 기존 동작 유지 — `BleConnectionState.Error(PermissionDenied)` 통보 |

---

## 8. Acceptance Criteria

> 각 항목은 **결과물의 관찰 가능한 속성**이다. 구현 코드를 다시 읽어 확인하는 방식이 아니라, 빌드·테스트·산출물 검사로 확인되어야 한다.

- [ ] **AC-1** `./gradlew verifyModuleDependencies test assembleDebug`가 성공한다.
- [ ] **AC-2** `:core:sensor` 모듈이 컴파일되며, 산출물(AAR/클래스)에 `BleManager`, `BleConnectionState`, `SensorDataSource`, `RealBleDataSource`, `MockBleDataSource`, `ImuPayloadParser`가 모두 포함된다.
- [ ] **AC-3** `:core:sensor`는 **어떤 프로젝트 모듈에도 의존하지 않는다.** (`verifyModuleDependencies` 통과가 이를 보증하며, 빌드 스크립트에 `project(":...")` 선언이 없음으로도 확인된다)
- [ ] **AC-4** `:core:sensor`의 소스 어디에도 `io.github.loje0611.tennisdoc.analysis` 패키지를 import하는 구문이 없다.
- [ ] **AC-5** 이전된 6개 타입이 `:app` 모듈의 소스 트리에 **더 이상 존재하지 않는다.** (`app/src/main/.../BleManager.kt`, `BleConnectionState.kt`, `sensor/{SensorDataSource,RealBleDataSource,MockBleDataSource,ImuPayloadParser}.kt` 부재)
- [ ] **AC-6** `MockSwingDataGenerator.kt`는 `:app` 모듈에 **여전히 존재**하며, 그 내용이 작업 전과 동일하다.
- [ ] **AC-7** IMU 페이로드 파싱 동작이 자동 테스트로 검증되며, FR-5 표의 **7개 입력 조건이 모두 커버**된다. 해당 테스트는 `:core:sensor` 모듈에서 실행되고 전부 통과한다.
- [ ] **AC-8** 축 개수 상수 정합성(FR-4)을 검증하는 테스트가 존재하고 통과한다. 이 테스트는 `:core:sensor`의 상수와 `EdgeImpulseInputSpec.AXES_PER_SAMPLE`을 **각각 읽어 비교**해야 한다.
- [ ] **AC-9** AC-8의 테스트가 실효성이 있다: 한쪽 상수 값을 일시적으로 다르게 바꾸면 해당 테스트가 **실패**한다. (검증 후 원복)
- [ ] **AC-10** `EdgeImpulseInputSpec`의 `WINDOW_SAMPLES`가 40, `AXES_PER_SAMPLE`이 6, `FLAT_SIZE`가 240으로 유지된다.
- [ ] **AC-11** 병합된 디버그 매니페스트에 BLE 스캔·연결 권한이 선언되어 있다. 작업 전 앱 매니페스트에 있던 BLE 관련 권한 중 **병합 결과에서 누락된 것이 없다.**
- [ ] **AC-12** `:app`에 `ImuPayloadParser`를 검증하는 중복 테스트가 남아 있지 않다.
- [ ] **AC-13** `:app`의 기존 단위 테스트가 **모두 통과**한다 (회귀 없음).
- [ ] **AC-14** `BleConnectionState`의 상태 4종과 `ErrorReason` 5종이 작업 전과 동일한 이름으로 존재한다.
- [ ] **AC-15** 작업 범위 밖 파일이 수정되지 않았다. 특히 `tennis-vision-analyzer/` 등 다른 서브프로젝트와 `docs/` 하위 산출물(스펙·QA 리포트 제외)에 변경이 없다.

---

## 9. Testing Instructions

`TennisDocAI/AI_README.md`에 정의된 표준 검증 명령을 사용한다.

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies test assembleDebug
```

### 검증 시 유의사항
- `verifyModuleDependencies`는 §1.2 의존 규칙 위반을 검출한다. **이 태스크가 실패하면 AC-3은 자동으로 불합격**이다.
- `test`는 `:app`과 `:core:*` 모든 모듈의 단위 테스트를 실행한다. `:core:sensor`의 테스트가 실제로 실행되었는지 테스트 리포트에서 확인한다 — 모듈에 테스트가 없으면 태스크는 아무것도 실행하지 않고도 성공하므로, **"BUILD SUCCESSFUL"만으로 AC-7을 판정하지 않는다.**
- AC-11(매니페스트 병합)은 `assembleDebug` 산출물인 병합된 매니페스트 파일을 직접 확인한다.
- AC-9(테스트 실효성)는 상수를 일시적으로 변조하여 테스트가 실패하는지 확인한 뒤 **반드시 원복**한다.
- 실기기·에뮬레이터가 필요한 계측 테스트(`androidTest`)는 본 작업의 필수 검증 대상이 아니다. BLE 실동작은 하드웨어 없이 검증할 수 없으므로, `BleManager`에 대해서는 **컴파일 성공과 API 보존**으로 검증을 한정한다.
