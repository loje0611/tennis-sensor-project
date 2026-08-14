# 🧱 Phase 3 실행 계획 (Task Backlog)

> **작성일**: 2026-08-14
> **문서 성격**: Phase 3 (Lab 모드 MVP — 센서·비전 융합)의 **Task 백로그, 아키텍처 경계, 의존 그래프 및 합격 기준**을 정의하는 마스터 실행 계획서.
> - 단계 계획의 SSOT는 루트 [`README.md`](../README.md#-단계별-로드맵)입니다.
> - 아키텍처 및 기술 결정의 근거(Why)는 [`PRODUCT_DIRECTION.md`](PRODUCT_DIRECTION.md)에 있습니다.
> - 실제 Task 등록 상태는 [`task-board.json`](task-board.json)이 SSOT입니다.

---

## 1. 이 문서가 필요한 이유

`docs/AGENT_WORKFLOW.md`의 **단일 Task 처리 제약**에 따라 `task-board.json`에는 현재 진행 중인 단 하나의 Task만 등록됩니다. 아직 보드에 올라가지 않은 전체 Phase 3 로드맵과 세부 의존 관계를 보존하고, 데이터 인프라(D-7.2) 및 검증 도구(D-7.1)가 누락되지 않도록 실행 계획을 명문화합니다.

> **주의**: 본 문서에 명시된 `TASK-0XX` 번호는 **예정 번호**입니다. 실제 ID는 PM이 등록 시점에 `task-board.json`의 최대 번호 + 1로 자동 부여합니다 (예: `TASK-029`부터 시작).

---

## 2. Phase 3 핵심 목표 및 범위

Phase 3의 핵심은 **"Lab 모드 MVP 구축 — 센서-비전 융합(Fusion)을 통한 정밀 폼 진단 및 인과 코칭"** 입니다.

1. **센서-비전 시간 동기화**: IMU 고주파(50Hz) 임팩트 피크와 CameraX(30fps) 비전 포즈 프레임 간의 **임팩트 앵커(Impact Anchor) 동기화**.
2. **5단계 운동 체인 (Kinetic Chain)**: 비전 3단계(골반 ➔ 어깨 ➔ 손목)에 센서 라켓 링크(라켓 회전 ➔ 라켓 페이스 각도)를 결합한 통합 5단계 가속/감속 체인 분석.
3. **인과 코칭 (Causal Coaching)**: 단순 궤적 표시를 넘어 "임팩트 페이스 열림 원인이 상체 조기 회전인지, 손목 젖힘인지"를 판별하는 인과 진단.
4. **동기 리플레이 UI**: 비전 스켈레톤 영상 재생과 센서 IMU 시계열 그래프를 1:1로 락킹한 인터랙티브 분석 뷰.
5. **데이터 수집 파이프라인 (D-7.2)**: 모델 재학습 및 Phase 5 지식 증류를 위한 `(드릴 라벨, IMU 50Hz, PoseFrame)` 원시 세션 저장.
6. **Ablation 자동 채점 (D-7.1)**: 센서 제거 시 코칭 문장 변화율을 정량 측정하는 합격 검증 도구 구축.

---

## 3. 아키텍처 및 모듈 경계 (`:core:fusion` 신설)

D-9.2에 따라 순수 알고리즘(`:core:vision`, `:core:analysis`)과 융합 계층을 격리하고 단방향 의존을 보장합니다.

```text
[입력 계층]
  :core:sensor  (BLE / IMU 수신)
  :feature:lab  (CameraX / MediaPipe 실시간 스트림)
       │
       ▼
[분석 계층]
  :core:analysis (IMU 가속도·각속도 피크 / Edge Impulse 추론) ──┐
  :core:vision   (포즈 33개 관절 기하·각도·손목 궤적 - JVM)      ──┼──▶ :core:fusion (신규)
  :core:model    (공용 도메인 모델)                             ──┘          │
       │                                                                     │
       ▼                                                                     ▼
[영속화 / UI 계층]                                                      :feature:lab (Lab Screen)
  :core:data    (Room DB / 세션 원시 데이터 저장) ◀─────────────────────────┘
  :core:ui      (공용 테마 / 차트 / 툴팁)
```

- **`:core:fusion` [신규]**: 순수 Kotlin/JVM 모듈. Android 프레임워크 의존 없이 `PoseFrame` 시계열과 `ImuData` 시계열을 입력받아 동기화 및 융합 지표를 산출하여 고속 단위 테스트 가능.
- **`service/` & `session/` 재배치 (D-9.1)**: 기존 BLE 자동 연결 기반 세션을 **"사용자의 명시적 Lab 모드 선택 후 세션 생성"** UX로 전면 리팩터링.

---

## 4. Task 그룹별 백로그

### 🅰️ A그룹: 데이터 인프라 & 세션 아키텍처 개편

| 예정 ID | 제목 | 대상 모듈 | depends_on | 주요 내용 및 검증 |
|---|---|---|---|---|
| **TASK-029** | Room DB v7 마이그레이션 및 Lab 세션 원시 데이터 스키마 구축 (D-7.2) | `:core:data`, `:core:model` | `TASK-028` | `sessionType`(`MATCH`/`LAB`), 드릴 라벨, IMU 50Hz 원시 BLOB, `PoseFrame` JSON 영속화 스키마 및 DB 마이그레이션 테스트 |
| **TASK-030** | 세션 라이프사이클 UX 개편 (모드 선택 기반 세션 생성 및 `service/` 정리) | `:app`, `:core:data` | `TASK-029` | BLE 자동 생성 탈피 ➔ Lab/Match 진입 시 명시적 세션 생성, `SwingAnalysisForegroundService` 역할 분리 및 수명주기 정리 |

---

### 🅱️ B그룹: `:core:fusion` 모듈 신설 및 융합 엔진

| 예정 ID | 제목 | 대상 모듈 | depends_on | 주요 내용 및 검증 |
|---|---|---|---|---|
| **TASK-031** | `:core:fusion` 모듈 신설 및 데이터 계약 정의 | `:core:fusion`, `:core:model` | `TASK-029` | 순수 Kotlin JVM 모듈 신설, `FusedSwing`, `SyncAnchor`, `KineticChain5Stage` 데이터 모델 및 컨벤션 플러그인 설정 |
| **TASK-032** | 임팩트 앵커(Impact Anchor) 시간 동기화 알고리즘 구현 | `:core:fusion` | `TASK-031` | IMU 자이로/가속도 피크(고주파)와 비전 손목 속도 피크(30fps) 간의 시간차(dt) 보정 및 락킹 알고리즘 (골든 픽스처 단위 테스트) |
| **TASK-033** | 5단계 통합 운동 체인(Kinetic Chain) 분석 엔진 | `:core:fusion` | `TASK-032` | 비전(골반 ➔ 어깨 ➔ 손목) + 센서(라켓 스윙 ➔ 라켓 페이스) 결합 가속 순서 및 에너지 전달 효율(지연 시간 ms) 수치화 |
| **TASK-034** | 센서-비전 융합 인과 코칭 및 진단 룰 엔진 | `:core:fusion` | `TASK-033` | 라켓 페이스 각도(센서)와 상체 회전 타이밍(비전)의 상관관계를 분석하여 "원인 ➔ 결과" 매핑 코칭 문장/태그 생성 |

---

### 🅲 C그룹: 검증 도구 & 이상 탐지

| 예정 ID | 제목 | 대상 모듈 | depends_on | 주요 내용 및 검증 |
|---|---|---|---|---|
| **TASK-035** | Ablation 자동 채점 검증 도구 구현 (D-7.1) | `:core:fusion` | `TASK-034` | 센서 포함 vs 비전 단독 리포트 비교: 진단 태그 Jaccard 거리 및 코칭 문장 임베딩 코사인 유사도 자동 측정 테스트 스위트 |
| **TASK-036** | 개인 Baseline 통계적 이상 탐지 모듈 (D-7.4) | `:core:fusion`, `:core:data` | `TASK-034` | 유저 평균 대비 z-score 및 이동평균 기반 세션 후반 폼 붕괴/피로도 누적 탐지 |

---

### 🅳 D그룹: `:feature:lab` UI 및 동기화 리플레이

| 예정 ID | 제목 | 대상 모듈 | depends_on | 주요 내용 및 검증 |
|---|---|---|---|---|
| **TASK-037** | 실시간 센서-비전 스트림 융합 파이프라인 연동 | `:feature:lab` | `TASK-030`, `TASK-034` | CameraX 프레임 스트림과 BLE 센서 수신 큐를 실시간 버퍼링하여 `FusionEngine`으로 전달하는 백그라운드 파이프라인 |
| **TASK-038** | Lab 세션 드릴 가이드 및 실시간 융합 피드백 UI | `:feature:lab`, `:core:ui` | `TASK-037` | 드릴 선택(포핸드 스트로크, 탑스핀 등), 실시간 스윙 감지 카운터, 임팩트 즉시 요약 오버레이 |
| **TASK-039** | 동기 리플레이(Synchronized Replay) 및 정밀 진단 뷰어 | `:feature:lab`, `:core:ui` | `TASK-038` | 비전 스켈레톤 영상 시크바와 IMU 파형 그래프의 타임라인 동기화 뷰, 툴팁 배치 불변식(TASK-007 규정) 준수 UI |

---

## 5. 의존 그래프 (Dependency Graph)

```text
TASK-028 (Phase 2 완결)
   │
   ▼
TASK-029 (DB v7 마이그레이션 & 원시 스키마) ──────────────┐
   │                                                     │
   ├──────────────────────────────┐                      │
   ▼                              ▼                      │
TASK-030 (세션 라이프사이클 UX)   TASK-031 (:core:fusion 신설)    │
   │                              │                      │
   │                              ▼                      │
   │                      TASK-032 (임팩트 앵커 동기화)     │
   │                              │                      │
   │                              ▼                      │
   │                      TASK-033 (5단계 운동 체인)       │
   │                              │                      │
   │                              ▼                      │
   │                      TASK-034 (인과 코칭 룰 엔진)     │
   │                        │             │              │
   │            ┌───────────┴─────────┐   │              │
   │            ▼                     ▼   ▼              │
   │    TASK-035 (Ablation 도구)  TASK-036 (이상 탐지)    │
   │                                      │              │
   └──────────────────────────────┬───────┘              │
                                  ▼                      │
                          TASK-037 (실시간 융합 파이프라인) │
                                  │                      │
                                  ▼                      │
                          TASK-038 (드릴 가이드 UI)       │
                                  │                      │
                                  ▼                      │
                          TASK-039 (동기 리플레이 & 정밀 뷰어)◀┘
                                  │
                                  ▼
                         [Phase 3 Lab MVP 완결]
```

---

## 6. Phase 3 합격 판정 기준 (Ablation Test Gate)

Phase 3의 최종 종료 판정은 **TASK-035 (Ablation 검증 도구)** 의 자동 채점 결과에 의해서만 결정됩니다.

1. **테스트 데이터셋**: 실기기에서 수집된 20개 이상의 스윙 세션 데이터 `(IMU 50Hz, 30fps PoseFrame, 정답 드릴 라벨)`.
2. **검증 가설**: 센서 데이터(IMU)를 주입했을 때와 비전 단독일 때 생성되는 진단 리포트가 유의미하게 달라져야 함.
3. **정량 합격 기준**:
   - **조언 차별화 비율**: 20개 스윙 중 **최소 60% 이상**에서 진단 태그 변경(Jaccard Distance $\ge 0.4$) 또는 코칭 핵심 문장 수정 발생.
   - **원인 판별률**: 라켓 페이스 각도 이상 및 임팩트 타이밍 결함의 인과 관계 설명 포함 여부 **100% 충족**.
   - **동기화 정밀도**: 임팩트 앵커 시간 오차 **$\le \pm 33\text{ms}$ (1 비전 프레임 이내)**.

---

## 7. 실행 원칙 및 PM 가이드라인

1. **Single Task Processing 준수**:
   - 보드에는 항상 하나의 Task만 활성화(`SPEC_READY` ➔ `DEVELOPER` ➔ `TESTER`).
   - `TASK-029`부터 순차적으로 명세를 발행하고 완료 후 다음 단계로 진행.
2. **사전 커밋 필수**:
   - PM은 명세 작성 및 태스크보드 갱신 후 반드시 커밋을 완료하고 `docs/turn.json`을 핸드오프.
3. **골든 픽스처(Golden Fixture) 검증**:
   - B그룹의 동기화 및 5단계 운동 체인은 시뮬레이션 및 사전 수집된 센서-비전 JSON 픽스처 기반 JVM 테스트로 100% 검증.
