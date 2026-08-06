# 🧱 Phase 2 실행 계획 (Task Backlog)

> **최종 갱신**: 2026-08-06
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
| **산출물** | 코드가 아니라 **측정 결과 문서**. TASK-021 spec의 입력으로 사용 |
| **수행 시점** | A그룹(모듈 분리)과 **병행 가능**. C그룹 착수 전까지 완료 |

> 스파이크 결과가 부정적일 경우의 대안(해상도·프레임레이트 하향, 임팩트 ±2초 구간만 처리 — D-6의 자동 클립 전략)까지 함께 기록합니다.

---

## 4. A그룹 — 멀티모듈 분리 + 개명

모듈 경계는 **D-9**, 개명 범위는 **D-8**을 따릅니다.

| 예정 ID | 제목 | depends_on | 검증 방법 |
|---|---|---|---|
| **TASK-009** | Gradle 멀티모듈 스캐폴딩 · 버전 카탈로그 정비 · **앱 개명(D-8)** | `[]` | `./gradlew test`·`assembleDebug` 그린 + 모듈 의존 방향 규칙 검증 |
| **TASK-010** | `:core:ui` 추출 (테마 + 공용 Compose 컴포넌트) | `[009]` | 컴파일 + 기존 화면 렌더 회귀 없음 |
| **TASK-011** | `:core:sensor` 추출 (BLE · IMU 파서) | `[009]` | `ImuPayloadParserTest` 이관 후 통과 |
| **TASK-012** | `:core:data` 추출 (Room · DataStore) | `[009]` | `SwingSessionDaoTest` 통과, `schemas/` 경로 유지 |
| **TASK-013** | `:core:analysis` 추출 (Kinematic · Coaching · **Edge Impulse NDK**) | `[009, 011]` | `KinematicAnalyzerTest`·`CoachingEngineTest`·`VolleyDetectorTest`·`SwingInferenceBufferTest` 통과 |
| **TASK-014** | `:feature:history` 신설 및 이관 | `[010, 012]` | 이력 조회 화면 컴파일 + ViewModel 단위 테스트 |
| **TASK-015** | `:feature:match` 이관 및 v1 내비게이션 비활성화(보존) | `[010, 011, 012, 013]` | 라우트 목록에 match 부재 + 모듈 독립 컴파일 |

### 주의사항

- **TASK-009의 `target_project`는 개명 전 값(`SwingSenseAI`)** 입니다. 이 task가 디렉토리를 `TennisDocAI/`로 바꾸므로, **TASK-010부터는 `TennisDocAI`** 를 사용합니다.
- TASK-009 범위에 **후속조치 #11·#12 문서 갱신을 반드시 포함**합니다 — `README.md`(디렉토리 구조·모듈 구조), `docs/AGENT_WORKFLOW.md`(§7 테스트 명령표), 서브프로젝트 `AI_README.md`. 특히 `AI_README.md`는 **Tester가 테스트 명령을 조회하는 파일**이라, 누락하면 이후 모든 task의 QA가 잘못된 경로를 참조합니다.
- **TASK-013이 최대 리스크**입니다. `externalNativeBuild`(CMake 3.22.1) + `abiFilters` + JNI 브리지를 라이브러리 모듈로 옮기는 작업이라 QA 3회 소진 후 `BLOCKED` 가능성이 가장 높습니다. 별도 task로 격리한 이유입니다.

---

## 5. B그룹 — 비전 알고리즘 Kotlin 포팅

D-9.2에 따라 **`:core:vision`(순수 `kotlin("jvm")` 모듈)** 에 구현합니다. Android 의존이 없으므로 계측 기기 없이 검증됩니다.

| 예정 ID | 제목 | 원본 (Phase 1 산출물) | depends_on |
|---|---|---|---|
| **TASK-016** | `:core:vision` 모듈 + `PoseFrame` 데이터 계약 + 관절 각도 계산 | `src/angle_calculator.py` (TASK-002) | `[009]` |
| **TASK-017** | 속도 계산 + 다중 스윙 임팩트 감지 | `src/impact_detector.py` (TASK-003) | `[016]` |
| **TASK-018** | 스윙 궤적 분류 (Topspin / Flat / Slice) | `src/swing_path.py` (TASK-004) | `[017]` |
| **TASK-019** | 운동 체인 분석 | `src/kinetic_chain.py` (TASK-005) | `[017]` |
| **TASK-020** | 스윙 진단 · 피드백 생성 | `src/swing_diagnosis.py` (TASK-006) | `[016, 018, 019]` |

### 골든 픽스처 원칙 (필수)

B그룹 전 task의 Acceptance Criteria에 다음이 **반드시** 포함되어야 합니다.

> **동일 입력에 대해 Python 구현과 수치적으로 동일한 결과를 산출한다(허용 오차 명시).**

Python 쪽에서 입출력 골든 픽스처(JSON)를 추출해 Kotlin 테스트 리소스로 고정하는 방식입니다. 이것이 없으면 **"포팅했다"를 검증할 방법 자체가 존재하지 않습니다.** 픽스처 추출 도구는 TASK-016 범위에 포함하고 이후 task가 재사용합니다.

> Phase 1의 TASK-007(오버레이 렌더링)·TASK-008(Streamlit UI)은 **포팅 대상이 아닙니다.** 렌더링·UI는 Android에서 완전히 다른 구현이 되므로 Phase 3 Lab UI에서 새로 설계합니다. 다만 TASK-007 spec v2가 정의한 **툴팁 배치 불변식(겹침 금지·프레임 내부·배너 비간섭)** 은 Android UI에서도 동일하게 적용해야 할 요구사항이므로 Phase 3 spec 작성 시 참조합니다.

---

## 6. C그룹 — 카메라 · MediaPipe

| 예정 ID | 제목 | depends_on | 비고 |
|---|---|---|---|
| **TASK-021** | MediaPipe Pose Landmarker 통합 → `PoseFrame` 산출 | `[009, 016]` + **SPIKE-01** | Android SDK 바인딩은 D-9.2에 따라 `:feature:lab`에 배치 |
| **TASK-022** | CameraX 프레임 파이프라인 (`:feature:lab` 골격) | `[021]` | |

CameraX·MediaPipe 의존성은 현재 `libs.versions.toml`에 없습니다(후속조치 #4). TASK-009의 버전 카탈로그 정비 또는 TASK-021에서 도입합니다.

---

## 7. 의존 그래프

```text
SPIKE-01 (정규 task 아님) ─────────────────────────┐
                                                   │
009 ─┬─ 010 ─┬─ 014                                │
     ├─ 011 ─┼─ 013 ─┬─ 015                        │
     ├─ 012 ─┘       │                             │
     │               │                             │
     └─ 016 ─┬─ 017 ─┬─ 018 ─┬─ 020                │
             │       └─ 019 ─┘                     │
             └────────── 021 ◀────────────────────┘
                          └─ 022
```

비순환이며, A그룹과 B그룹은 TASK-009 이후 서로 독립적으로 진행 가능합니다. 다만 **단일 task 처리 제약** 때문에 실제 실행은 순차입니다.

---

## 8. 실행 규칙

- PM은 계획 전체를 사용자에게 먼저 보고한 뒤 **첫 task 하나만 생성·개시**합니다(`prompts/pm-agent.md`). 위 14개를 한꺼번에 등록하는 것은 규약 위반입니다.
- 각 task가 `DONE` 또는 `BLOCKED`에 도달한 뒤 다음 task를 생성합니다.
- 기존 task의 요구사항 결함이 발견되면 신규 등록이 아니라 **Step 1A 명세 개정**(`AGENT_WORKFLOW.md` §5)으로 처리합니다.

---

## 9. 미확정 사항

- **`service/SwingAnalysisForegroundService`·`session/`의 최종 소속** — D-9.1에서 Phase 3까지 판단 유보. A그룹 진행 중에는 `:app`에 잔류.
- **CameraX·MediaPipe 의존성 도입 시점** — TASK-009(카탈로그 일괄) vs TASK-021(필요 시점).
- **SPIKE-01 결과에 따른 C그룹 재설계 여부.**
