# 🤖 에이전트 기반 개발 워크플로우 (Agent-Driven Development Workflow)

이 문서는 본 모노레포에서 **PM · Developer · Tester 세 개의 AI 에이전트**가 파일 기반 핸드오프를 통해 협업하여 기능을 개발하는 자동화 파이프라인을 설명합니다. 각 에이전트의 상세 지시문은 [`prompts/`](../prompts/) 디렉토리에 있습니다.

---

## 1. 개요

사용자가 PM 에이전트에게 기능 요청을 전달하면, 세 에이전트가 순차적으로 다음을 수행합니다.

1. **PM (Product Manager)** — 요청을 task로 등록하고 명세(spec)를 작성.
2. **Developer** — 명세를 읽고 대상 서브프로젝트에 코드를 구현.
3. **Tester (QA)** — 테스트를 작성·실행하고 합격/불합격을 판정, 최종 커밋.

에이전트 간 제어권은 **파일 핸드오프**로 전달되며, 한 번에 **하나의 task만** 처리합니다. 단, **PM은 `turn.json` 순환에 참여하지 않습니다.** PM은 사용자의 지령으로만 기동하며, `{"next_agent": "none"}` 은 *사용자의 차례* 를 뜻합니다.

```text
[User] ─▶ PM ─▶ (spec 작성) ─▶ Developer ─▶ (구현) ─▶ Tester ─▶ (QA)
   ▲                                  ▲                        │
   │                                  └──── QA_FAILED 재시도 ───┤
   │                                                            │
   │                        QA_PASSED ─▶ Developer가 커밋/푸시 ─▶ DONE
   │                                                            │
   └──── BLOCKED ─▶ turn=none ─▶ 사용자가 판단 ────────────────┘
            (재시도 소진 · 명세 결함 · 검증 불가)
   │
   └─ 사용자가 개정 지시 ─▶ PM이 해당 spec을 제자리 개정 ─▶ 동일 TASK-ID 재개
```

> **Tester는 PM에게 직접 핸드오프하지 않습니다.** 명세 결함을 발견해도 `turn.json`에 `pm`을 쓰지 않고 `none`으로 멈춥니다. 파이프라인이 멈추면 사용자가 QA 리포트를 읽고 PM에게 개정을 지시합니다.

---

## 2. 핵심 파일 (State & Handoff)

| 파일 | 역할 |
|---|---|
| [`docs/task-board.json`](task-board.json) | 모든 task의 단일 원천(SSOT). 상태·재시도 횟수·라우팅 정보 저장 |
| [`docs/turn.json`](turn.json) | 현재 제어권을 가진 에이전트 지정 (`{"next_agent": ..., "task_id": ...}`) |
| `docs/specs/{TASK-ID}-{slug}.md` | PM이 작성하는 기능 명세서 |
| `docs/qa/{TASK-ID}-report.md` | Tester가 작성하는 QA 리포트 |
| `{target_project}/AI_README.md` | 서브프로젝트별 빌드/테스트/실행 명령 컨텍스트 |

### task 항목 스키마 (`task-board.json`)

```json
{
  "id": "TASK-001",
  "title": "기능 요약",
  "raw_request": "사용자의 원본 요청",
  "target_project": "tennis-vision-analyzer",
  "depends_on": ["TASK-003"],
  "spec_path": "docs/specs/TASK-001-feature-slug.md",
  "status": "DRAFT",
  "retry_count": 0,
  "created_at": "2026-...Z",
  "updated_at": "2026-...Z"
}
```

> - `id`가 `TASK-000`이고 `status`가 `TEMPLATE`인 항목은 **구조 템플릿**이며 모든 에이전트가 무시한다.
> - `depends_on`은 이 task를 구현하기 전에 `DONE`이어야 하는 선행 `TASK-ID` 배열이다(없으면 `[]`). 각 task는 **독립적으로 단위 테스트 가능한 기능 단위**로 분할하고, task 간 의존성은 반드시 비순환(acyclic)이어야 한다.

---

## 3. 상태 머신 (Status Lifecycle)

| 상태 | 설정 주체 | 의미 / 다음 동작 |
|---|---|---|
| `DRAFT` | PM | task 등록됨, 명세 작성 대기 |
| `SPEC_READY` | PM | 명세 완료 → Developer에게 핸드오프 |
| `DEV_DONE` | Developer | 구현 완료 → Tester에게 핸드오프 |
| `QA_PASSED` | Tester | 테스트 통과 → Developer가 커밋/푸시 |
| `QA_FAILED` | Tester | 테스트 실패(재시도 < 3) → Developer에게 반환 |
| `BLOCKED` | Tester | 루프 중단 → `turn.json`을 `none`으로 두고 **사용자 판단 대기**. 원인 3종은 아래 참조 |
| `DONE` | Developer | 커밋/푸시 완료, task 종료 |

#### `BLOCKED` 진입 원인 3종

Tester는 아래 세 경우에 `BLOCKED`를 설정하며, QA 리포트 말미의 `## Escalation: ...` 섹션에 원인을 명시합니다.

| 원인 | 조건 | `retry_count` | 해소 방법 |
|---|---|---|---|
| 재시도 한도 소진 | 동일 spec에서 QA 3회 실패 | 3 | 사용자 개입 |
| **명세 결함** | 요구사항이 모순·구현 불가이거나, 소스를 다시 읽는 방법으로만 "검증" 가능함 | **증가시키지 않음** | 사용자가 PM에게 개정 지시 → Step 1A |
| **검증 불가** | 선언된 테스트 명령을 아예 실행할 수 없음(툴체인·SDK·기기 부재) | **증가시키지 않음** | 환경 보강 또는 Testing Instructions 개정 |

> **환경 부재는 절대 합격 사유가 아닙니다.** 실행하지 못한 판정 기준은 `QA_PASSED`가 아니라 `BLOCKED`(검증 불가)로 처리합니다.

> **재개(reopen) 전이**: `DONE` 또는 `BLOCKED`에 도달한 task라도, 해당 기능의 요구사항 자체에 결함이 발견되면 **PM이 `DRAFT`로 되돌려** 개정 사이클을 시작할 수 있습니다(§5 참조). 이 전이는 **PM만** 수행하며, `retry_count`를 `0`으로 리셋합니다. Developer·Tester는 어떤 경우에도 종단 상태를 되돌리지 않습니다.

### `turn.json` 핸드오프 규칙

- `next_agent`가 가질 수 있는 값은 **`developer` · `tester` · `none`** 세 가지입니다. **`pm`은 사용되지 않습니다** — PM은 사용자 지령으로만 기동하므로 `turn.json`으로 깨어나지 않습니다.
- `{"next_agent": "none", "task_id": ""}` 은 **파이프라인 정지 = 사용자 차례** 를 뜻하며, task 사이클 종료(`DONE`) 또는 `BLOCKED` 시 설정됩니다. 에이전트는 이 상태를 스스로 해제하지 않습니다.
- Developer와 Tester는 이 파일을 **블로킹 방식으로 감시**합니다. 짧은 읽기를 반복하는 폴링 대신, **자신의 차례로 바뀔 때만 반환되는 단일 대기 명령**(내부적으로 5초 간격 확인)을 사용해 전이 시점에 **정확히 한 번** 깨어납니다. **PM은 감시자를 실행하지 않습니다.**
- **유휴 상태에서는 어떤 출력·알림도 내지 않습니다.** 차례가 아닌 사이클을 보고하면 동일한 "idle" 알림만 반복되어 로그가 오염됩니다. 감시자는 에이전트당 **하나만** 실행하고, 보고는 **차례 진입·핸드오프 시점에만** 합니다.

---

## 4. 에이전트별 역할

### 🧭 PM 에이전트 ([`prompts/pm-agent.md`](../prompts/pm-agent.md))
- **사용자 지령으로만 기동**합니다. `turn.json` 감시자를 실행하지 않고, 자기 차례(`next_agent: "pm"`)를 갖지 않습니다.
- 사용자 요청 수신 → 고유 `TASK-ID` 계산, `FEATURE_SLUG` 생성, `target_project` 결정.
- `task-board.json`에 task 등록 후 명세서 작성 → `SPEC_READY`로 전이 → Developer에게 핸드오프하고 **제어권을 사용자에게 반환**.
- **단일 task 처리**: 한 task가 `DONE`(성공) 또는 `BLOCKED`(중단)에 도달할 때까지 다음 task를 만들지 않음.
- **신규 등록 vs 명세 개정 라우팅**: 사용자 요청이 기존 기능의 요구사항 결함이면 신규 task를 만들지 않고 기존 spec을 제자리 개정함(§5).
- **`{target_project}/` 하위의 어떤 파일도 수정하지 않음** — 언어가 아니라 경로 기준 금지. QA 판정도 내리지 않음.

### 💻 Developer 에이전트 ([`prompts/developer-agent.md`](../prompts/developer-agent.md))
- `SPEC_READY` → 명세 구현(`{target_project}/` 내부로 한정) → `DEV_DONE`.
- `QA_FAILED` → QA 리포트를 읽고 버그 수정 → `DEV_DONE`.
- `QA_PASSED` → 상태 파일(`task-board.json` → `DONE`, `turn.json` → `none`)을 **먼저** 갱신한 뒤, **해당 task의 산출물 경로만 명시적으로 스테이징**하여 커밋/푸시 → `DONE`.
- **완료 정의는 명세의 Functional Requirements 구현까지**입니다. Acceptance Criteria 충족 여부의 판정은 Tester 전권이며, Developer는 빌드·테스트를 실행해도 `QA_PASSED`를 스스로 설정하지 않습니다.
- **테스트 소스와 `docs/specs/**`의 내용은 수정하지 않습니다.** 명세가 명시적으로 요구하는 경우(예: 패키지 개명)에 한해 테스트의 `package`/`import`/식별자 참조만 갱신하며, 단정문은 건드리지 않습니다.

### 🔍 Tester 에이전트 ([`prompts/tester-agent.md`](../prompts/tester-agent.md))
- `DEV_DONE` → **`{target_project}/AI_README.md`가 선언한 위치와 하네스로** 테스트 작성. 디렉토리명을 가정하지 않으며, 대상 프로젝트의 테스트 언어·프레임워크를 그대로 사용함(다른 언어의 별도 검사 스크립트 금지).
- 테스트 명령도 `AI_README.md`에서 조회(없으면 명세의 Testing Instructions로 폴백).
- **실행하지 못한 판정 기준은 합격시키지 않음.** 명령을 완주하지 못하면 `BLOCKED`(검증 불가). 문서 리뷰 대체는 `AI_README.md`가 **자동 테스트 하네스 자체가 없다고 선언한** 서브프로젝트에만 허용됨.
- 통과 → `QA_PASSED`. 실패 → `retry_count` 증가 후 `QA_FAILED`(재시도 < 3) 또는 `BLOCKED`(≥ 3). 명세 결함·검증 불가는 `retry_count`를 올리지 않고 `BLOCKED`.
- QA 리포트는 **덮어쓰지 않고 실행 회차 섹션(`## Run N`)을 덧붙여** 이력을 보존하며, 각 판정 기준마다 **실행 증거(명령 출력)** 를 기록함.
- **테스트가 아닌 파일은 어떤 것도 수정하지 않음** — 프로덕션 소스·빌드 스크립트·리소스·`AI_README.md`·`docs/specs/**` 포함. 버그는 리포트로만 보고.

### 📋 쓰기 권한 매트릭스

각 에이전트가 **생성·수정할 수 있는 파일**의 전체 목록입니다. 표에 없는 경로는 해당 에이전트에게 금지됩니다.

| 경로 | PM | Developer | Tester |
|---|:--:|:--:|:--:|
| `docs/specs/{TASK-ID}-*.md` | ✅ | 🔒 스테이징만 | ❌ |
| `docs/qa/{TASK-ID}-report.md` | ❌ | 🔒 스테이징만 | ✅ |
| `docs/task-board.json` · `docs/turn.json` | ✅ | ✅ | ✅ |
| `{target_project}/` 프로덕션 소스·빌드 스크립트·리소스 | ❌ | ✅ | ❌ |
| `{target_project}/` 테스트 소스 | ❌ | 🔒 명세가 요구할 때 한정, 단정문 변경 불가 | ✅ |
| `{target_project}/AI_README.md` | ❌ | ✅ 명세가 요구할 때 | ❌ |
| `docs/` 기획·결정 문서 | ✅ 사용자 요청 시 | ❌ | ❌ |

> 금지는 **경로 기준**이며 언어 기준이 아닙니다. Kotlin·Gradle KTS·C++·TOML·XML·Markdown 모두 동일하게 적용됩니다. "빌드 설정일 뿐" 또는 "문서일 뿐"은 예외 사유가 되지 않습니다.

---

## 5. 명세 개정 프로토콜 (Spec Amendment)

### 5.1 왜 신규 task가 아니라 개정인가

`docs/task-board.json`은 SSOT이고, 각 spec은 **선행 코드 없이도 단독 구현 가능한 완결된 SRS**입니다(`prompts/pm-agent.md`의 Specification Standard). 따라서 **하나의 기능에 대해 유효한 명세는 항상 정확히 하나**여야 합니다.

기존 기능의 요구사항 결함을 신규 task로 처리하면 다음 문제가 발생합니다.

- 개정 내용을 "diff" 형태로 쓰면 → 선행 spec 없이는 구현 불가하므로 SRS 표준을 위반합니다.
- 기능 전체를 다시 쓰면 → 같은 모듈을 서로 다르게 규정하는 **두 개의 명세가 공존**합니다. 이 task 세트를 다른 프로젝트에 재생(replay)할 때 두 spec이 상충하며, 어느 쪽이 유효한지 판정할 근거가 보드에 없습니다.

### 5.2 신규 등록 vs 개정 판단 기준

| 조건 | 처리 |
|---|---|
| 새로운 독립 검증 단위(별도 모듈·별도 인터페이스 계약)가 추가됨 | **신규 task 등록** |
| 기존 기능의 요구사항이 부정확·불완전·모순이며, 산출물(소스·테스트 파일)이 기존 task와 동일함 | **기존 spec 개정** |

판단이 모호하면 **산출물 파일이 겹치는지**를 기준으로 삼습니다. 겹치면 개정입니다.

### 5.3 절차

1. **PM** — 기존 `docs/specs/{TASK-ID}-{slug}.md`를 **제자리 개정**합니다. 파일명과 `TASK-ID`는 바꾸지 않습니다. 개정 후에도 문서는 여전히 **단독 구현 가능한 완결 명세**여야 하며, 변경 이력 형태로 기술하지 않습니다.
2. **PM** — spec 최상단 `Revision History` 섹션에 회차·날짜·사유를 기록합니다. 제자리 개정의 유일한 단점인 변경 이력 소실을 이 섹션으로 상쇄합니다.
3. **PM** — `task-board.json`에서 해당 task의 `status`를 `DRAFT`로 되돌리고 `retry_count`를 `0`으로 리셋한 뒤, 개정 완료 시 `SPEC_READY`로 전이하고 `docs/turn.json`을 Developer로 핸드오프합니다.
4. **Developer / Tester** — 이후 사이클은 신규 task와 완전히 동일합니다. 단 Tester는 QA 리포트를 덮어쓰지 않고 `## Run N (spec vN)` 섹션을 덧붙입니다.
5. **Developer** — 개정 사이클의 커밋 메시지는 `fix: amend {TASK-ID} spec vN implementation` 형식을 사용해 최초 구현 커밋과 구분합니다.

### 5.4 SRS의 Revision History 형식

```markdown
## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-07-31 | PM | 최초 작성 |
| v2 | 2026-08-01 | PM | FR-8 툴팁 배치가 겹침 금지 속성을 규정하지 못해 재작성 |
```

---

## 6. 안전 규칙 (Invariants)

- `task-board.json`과 `turn.json`은 항상 **유효한 JSON**을 유지해야 합니다. 원시 텍스트로 덮어쓰지 말고 `jq` 또는 Python(`python -c "import json..."`)으로 파싱 후 갱신하세요.
- 모든 코드 변경은 task의 `target_project` 디렉토리 내부로 한정됩니다.
- 무한 루프 방지: QA 재시도는 최대 3회, 초과 시 `BLOCKED`로 종료됩니다.
- 하나의 기능에 대해 유효한 spec은 **정확히 하나**입니다. 같은 산출물을 규정하는 spec을 중복 생성하지 마세요(§5).
- `DONE`/`BLOCKED` 재개는 **PM만** 수행합니다.
- **각 에이전트는 §4 쓰기 권한 매트릭스에 명시된 경로만 수정합니다.** 금지는 경로 기준이며, 파일의 언어나 "코드가 아니라 설정/문서"라는 성격은 예외 사유가 아닙니다.
- **판정 권한은 Tester에게만 있습니다.** Developer는 빌드·테스트를 실행해도 되지만 `QA_PASSED`를 스스로 설정할 수 없고, PM은 어떤 QA 상태도 설정하지 않습니다.
- **실행되지 않은 검증은 검증이 아닙니다.** 테스트 명령을 완주하지 못한 채 소스를 읽고 합격시키는 것을 금지합니다.
- **명세를 구현에 맞추어 고치지 않습니다.** spec은 PM이 사용자 지시에 따라서만 개정합니다.
- **커밋 스테이징은 `git add -A`/`git add .`을 금지하고, 해당 task의 산출물 경로만 명시적으로 지정합니다.** 본 저장소는 PM·사용자가 문서를 동시에 편집할 수 있는 모노레포이므로, 일괄 스테이징은 무관한 작업을 잘못된 task 커밋 메시지 아래 묻어버립니다. 커밋 전 `git diff --cached --name-only`로 스테이징 목록을 검증하고, 무관한 변경은 **워킹 트리에 그대로 남겨 둡니다**.
- 상태 파일(`task-board.json`, `turn.json`) 갱신은 **커밋보다 먼저** 수행합니다. 커밋 후에 갱신하면 그 변경이 워킹 트리에 남아 다음 task의 커밋에 섞여 들어갑니다.

---

## 7. 서브프로젝트별 테스트 명령 요약

| target_project | 테스트 명령 |
|---|---|
| `TennisDocAI` | `./gradlew test` |
| `tennis-swing-analyzer` | 컴포넌트별 `pio test`(펌웨어) / `pytest`(대시보드) |
| `tennis-vision-analyzer` | `python -m unittest discover tests/` |
| `tennis-sensor-case` | `python3 verify_geometry.py` (exit 0/1) |
| `tennis-sensor-hardware` | 자동 테스트 없음 → Acceptance Criteria 문서 리뷰 |

각 명령의 상세 환경은 해당 서브프로젝트의 `AI_README.md`를 참조하세요.
