# 🧱 Phase 2 실행 계획 (Task Backlog)

> **최종 갱신**: 2026-08-08 (TASK-017 완료 반영 — §8.4 신설, 부채 1건 해소·1건 추가)
> **문서 성격**: Phase 2에서 생성할 **task 후보 목록과 순서**를 보존하는 실행 계획서.
> - 단계 계획의 SSOT는 루트 [`README.md`](../README.md#-제품-비전-및-단계별-로드맵)입니다.
> - 각 결정의 **근거(Why)** 는 [`PRODUCT_DIRECTION.md`](PRODUCT_DIRECTION.md)에 있습니다.
> - 실제 task 등록 상태는 [`task-board.json`](task-board.json)이 SSOT입니다.
> 세 문서가 충돌하면 `README.md` → `task-board.json` → 본 문서 순으로 우선합니다.

---

## 1. 이 문서가 필요한 이유

`docs/AGENT_WORKFLOW.md`의 **단일 task 처리 제약** 때문에 `task-board.json`에는 진행 중인 task 하나만 등록됩니다. 따라서 **아직 등록되지 않은 나머지 계획은 보드에 존재하지 않습니다.** 이 문서가 그 공백을 메우며, 특히 정규 파이프라인 밖에서 수행되는 **선행 스파이크(D-10)가 누락되지 않도록** 명시적으로 기록합니다.

> **주의**: 아래 `TASK-0XX`는 **예정 번호**입니다. 실제 ID는 등록 시점에 PM이 `task-board.json`의 최대 번호 + 1로 계산하므로 달라질 수 있습니다.

---

## 2. Phase 2 범위

README 로드맵 기준: **Gradle 멀티모듈 분리(`:core:*` / `:feature:*`) 후, 검증된 비전 알고리즘(수학 공식·임계값·타이밍 로직)을 Kotlin으로 포팅(CameraX + MediaPipe Android SDK).**

센서-비전 융합(임팩트 앵커 동기화, Fusion 엔진)은 **Phase 3**이며 본 계획에 포함하지 않습니다.

---

## 3. 선행: SPIKE-01 (정규 task 아님)

| 항목 | 내용 |
|---|---|
| **제목** | MediaPipe Pose Landmarker Android 실기기 실용성 검증 |
| **성격** | **폐기 전제 스파이크.** D-10에 따라 task 파이프라인에 등록하지 않음 |
| **왜 선행인가** | 실기기 처리 성능이 기대에 못 미치면 `:feature:lab`의 설계 전제(30fps 실시간 포즈 추출)가 무너져 **Phase 2 후반 C그룹 전체가 재설계**된다 |
| **측정 항목** | 실기기 fps, 프레임당 지연(ms), 배터리 소모, 해상도별 정확도 |
| **산출물** | 코드가 아니라 **측정 결과 문서**. TASK-024 spec의 입력으로 사용 |
| **수행 시점** | A그룹(모듈 분리)과 **병행 가능**. C그룹 착수 전까지 완료 |

> 스파이크 결과가 부정적일 경우의 대안(해상도·프레임레이트 하향, 임팩트 ±2초 구간만 처리 — D-6의 자동 클립 전략)까지 함께 기록합니다.

---

## 4. A그룹 — 멀티모듈 분리 + 개명

모듈 경계는 **D-9**, 개명 범위는 **D-8**을 따릅니다. **A그룹은 아래 10개 task 전부이며, `:feature:*` 이관(016~018)까지 포함합니다.**

| 예정 ID | 제목 | depends_on | 검증 방법 | 상태 |
|---|---|---|---|---|
| **TASK-009** | Gradle 멀티모듈 스캐폴딩 · 버전 카탈로그 정비 · **앱 개명(D-8)** | `[]` | `./gradlew test`·`assembleDebug` 그린 + 모듈 의존 방향 규칙 검증 | ✅ |
| **TASK-010** | `:core:ui` 추출 (테마 + 공용 Compose 컴포넌트) | `[009]` | 컴파일 + 기존 화면 렌더 회귀 없음 | ✅ |
| **TASK-011** | `:core:sensor` 추출 (BLE · IMU 파서) | `[009]` | `ImuPayloadParserTest` 이관 후 통과 | ✅ |
| **TASK-012** | `:core:data` 추출 (Room · DataStore) | `[009]` | `SwingSessionDaoTest` 통과, `schemas/` 경로 유지 | ✅ |
| **TASK-013** | **JNI 바인딩 `RegisterNatives` 전환** (개명 후유증 수정 — §4.1) | `[009]` | `verifyJniBindings` 태스크가 4개 ABI 전부 통과 | ✅ |
| **TASK-014** | **`:core:model` 신설** (공용 도메인 타입 — §4.2) | `[009]` | `SwingMetrics`·`SwingClassificationKeys` 이전 후 전 모듈 그린 | ✅ |
| **TASK-015** | `:core:analysis` 추출 (Kinematic · Coaching · **Edge Impulse NDK**) | `[011, 013, 014]` | `KinematicAnalyzerTest`·`CoachingEngineTest`·`VolleyDetectorTest`·`SwingInferenceBufferTest` 통과 + `verifyJniBindings` 유지 | ✅ |
| **TASK-016** | **`:feature:history` 추출 준비 — 결합 해소 리팩터링 (§4.4)** | `[012, 014, 015]` | 이력 화면의 `:app`·`:core:analysis` 참조 0건 | ✅ |
| **TASK-017** | `:feature:history` 모듈 신설 및 이관 | `[016]` | 이력 조회 화면 컴파일 + 모듈 독립 검증 | ✅ |
| **TASK-018** | `:feature:match` 이관 및 v1 내비게이션 비활성화(보존) | `[010, 011, 012, 015]` | 라우트 목록에 match 부재 + 모듈 독립 컴파일 | ▶ 다음 |

### 4.1 발견·수정된 결함: JNI 심볼이 개명을 따라가지 않음 (TASK-013)

**TASK-012 완료 후 TASK-013 사전 조사 중 발견.** TASK-013에서 수정 완료(`DONE`).

JNI는 기본적으로 **함수 이름 규칙으로 네이티브 함수를 찾습니다.** 그런데 C++ 심볼이 개명 전 패키지를 그대로 쓰고 있었습니다.

| | 값 |
|---|---|
| C++ 정의 (`app/src/main/cpp/ei_jni_bridge.cpp`) | `Java_com_example_swingsenseai_inference_EdgeImpulseNative_runClassifierNative` |
| Kotlin 실제 클래스 | `io.github.loje0611.tennisdoc.inference.EdgeImpulseNative` |
| 이름 규칙상 필요한 심볼 | `Java_io_github_loje0611_tennisdoc_inference_EdgeImpulseNative_runClassifierNative` |

`JNI_OnLoad`/`RegisterNatives`가 없어 **이름 기반 바인딩이 유일한 연결 수단**이었으므로, 호출 시 `UnsatisfiedLinkError`가 발생했습니다. 빌드된 `.so` 4개 ABI 전부에서 낡은 심볼만 존재하고 새 심볼과 `JNI_OnLoad`가 모두 부재함을 확인했습니다.

**왜 아무도 못 잡았는가 — 실패가 조용했기 때문입니다.**

1. `System.loadLibrary("swingsense_ei")`는 `.so`가 존재하므로 **성공**한다 → `isAvailable == true`
2. `runClassifierNative` 호출 시점에야 심볼 탐색이 실패한다
3. `EdgeImpulseNative.runClassifier`의 `catch (t: Throwable)`가 이를 **삼키고 빈 문자열 `""`를 반환**한다
4. 빈 문자열은 "분류 결과 없음"과 구별되지 않는다 → 사용자에겐 **"스윙이 안 잡힌다"** 로만 보인다

단위 테스트는 JVM에서 실행되어 `.so`를 적재하지 않고, 추론 경로를 덮는 계측 테스트도 없습니다. 그래서 TASK-009~012의 모든 QA가 그린이면서도 이 결함을 통과시켰습니다.

**리팩터링보다 먼저 고친 이유**: 고장난 상태에서 `:core:analysis`를 옮기면, 이후 추론이 동작하지 않을 때 **원래 고장나 있던 것인지 이번 이동으로 깨진 것인지 구분할 수 없습니다.** 정상 기준선(baseline)을 먼저 확보했습니다.

**채택된 해법 — `RegisterNatives` 전환** (사용자 승인, 2026-08-07): 심볼 이름을 새 패키지로 고치는 것만으로도 당장의 결함은 사라지지만, TASK-015에서 이 클래스를 `:core:analysis`로 **한 번 더 옮길 예정**이라 같은 결함이 재발합니다. `RegisterNatives`는 클래스 경로를 C++ 내 명시적 문자열 한 곳으로 모으고, 그 값이 틀리면 `JNI_OnLoad`가 `JNI_ERR`을 반환해 **라이브러리 적재 자체가 실패**하게 만듭니다. 그 결과 바인딩 결함이 "결과 없음"이 아니라 **`isAvailable == false`** 로 드러납니다.

**도입된 검증 수단 — `verifyJniBindings` (기기 불필요)**: `verifyModuleDependencies`와 같은 성격의 Gradle 검증 태스크입니다. 패키징되는 `.so`를 ABI별로 검사해 ⑴ `JNI_OnLoad` 존재, ⑵ 낡은 `Java_com_example_swingsenseai` 심볼 부재, ⑶ 클래스 디스크립터가 Kotlin 클래스의 실제 위치와 일치를 확인합니다.

핵심은 **기대값을 Kotlin 소스의 `package` 선언에서 파생**시킨다는 점입니다. 기대값을 따로 하드코딩하면 패키지 이동 시 소스·C++·검증값이 모두 어긋난 채 통과해버립니다. 소스에서 파생시키면 **C++을 고치지 않은 실수가 자동 검출**됩니다. TASK-015의 모듈 이동에서 이 성질이 안전망이 됩니다.

> **검증의 실효성이 실증되었습니다.** TASK-013 QA에서 변이 검증 3종이 모두 의도대로 실패·복구했습니다 — C++ 클래스 경로 변조(AC-7), Kotlin 패키지 변조(AC-8), `.so` 제거(AC-9). 특히 AC-9는 산출물이 없을 때 **조용히 통과하지 않는지**를 확인한 것입니다.

**남은 한계**: `verifyJniBindings`는 "호출이 연결되는가"까지만 보장합니다. **추론 결과의 정확성**은 실기기가 필요하며 여전히 검증 수단이 없습니다(§9).

### 4.2 `:core:model` 신설 근거 (TASK-014)

`SwingMetrics`(6개 지표 값 객체)와 `SwingClassificationKeys`(스윙 레이블 상수 + 정규화)는 **여러 계층이 공유하는 도메인 타입**입니다.

```
analysis  : CoachingEngine, KinematicAnalyzer
data      : SwingHistoryRepository
ui        : HexagonalRadarChart, DeltaSummaryChips, SessionDetailViewModel
service   : SwingAnalysisForegroundService
```

그런데 `verifyModuleDependencies`의 현재 규칙에서 `:core:data`는 의존성이 **빈 집합**이고 `:core:analysis`는 `{:core:sensor}`만 허용합니다. **두 모듈이 서로를 참조할 수 없으므로 이 타입들이 있을 곳이 없습니다.**

TASK-012에서는 `SwingHistoryRepository`를 `:app`에 남겨 회피했으나, **이 회피는 C그룹에서 통하지 않습니다.** `:feature:history`의 허용 의존성은 `{:core:ui, :core:data}`인데 히스토리 화면이 `SwingMetrics`를 사용하므로, 이 타입이 `:app`에 있으면 **`:feature:history`는 영원히 추출 불가**입니다(하위 모듈은 `:app`을 참조할 수 없음).

따라서 `:core:model` 도입은 선택이 아니라 **이미 예정된 필수 작업**이며, 지금 하는 편이 이동할 코드가 적습니다.

- **모듈 성격**: Android 의존이 없는 **순수 Kotlin(JVM) 모듈**. 기존 `tennisdoc.jvm.library` 컨벤션 플러그인을 사용합니다(현재 미사용 상태).
- **범위에 포함되어야 할 것**: `settings.gradle.kts`의 `include(":core:model")` 추가와 **`verifyModuleDependencies` 허용 규칙 갱신**(`:core:model`은 빈 집합, 이를 참조하는 모듈들의 허용 집합에 추가). 이는 선언된 아키텍처를 바꾸는 작업이므로 spec에 명시적으로 기술합니다.
- **부수 효과**: `SwingHistoryRepository`의 `:core:data` 이전 경로가 열립니다(별도 판단 사항, 본 계획에서는 강제하지 않음).

### 4.3 주의사항

- **A그룹은 `:core:*` 추출로 끝나지 않습니다.** TASK-016~018의 `:feature:*` 이관까지가 A그룹입니다. `:core:` 5개가 빠졌다고 A그룹 완료로 오독하지 마십시오(실제로 한 차례 오판이 있었습니다 — §8).
- **TASK-009의 `target_project`는 개명 전 값(`SwingSenseAI`)** 입니다. 이 task가 디렉토리를 `TennisDocAI/`로 바꾸므로, **TASK-010부터는 `TennisDocAI`** 를 사용합니다.
- TASK-009 범위에 **후속조치 #11·#12 문서 갱신을 반드시 포함**합니다 — `README.md`(디렉토리 구조·모듈 구조), `docs/AGENT_WORKFLOW.md`(§7 테스트 명령표), 서브프로젝트 `AI_README.md`. 특히 `AI_README.md`는 **Tester가 테스트 명령을 조회하는 파일**이라, 누락하면 이후 모든 task의 QA가 잘못된 경로를 참조합니다.
- ~~**TASK-015가 최대 리스크**~~ — **해소되었습니다.** `externalNativeBuild`(CMake 3.22.1)·`abiFilters`·JNI 브리지 이관을 `retry_count` 0으로 통과했습니다. 1,395건의 이동 중 1,382건이 완전 동일(`R100`)했고, 내용이 바뀐 13건은 `package` 선언과 JNI 디스크립터 문자열뿐이었습니다.
- **잔여 리스크는 TASK-016으로 이동했습니다.** `SwingHistoryRepository`의 `:core:data` 이전을 포함한 결합 해소 4건을 TASK-016이 처리합니다(§4.4).
- 원래 계획에서는 위 작업이 단일 task(구 TASK-013)였으나, **JNI 결함 · 공용 타입 부재 · NDK 이동**이라는 서로 다른 세 실패 요인이 한 task에 겹쳐 있었습니다. 실패 시 원인 특정이 불가능하므로 **013(결함 수정) → 014(경계 확보) → 015(NDK 이동)** 로 분해했습니다. 각각 독립적으로 검증 가능했고, **분해 판단이 결과로 정당화되었습니다** — 세 task 모두 독립적으로 검증되었으며 재작업은 013의 1회(원인은 PM 절차)뿐이었습니다.

### 4.4 `:feature:history` 이관을 016·017로 분해한 근거

**TASK-016 명세 조사 중 발견.** 계획서는 `:feature:history`가 `{model, ui, data}`만 참조한다고 가정했으나, 이관 대상 7개 파일에 **허용 집합 밖 참조가 4건** 있었습니다. 저장소 이전 1건만 문제라고 본 §9의 진단은 불완전했습니다.

| # | 결합 | 채택한 해법 |
|---|---|---|
| 1 | `SwingHistoryRepository`가 `:app`에 있음 | `:core:data`로 이전. 단 `FileProvider` 기반 CSV 내보내기는 **매니페스트 `<provider>` 선언에 대한 암묵적 계약**이 되므로, 순수한 CSV 문자열 생성만 `:core:data`에 두고 파일·URI 생성은 `:app`에 남깁니다. 부수 효과로 **현재 검증 수단이 전혀 없는 CSV 로직이 단위 테스트 가능**해집니다. |
| 2 | `SessionDetailViewModel` → `CoachingEngine`(`:core:analysis`) | `:core:model`에 인터페이스를 두고 `:app`이 구현을 주입. `:core:analysis`는 **Edge Impulse NDK(약 29MB)** 를 포함하므로 이력 조회가 네이티브 빌드에 묶이는 것을 막습니다 — D-9.2가 `:core:vision`을 분리한 것과 같은 이유입니다. |
| 3 | `HistoryScreen` → `AppRoutes`·`SwingAnalysisSessionState`(`:app`) | 내비게이션은 콜백으로, 디버그 모드는 파라미터로 외부 주입. `session/`의 소속(D-9.1)은 Phase 3까지 유보한 채로 결합만 끊습니다. |
| 4 | `accentColorForCategory`가 `ui/history/`에 있는데 `PracticeScreen`(`:app`)이 사용 | `:core:ui`로 이전. **`PracticeScreen`은 `import` 없이 FQCN으로 직접 호출**하고 있어 단순 import 검색으로는 놓치는 결합입니다. |

**분해 이유**: 위 4건의 결합 해소와 모듈 신설·파일 이관·라이브러리 모듈 Hilt 도입을 한 task에 넣으면 실패 원인을 특정할 수 없습니다. 특히 **라이브러리 모듈에 Hilt를 적용한 전례가 이 저장소에 없어** 017은 미검증 인프라를 다룹니다(TASK-014에서 미사용 컨벤션 플러그인 `JvmLibraryConventionPlugin`이 실제로 결함을 갖고 있었습니다). 분리하면 017이 막히더라도 016의 결합 해소는 이미 커밋되어 안전합니다. 구 TASK-013 분해와 동일한 판단입니다.

- **TASK-016** — 결합 해소만. 파일은 `:app`에 그대로 두고, 이력 화면의 `:app`·`:core:analysis` 참조를 0건으로 만듭니다. 모듈을 만들지 않습니다.
- **TASK-017** — 모듈 신설과 파일 이관. 016 완료 후에는 기계적인 이동이 됩니다.

---

## 5. B그룹 — 비전 알고리즘 Kotlin 포팅

D-9.2에 따라 **`:core:vision`(순수 `kotlin("jvm")` 모듈)** 에 구현합니다. Android 의존이 없으므로 계측 기기 없이 검증됩니다.

| 예정 ID | 제목 | 원본 (Phase 1 산출물) | depends_on |
|---|---|---|---|
| **TASK-019** | `:core:vision` 모듈 + `PoseFrame` 데이터 계약 + 관절 각도 계산 | `src/angle_calculator.py` (TASK-002) | `[009]` |
| **TASK-020** | 속도 계산 + 다중 스윙 임팩트 감지 | `src/impact_detector.py` (TASK-003) | `[019]` |
| **TASK-021** | 스윙 궤적 분류 (Topspin / Flat / Slice) | `src/swing_path.py` (TASK-004) | `[020]` |
| **TASK-022** | 운동 체인 분석 | `src/kinetic_chain.py` (TASK-005) | `[020]` |
| **TASK-023** | 스윙 진단 · 피드백 생성 | `src/swing_diagnosis.py` (TASK-006) | `[019, 021, 022]` |

### 골든 픽스처 원칙 (필수)

B그룹 전 task의 Acceptance Criteria에 다음이 **반드시** 포함되어야 합니다.

> **동일 입력에 대해 Python 구현과 수치적으로 동일한 결과를 산출한다(허용 오차 명시).**

Python 쪽에서 입출력 골든 픽스처(JSON)를 추출해 Kotlin 테스트 리소스로 고정하는 방식입니다. 이것이 없으면 **"포팅했다"를 검증할 방법 자체가 존재하지 않습니다.** 픽스처 추출 도구는 TASK-019 범위에 포함하고 이후 task가 재사용합니다.

> Phase 1의 TASK-007(오버레이 렌더링)·TASK-008(Streamlit UI)은 **포팅 대상이 아닙니다.** 렌더링·UI는 Android에서 완전히 다른 구현이 되므로 Phase 3 Lab UI에서 새로 설계합니다. 다만 TASK-007 spec v2가 정의한 **툴팁 배치 불변식(겹침 금지·프레임 내부·배너 비간섭)** 은 Android UI에서도 동일하게 적용해야 할 요구사항이므로 Phase 3 spec 작성 시 참조합니다.

---

## 6. C그룹 — 카메라 · MediaPipe

| 예정 ID | 제목 | depends_on | 비고 |
|---|---|---|---|
| **TASK-024** | MediaPipe Pose Landmarker 통합 → `PoseFrame` 산출 | `[009, 019]` + **SPIKE-01** | Android SDK 바인딩은 D-9.2에 따라 `:feature:lab`에 배치 |
| **TASK-025** | CameraX 프레임 파이프라인 (`:feature:lab` 골격) | `[024]` | |

CameraX·MediaPipe 의존성은 현재 `libs.versions.toml`에 없습니다(후속조치 #4). TASK-009의 버전 카탈로그 정비 또는 TASK-024에서 도입합니다.

---

## 7. 의존 그래프

```text
SPIKE-01 (정규 task 아님) ──────────────────────────────────┐
                                                            │
009 ─┬─ 010 ──────────────┬─ 016                            │
     ├─ 011 ─┐            │                                 │
     ├─ 012 ─┼────────────┤                                 │
     ├─ 013 ─┼─ 015 ──────┴─ 017                            │
     ├─ 014 ─┘                                              │
     │                                                      │
     └─ 018 ─┬─ 019 ─┬─ 020 ─┬─ 022                         │
             │       └─ 021 ─┘                              │
             └────────── 023 ◀─────────────────────────────┘
                          └─ 024
```

정확한 의존 관계는 §4·§5·§6 표의 `depends_on`이 SSOT이며, 위 그림은 흐름 요약입니다.

- **015**(`:core:analysis`)는 **011**(sensor) · **013**(JNI 복구) · **014**(model)에 의존합니다.
- **016**(`:feature:history`)은 **010**(ui) · **012**(data) · **014**(model)에 의존합니다.
- **017**(`:feature:match`)은 **010** · **011** · **012** · **015**에 의존합니다.

비순환이며, A그룹과 B그룹은 TASK-009 이후 서로 독립적으로 진행 가능합니다. 다만 **단일 task 처리 제약** 때문에 실제 실행은 순차입니다.

---

## 8. 실행 규칙

- PM은 계획 전체를 사용자에게 먼저 보고한 뒤 **첫 task 하나만 생성·개시**합니다(`prompts/pm-agent.md`). 위 16개를 한꺼번에 등록하는 것은 규약 위반입니다.
- 각 task가 `DONE` 또는 `BLOCKED`에 도달한 뒤 다음 task를 생성합니다.
- 기존 task의 요구사항 결함이 발견되면 신규 등록이 아니라 **Step 1A 명세 개정**(`AGENT_WORKFLOW.md` §5)으로 처리합니다.
- **PM은 문서 변경을 인계 전에 커밋합니다.** 커밋되지 않은 변경은 경계 검사에서 Developer의 무단 수정과 구별되지 않아 원복됩니다(§8.1).

### 8.1 사례: 미커밋 PM 문서가 원복된 건 (TASK-013)

TASK-013 진행 중 본 문서의 PM 수정본이 **Tester의 경계 위반 판정을 받아 Developer가 원복**했습니다.

- **경위**: PM이 본 문서를 수정한 뒤 커밋하지 않은 상태로 `turn.json`을 Developer에게 넘겼습니다. Tester의 경계 검사는 `git status`/`git diff` 기반이므로, 워킹 트리에 남은 PM의 변경이 **Developer가 권한 없이 수정한 것으로 보였습니다.**
- **판정의 타당성**: Tester가 옳았습니다. 증거만으로는 누가 수정했는지 구별할 수 없고, 실제로 TASK-013 명세(FR-8)는 `AI_README.md`와 `AGENT_WORKFLOW.md`만 허용했습니다.
- **교훈**: 이는 `prompts/developer-agent.md`에서 고쳤던 **일괄 스테이징 결함과 같은 계열**입니다. 커밋되지 않은 상태가 task 경계를 넘어 새어나가면 소유자가 불분명해집니다. PM도 동일 규율을 따라야 합니다.

### 8.2 A그룹 중간 점검 결과 (2026-08-07)

TASK-015 완료 시점에 A그룹 전체를 감사한 결과입니다.

**확인된 것**

- `:app`에서 5개 모듈(`model`·`ui`·`sensor`·`data`·`analysis`)이 분리되었고, 선언(`verifyModuleDependencies`)과 실제 의존성이 일치합니다. 모듈 간 실제 참조는 `:core:analysis → :core:model` 하나뿐으로, 그래프가 계획보다 단순합니다.
- `verifyModuleDependencies`·`verifyJniBindings` 두 검증 태스크가 자동화되었고, TASK-011·012·013·014·015의 **변이 검증이 모두 실제로 실패·복구**했습니다. 검사가 형식만 통과하는 것이 아님이 실증되었습니다.
- `retry_count`는 009가 2, 013이 1(PM 절차 오류), 나머지 5건은 0입니다. 스캐폴딩 이후 안정적입니다.

**주의: 빈 모듈의 초록색은 검증이 아닙니다**

`:core:vision`·`:feature:match`·`:feature:history`·`:feature:lab`은 TASK-009에서 스캐폴딩만 되어 **소스가 0개**입니다. 이 모듈들은 `test`가 항상 성공하고 `verifyModuleDependencies`도 무조건 통과합니다. 016~018·B그룹이 실제 코드를 채우기 전까지 **전체 그린은 실제보다 낙관적으로 보입니다.**

> **갱신(2026-08-08)**: TASK-017로 `:feature:history`는 소스를 갖게 되어 위 경고에서 해제되었습니다. 다만 이 모듈에는 **단위 테스트가 0건**이라 `test` 성공은 여전히 근거가 되지 못하며, 실질 검증은 모듈 단독 빌드(`:feature:history:assembleDebug`)와 의존성 규칙 변이로 대체했습니다. `:core:vision`·`:feature:match`·`:feature:lab` 3개는 경고가 그대로 유효합니다.

**정리 대상(부채)**

- **ProGuard 규칙 중복** — JNI keep 규칙이 `app/proguard-rules.pro`와 `core/analysis/consumer-rules.pro` 양쪽에 있습니다. 규칙이 대상 코드와 함께 이동하도록 모듈 쪽만 남기는 것이 원칙입니다.
- **사문화된 ProGuard 규칙** — `...tennisdoc.data.db.**` 3줄은 가리키는 코드가 `main`에 존재하지 않습니다.
- **`SwingHistoryRepository.CSV_TIMESTAMP_FORMAT`의 가시성** (TASK-016) — 테스트에서 기대값을 만들기 위해 `private`에서 공개 `val`로 넓혔습니다. `SimpleDateFormat`은 **스레드 안전하지 않으므로** 공개된 단일 인스턴스를 여러 호출자가 동시에 쓰면 깨집니다. 현재 사용처는 저장소 내부의 단일 IO 경로뿐이라 실제 위험은 없으나, 공개 API로 둘 이유는 없습니다. 테스트가 서식 문자열만 참조하도록 바꾸고 다시 좁히는 것이 맞습니다.
- **`HistoryScreen(debugModeEnabled: Boolean = false)`의 기본값** (TASK-016) — ~~호출자가 전달을 누락해도 컴파일이 통과하고 디버그 UI가 조용히 꺼집니다.~~ **해소됨(TASK-017).** 기본값을 제거해 필수 인자로 만들었고, 인자를 빼면 `:app:compileDebugKotlin`이 실패함을 변이로 확인했습니다.
- **사문화된 `hiltViewModel` import** (TASK-017) — `SessionDetailScreen.kt`가 `androidx.hilt.navigation.compose.hiltViewModel`을 import하지만, ViewModel 획득을 `:app`의 내비게이션 진입점으로 올린 뒤 **모듈 내 호출이 0건**입니다. 그 결과 `:feature:history`의 `hilt-navigation-compose` 의존성도 함께 사문화되었습니다. 위의 ProGuard 사문화 규칙과 **같은 계열**이며, 죽은 선언은 이후 어느 것이 살아 있는지 판단을 흐립니다. import와 의존성을 함께 제거하는 것이 맞습니다.

위 항목들은 동작에는 무해하나, 죽은 규칙과 선언이 쌓이면 이후 어느 것이 살아 있는지 판단할 수 없게 됩니다. A그룹 완료(018) 후 일괄 정리합니다.

> **`:core:vision`이 순수 JVM 모듈인 것은 결함이 아니라 설계입니다**(D-9.2). CameraX·MediaPipe Android SDK 래퍼는 `:core:vision`이 아니라 **`:feature:lab`** 에 둡니다. B그룹 명세 작성 시 이 경계를 혼동하지 마십시오.

---

### 8.3 진행 현황 (2026-08-08 기준)

**A그룹은 TASK-009~018의 10개 task입니다.** `:core:*` 추출이 끝났다고 A그룹이 끝난 것이 아닙니다 — `:feature:*` 이관이 A그룹에 포함되며, **이것이 완료되기 전에는 B그룹으로 넘어가지 않습니다.** 016·017이 끝나 남은 것은 **TASK-018 하나**입니다.

| Task | 상태 |
|---|---|
| TASK-009 (스캐폴딩 · 개명) | ✅ `DONE` (`retry_count` 2) |
| TASK-010 (`:core:ui`) · 011 (`:core:sensor`) · 012 (`:core:data`) | ✅ `DONE` |
| TASK-013 (JNI `RegisterNatives` 전환) | ✅ `DONE` (`retry_count` 1 — §8.1의 경계 위반으로 1회 반려) |
| TASK-014 (`:core:model`) | ✅ `DONE` |
| TASK-015 (`:core:analysis` + NDK 이관) | ✅ `DONE` — 최대 리스크였으나 1회 통과 |
| **TASK-016 (이력 결합 해소)** | ✅ `DONE` (`retry_count` 0) — 결합 4건 해소, 테스트 57→60 |
| **TASK-017 (`:feature:history` 이관)** | ✅ `DONE` (`retry_count` 0) — 6개 파일 이관, **라이브러리 모듈 Hilt 최초 실증** |
| **TASK-018 (`:feature:match`)** | ▶ **다음 차례 — 미등록** |
| B그룹(019~023) · C그룹(024~025) | 미등록 |

실제 등록 상태는 언제나 [`task-board.json`](task-board.json)이 SSOT입니다.

### 8.4 TASK-017 결과 — 라이브러리 모듈 Hilt는 동작한다

본 task의 최대 리스크는 기능 이관이 아니라 **이 저장소에 전례가 없던 "라이브러리 모듈에서의 Hilt"** 였습니다. 결과적으로 1회에 통과했고, 이후 `:feature:*` 이관(018·B그룹)은 더 이상 미검증 인프라를 다루지 않습니다.

- **구성**: `feature/history/build.gradle.kts`에 `tennisdoc.android.library.compose` + KSP + Hilt 플러그인을 적용하고, 프로젝트 의존성은 `:core:model`·`:core:ui`·`:core:data` 3개로 한정했습니다. Hilt **제공자(`AppModule`)는 `:app`에 그대로 두었고**, feature 모듈에는 모듈을 신설하지 않았습니다 — 복제하면 동일 타입에 바인딩이 둘 생깁니다.
- **`hiltViewModel()`의 위치**: `SessionDetailScreen`이 본문에서 스스로 ViewModel을 얻던 구조를 **파라미터 주입으로 바꾸고**, 호출을 `:app`의 `SESSION_DETAIL` composable로 올렸습니다. 그 지점은 `NavBackStackEntry` 스코프이므로 `SavedStateHandle`에 `sessionId`가 정상적으로 채워집니다. **이 스코프를 잘못 잡으면 상세 화면이 항상 빈 상태가 되며 컴파일로는 드러나지 않습니다** — 018에서 같은 패턴을 쓸 때 반드시 확인하십시오.

**"Hilt가 설정된 것처럼 보이는" 상태를 검사로 배제했습니다**

플러그인만 적용하고 컴파일러(`ksp`)를 빠뜨려도 애노테이션은 조용히 무시되고 빌드는 통과합니다. 그래서 명세는 **생성 산출물의 실재**를 인수 조건으로 요구했고, `feature/history/build/generated/ksp/`에서 `HistoryViewModel_Factory`·`SessionDetailViewModel_HiltModules` 등이 확인되었습니다. 018 이후의 feature 모듈 명세에도 이 조건을 유지하십시오.

**변이 검증 2건이 실제로 실패했습니다**

`:core:analysis` 의존성을 임시로 추가하자 `verifyModuleDependencies`가 `forbidden dependency`로 실패했고, `debugModeEnabled` 인자를 빼자 `:app:compileDebugKotlin`이 실패했습니다. 모듈 격리와 필수 인자화가 형식이 아니라 실효적임이 확인되었습니다.

---

## 9. 미확정 사항

- ~~**`SwingHistoryRepository`의 최종 소속**~~ — **해소됨.** `:core:data` 이전으로 확정하고 TASK-016 범위에 포함했습니다(§4.4).
- **`service/SwingAnalysisForegroundService`·`session/`의 최종 소속** — D-9.1에서 Phase 3까지 판단 유보. A그룹 진행 중에는 `:app`에 잔류.
- **CameraX·MediaPipe 의존성 도입 시점** — TASK-009(카탈로그 일괄) vs TASK-024(필요 시점). 도입 위치는 **`:feature:lab`으로 확정**되어 있습니다(D-9.2). `:core:vision`은 Android 의존 없는 순수 JVM 모듈로 유지합니다.
- **SPIKE-01 결과에 따른 C그룹 재설계 여부.**
- **ProGuard 규칙 정리(부채)** — 중복 1건·사문화 3줄. 상세와 정리 시점은 §8.2 참조.
- **추론 결과 정확성의 검증 수단 부재** — §4.1의 결함이 단위 테스트를 모두 통과한 근본 원인입니다. TASK-013의 `verifyJniBindings`로 **바인딩 재발**은 막혔으나, 분류 결과가 올바른지는 실기기가 필요하며 여전히 확인할 방법이 없습니다.
