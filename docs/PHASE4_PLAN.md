# 🧱 Phase 4 실행 계획 (Task Backlog)

> **작성일**: 2026-08-17  
> **문서 성격**: Phase 4 (AI 코치 정밀 처방 리포트 — LLM 자연어 코칭 파이프라인)의 **Task 백로그, 아키텍처 경계, 의존 그래프 및 합격 기준**을 정의하는 마스터 실행 계획서.  
> - 단계 계획의 SSOT는 루트 [`README.md`](../README.md#-단계별-로드맵)입니다.  
> - 아키텍처 및 기술 결정의 근거(Why)는 [`PRODUCT_DIRECTION.md`](PRODUCT_DIRECTION.md) D-7.5에 있습니다.  
> - 실제 Task 등록 상태는 [`task-board.json`](task-board.json)이 SSOT입니다.  

---

## 1. 이 문서가 필요한 이유

`docs/AGENT_WORKFLOW.md`의 **단일 Task 처리 제약**에 따라 `task-board.json`에는 현재 진행 중인 단 하나의 Task만 활성화됩니다. 아직 보드에 올라가지 않은 Phase 4의 전체 로드맵, 모듈 경계(`:core:coach` 신설 등), D-7.5 설계 제약(수치 조작 방지, 프라이버시 보호) 및 Fallback 구조를 사전에 명문화하여 일관된 설계를 보존합니다.

> **주의**: 본 문서에 명시된 `TASK-0XX` 번호는 **예정 번호**입니다. 실제 ID는 PM이 등록 시점에 `task-board.json`의 최대 번호 + 1로 자동 부여합니다 (`TASK-043`부터 시작).

---

## 2. Phase 4 핵심 목표 및 범위

Phase 4의 핵심은 **"센서-비전 융합 지표를 바탕으로 한 개인화 AI 코치 정밀 처방 리포트(AI Coach Prescription Report) 생성"** 입니다.

1. **결정론적 수치와 자연어 표현의 엄격한 분리 (D-7.5 가드레일)**:
   - LLM은 각도, 체인 지연(ms), 효율(%), 스윙 횟수를 임의로 생성(Hallucination)하지 않습니다.
   - 결정론적 분석 엔진(`:core:fusion`, `:core:data`)이 산출한 정밀 집계 데이터(JSON)를 Context로 제공하고, LLM은 **인과 분석의 쉬운 설명, 우선순위 선정, 맞춤형 드릴 처방, 코칭 톤 조절**을 담당합니다.
2. **프라이버시 최우선 지표 전송 (Privacy-First Payload)**:
   - 카메라 영상이나 33개 관절 랜드마크 시계열 원시 데이터는 전송하지 않으며, 집계된 통계치, 결함 인과 태그, 대표 이상치 스윙 지표만 최소화하여 전송합니다.
3. **오프라인/에러 대비 룰 기반 Fallback 엔진**:
   - 네트워크 단절, API Key 미설정, API 호출 실패 시에도 `:core:fusion` 진단 엔진 기반 **로컬 룰 기반 처방 리포트**가 즉시 생성되어 무중단 UX를 제공합니다.
4. **리포트 영속화 (Room DB v9)**:
   - 생성된 처방 리포트는 DB에 영속화되어, 이후 오프라인 환경에서도 히스토리에서 언제든 다시 열람할 수 있습니다.
5. **Clean Sunlit Court 테마에 맞춘 프리미엄 스포츠 리포트 UI**:
   - 요약 총평, 핵심 결함 원인 분석(Causal Diagnosis), 다음 세션 집중 과제(Action Items), 맞춤 추천 드릴 3종 카드로 구성된 미려한 UI.

---

## 3. 아키텍처 및 모듈 경계 (`:core:coach` 신설)

D-9 및 D-7.5에 따라 LLM 통신 및 프롬프트 조립 계층을 별도 JVM 모듈로 격리하고 단방향 의존을 유지합니다.

```text
[도메인 / 융합 계층]
  :core:model   (공용 도메인 모델: AiCoachReport, PrescriptionContext) ◀──┐
  :core:fusion  (세션 지표 집계: SessionPrescriptionContextBuilder)       │
       │                                                                 │
       ▼                                                                 │
[코칭 / LLM 계층]                                                        │
  :core:coach   [신규: 순수 JVM]                                          │
    ├── PromptBuilder (System Prompt + JSON Context 주입)                │
    ├── StructuredReportParser (JSON Schema ➔ AiCoachReport 파싱)        │
    ├── LocalRuleBasedCoachEngine (오프라인 Fallback 룰 엔진)             │
    └── LlmCoachClient (Gemini API / REST Client / Mock Client)          │
       │                                                                 │
       ▼                                                                 │
[영속화 계층]                                                            │
  :core:data    (Room DB v9: aiCoachReport 캐싱 / DataStore API Key 설정) │
       │                                                                 │
       ▼                                                                 │
[UI / 화면 계층]                                                         │
  :core:ui      (AiCoachReportCard, PrescriptionDrillCard, 톤 선택기)─────┘
       │
       ├─────────────────────────────────────────┐
       ▼                                         ▼
  :feature:lab (세션 완료 다이얼로그 즉시 처방)   :feature:history (세션 상세 리포트 탭)
```

- **`:core:coach` [신규]**: 순수 Kotlin JVM 모듈. Android 의존성 없이 프롬프트 빌드, JSON 파싱, Fallback 로직을 100% JVM 단위 테스트로 검증.
- **모듈 의존성 규칙 (`verifyModuleDependencies`)**:
  - `:core:coach` ➔ `{:core:model, :core:fusion}`
  - `:core:data` ➔ `{:core:model}` (엔티티에 JSON 문자열 또는 도메인 모델 매핑)
  - `:feature:history` ➔ `{:core:model, :core:ui, :core:data, :core:fusion, :core:coach}`
  - `:feature:lab` ➔ `{:core:model, :core:ui, :core:vision, :core:data, :core:analysis, :core:fusion, :core:coach}`

---

## 4. Task 그룹별 백로그 (총 8개 Task)

### 🅰️ A그룹: 데이터 스키마 & 프롬프트 컨텍스트 인프라

| 예정 ID | 제목 | 대상 모듈 | depends_on | 주요 내용 및 검증 |
|---|---|---|---|---|
| **TASK-043** | Room DB v9 마이그레이션 및 AI 코치 도메인 계약 정의 | `:core:model`, `:core:data` | `TASK-042` | `AiCoachReport`, `PrescriptionSection`, `DrillPrescription` 도메인 모델 추가, Room DB v9 스키마(세션 엔티티 `aiCoachReportJson` 컬럼 추가) 및 마이그레이션 단위 테스트 |
| **TASK-044** | 세션 융합 지표 집계 및 LLM Context Builder 구현 | `:core:fusion` | `TASK-043` | 5단계 체인 딜레이 분포, 페이스 각도 오차, 주요 결함 스윙, Baseline 이상치 데이터를 프라이버시 보호형 JSON으로 집계하는 `SessionPrescriptionContextBuilder` 및 골든 픽스처 단위 테스트 |

---

### 🅱️ B그룹: `:core:coach` 모듈 신설 및 LLM / Fallback 엔진

| 예정 ID | 제목 | 대상 모듈 | depends_on | 주요 내용 및 검증 |
|---|---|---|---|---|
| **TASK-045** | `:core:coach` 모듈 신설 및 프롬프트 템플릿 / 구조화 파서 구현 | `:core:coach`, `root` | `TASK-044` | 순수 JVM 모듈 신설, `verifyModuleDependencies` 갱신, System Instruction & Few-shot 프롬프트 템플릿, Structured JSON 응답 파서 및 Mock LLM 단위 테스트 |
| **TASK-046** | 로컬 룰 기반 Fallback 엔진 & LLM API 클라이언트 구현 | `:core:coach`, `:core:data` | `TASK-045` | 오프라인용 `LocalRuleBasedCoachEngine` 구현, `GeminiCoachClient`(Google AI / REST) 연동, 네트워크 에러 시 Fallback 자동 전환 로직 및 Fake 네트워크 단위 테스트 |

---

### 🅲 C그룹: UI 컴포넌트 & 프레젠테이션 계층

| 예정 ID | 제목 | 대상 모듈 | depends_on | 주요 내용 및 검증 |
|---|---|---|---|---|
| **TASK-047** | AI 코치 리포트 스포츠 카드 UI 컴포넌트 구현 | `:core:ui` | `TASK-043` | Clean Sunlit Court 라이트 테마 기반 `AiCoachReportCard`, `CausalDiagnosisSection`, `DrillRecommendationCard`, 스켈레톤 로딩 애니메이션 및 Compose 렌더링 테스트 |
| **TASK-048** | `:feature:lab` 세션 완료 다이얼로그 AI 코칭 리포트 연동 | `:feature:lab` | `TASK-046`, `TASK-047` | 훈련 완료 다이얼로그(`SessionCompletionDialog`)에 [🤖 AI 코치 처방받기] 버튼 추가, 생성 중 비동기 로딩 표시, 다이얼로그 내 원터치 처방 요약 렌더링 |
| **TASK-049** | `:feature:history` 세션 상세 화면 AI 처방 리포트 탭 통합 | `:feature:history` | `TASK-046`, `TASK-047` | 세션 상세 화면에 [스윙 분석 / 동기 리플레이 / AI 코치 처방] 탭 구조 구현, 기 생성된 리포트 캐시 로딩 및 미생성 세션 즉시 분석 요청 지원 |

---

### 🅳 D그룹: 환경설정 & E2E 종합 검증

| 예정 ID | 제목 | 대상 모듈 | depends_on | 주요 내용 및 검증 |
|---|---|---|---|---|
| **TASK-050** | AI 코치 환경설정 UX 구현 및 전 구간 통합 E2E 검증 | `:app`, `:core:data`, `:core:coach` | `TASK-048`, `TASK-049` | DataStore 기반 API Key 입력, 코칭 톤(격려형/분석형/엄격한 코치) 설정 UI, 전체 파이프라인(세션 종료 ➔ 컨텍스트 조립 ➔ LLM 호출/Fallback ➔ DB 저장 ➔ UI 표시) E2E 통합 테스트 |

---

## 5. 의존 그래프 (Dependency Graph)

```text
TASK-042 (Phase 3 완결)
   │
   ▼
TASK-043 (도메인 계약 & DB v9 마이그레이션) ────────┐
   │                                              │
   ▼                                              ▼
TASK-044 (융합 지표 Context Builder)      TASK-047 (:core:ui 스포츠 리포트 카드)
   │                                              │
   ▼                                              │
TASK-045 (:core:coach 신설 & 프롬프트 파서)        │
   │                                              │
   ▼                                              │
TASK-046 (Fallback 룰 엔진 & Gemini 클라이언트)    │
   │                                              │
   ├──────────────────────┬───────────────────────┤
   ▼                      ▼                       │
TASK-048 (Lab 완료 연동)   TASK-049 (History 탭 연동) │
   │                      │                       │
   └──────────────────────┴───────────────────────┘
                          │
                          ▼
             TASK-050 (설정 UX & E2E 종합 검증)
                          │
                          ▼
                 [Phase 4 완결]
```

---

## 6. Phase 4 합격 판정 기준 (Gate Criteria)

Phase 4의 완료는 다음 4가지 핵심 기준을 만족해야 합니다.

1. **수치 불변성 검증 (Zero Hallucination Gate)**:
   - 생성된 리포트에 포함된 모든 정량 지표(임팩트 각도, 체인 딜레이 ms 등)는 원본 `SessionPrescriptionContext`의 수치와 정확히 일치해야 함.
2. **오프라인 Fallback 100% 무중단 보장**:
   - 비행기 탑승 모드(네트워크 단절) 또는 API Key 미설정 상태에서도 크래시 없이 로컬 룰 기반 리포트가 1초 이내에 렌더링되어야 함.
3. **영속화 및 캐싱 보장**:
   - 한 번 생성된 AI 리포트는 Room DB에 저장되어 앱 재기동 후에도 네트워크 호출 없이 즉시 재열람 가능해야 함.
4. **모듈 의존성 및 빌드 완전성**:
   - `verifyModuleDependencies`, `test`, `assembleDebug`가 0 실패로 통과해야 함.
