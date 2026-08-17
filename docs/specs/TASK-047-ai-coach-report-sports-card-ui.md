# TASK-047 명세서: AI 코치 리포트 스포츠 카드 UI 컴포넌트 구현

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-17 | PM | 최초 작성 (Phase 4 C그룹: Clean Sunlit Court 기반 AI 코치 리포트 스포츠 카드, 인과 진단 섹션, 드릴 추천 카드 및 스켈레톤 로딩 UI 구현) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
`TASK-043~046`을 통해 AI 코치 도메인 모델, 융합 지표 컨텍스트 빌더, Gemini LLM 클라이언트 및 무중단 로컬 Fallback 엔진이 완성되었습니다.
본 태스크(`TASK-047`)는 생성된 `AiCoachReport`를 사용자에게 직관적이고 세련되게 전달하기 위해, **Clean Sunlit Court 라이트 테마에 맞춘 재사용 가능한 스포츠 리포트 UI 컴포넌트 세트(`:core:ui`)** 를 구현합니다.

### 1.2 범위
- `:core:ui`에 `implementation(project(":core:model"))` 의존성 추가.
- `AiCoachReportCard`: 리포트 종합 컨테이너 (헤더, 출처 배지, 총평 Callout, 강점 칩, 인과 결함 카드, 액션 아이템, 추천 드릴 목록).
- `CausalDiagnosisCard`: 센서-비전 융합 기반 인과 결함(관측 현상 ➔ 근본 원인 ➔ 교정 큐) 시각화 카드.
- `DrillRecommendationCard`: 추천 드릴(종류, 명칭, 집중 포인트, 권장 횟수) 카드.
- `CoachToneSelector`: 코칭 스타일/톤(격려/분석/엄격) 캡슐 선택 컴포넌트.
- `AiCoachLoadingSkeleton`: AI 분석 중 펄스 애니메이션이 적용된 스켈레톤 로딩 뷰.
- Compose UI 단위/렌더링 테스트 구현.

---

## 2. Definitions & References (정의 및 참조)

- **Clean Sunlit Court 테마**: 코트의 밝은 햇살을 연상시키는 화이트/프로스트 글래스 배경, 로열 블루(`#2563EB`) 및 윔블던 그린(`#16A34A`), 앰버 경고(`#D97706`) 액센트를 적용한 프리미엄 라이트 스포츠 UI.
- **분석 출처 배지**:
  - `✨ Gemini AI 분석`: LLM 원격 API 정상 생성 (`isFallbackReport == false`).
  - `⚡ 로컬 룰 엔진 분석`: 오프라인/Fallback 로컬 엔진 생성 (`isFallbackReport == true`).

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: `AiCoachReportCard` 컨테이너 컴포넌트 구현
- `AiCoachReportCard(report: AiCoachReport, modifier: Modifier = Modifier, onDrillClick: ((DrillRecommendation) -> Unit)? = null)`
- **헤더 영역**:
  - 타이틀: "🤖 AI 코치 처방 리포트" (볼드 타이포그래피)
  - 출처 배지: `report.isFallbackReport` 여부에 따라 `⚡ 로컬 룰 엔진 분석` (Slate 회색조) 또는 `✨ Gemini AI 분석` (Royal Blue 테두리/캡슐) 표시.
  - 생성 일시: `yyyy.MM.dd HH:mm` 형식 텍스트.
- **총평 (Overall Summary)**:
  - 연한 블루/그레이 배경의 라운드 Callout 박스에 `report.overallSummary` 렌더링.
- **강점 칩 (Key Strengths)**:
  - `report.keyStrengths`가 비어있지 않은 경우 그린 체크마크(`✓`) 아이콘과 함께 가로 스크롤 또는 FlowRow 형태의 소프트 그린 캡슐 칩으로 표시.
- **인과 진단 (Causal Diagnosis)**:
  - `report.primaryFlawDiagnosis != null`인 경우 `CausalDiagnosisCard` 임베드.
- **집중 과제 (Action Items)**:
  - `report.actionItems`가 있는 경우 번호 매겨진 체크리스트 스타일로 렌더링.
- **추천 드릴 (Recommended Drills)**:
  - `report.recommendedDrills` 목록을 `DrillRecommendationCard`로 렌더링.

### FR-2: `CausalDiagnosisCard` 인과 결함 시각화 컴포넌트 구현
- `CausalDiagnosisCard(diagnosis: CausalFlawDiagnosis, modifier: Modifier = Modifier)`
- 앰버/오렌지 소프트 서피스 배경 (`#FFFBEB` / 테두리 `#FDE68A`).
- 3단계 인과 구조 표현:
  1. **🚨 관측된 현상**: `diagnosis.observedEffect` (예: "임팩트 시 라켓 페이스 +14° 열림")
  2. **🔍 근본 원인**: `diagnosis.rootCause` (예: "골반 회전 대비 어깨 조기 회전으로 체인 가속 전달 누수")
  3. **💡 코칭 큐**: `diagnosis.coachingCue` (예: "포워드 스윙 시 라켓 헤드를 뒤에 두고 하체 리드로 회전하세요.")

### FR-3: `DrillRecommendationCard` 드릴 추천 컴포넌트 구현
- `DrillRecommendationCard(drill: DrillRecommendation, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null)`
- 드릴 종류 뱃지 (`drill.drillType.toDisplayName()`), 권장 횟수 (`drill.targetRepetitions`회), 타이틀 (`drill.title`), 포커스 포인트 (`drill.focusPoint`).
- 클릭 가능한 카드 인터랙션 지원.

### FR-4: `CoachToneSelector` 코칭 톤 선택 컴포넌트 구현
- `CoachToneSelector(selectedTone: CoachTone, onToneSelected: (CoachTone) -> Unit, modifier: Modifier = Modifier)`
- 3가지 옵션 캡슐 칩:
  - `ENCOURAGING` ➔ "🌱 격려형"
  - `ANALYTICAL` ➔ "📊 분석형"
  - `STRICT` ➔ "🎯 엄격형"
- 선택된 칩은 Royal Blue 채움 배경 + 화이트 텍스트, 비선택 칩은 아웃라인 스타일.

### FR-5: `AiCoachLoadingSkeleton` 스켈레톤 로딩 컴포넌트 구현
- `AiCoachLoadingSkeleton(modifier: Modifier = Modifier)`
- AI 분석 중임을 알리는 스피너 또는 부드러운 펄스 애니메이션이 적용된 카드 스켈레톤.
- 안내 문구: "🤖 AI 코치가 5단계 운동 체인과 스윙 역학을 분석하고 있습니다..."

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

```kotlin
package io.github.loje0611.tennisdoc.core.ui.coach

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.CausalFlawDiagnosis
import io.github.loje0611.tennisdoc.core.model.CoachTone
import io.github.loje0611.tennisdoc.core.model.DrillRecommendation

@Composable
fun AiCoachReportCard(
    report: AiCoachReport,
    modifier: Modifier = Modifier,
    onDrillClick: ((DrillRecommendation) -> Unit)? = null
)

@Composable
fun CausalDiagnosisCard(
    diagnosis: CausalFlawDiagnosis,
    modifier: Modifier = Modifier
)

@Composable
fun DrillRecommendationCard(
    drill: DrillRecommendation,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
)

@Composable
fun CoachToneSelector(
    selectedTone: CoachTone,
    onToneSelected: (CoachTone) -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun AiCoachLoadingSkeleton(
    modifier: Modifier = Modifier
)
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- **색상 팔레트 (Clean Sunlit Court)**:
  - 카드 배경: `#FFFFFF` (Surface), 테두리 `#E2E8F0` (1dp).
  - 총평 Callout: 배경 `#F8FAFC`, 테두리 `#CBD5E1`.
  - 강점 칩: 배경 `#F0FDF4`, 텍스트/테두리 `#16A34A`.
  - 결함 인과 카드: 배경 `#FFFBEB`, 테두리 `#FCD34D`, 텍스트 `#92400E`.
  - 코칭 큐 박스: 배경 `#EFF6FF`, 텍스트 `#1E40AF`.
  - 활성 버튼/뱃지: Royal Blue `#2563EB`.
- **타이포그래피**:
  - 제목: 볼드체 (16~18sp).
  - 본문: Regular/Medium (13~14sp), 가독성 높은 줄간격 (line-height 20sp).
- **인터랙션**:
  - 톤 선택 칩 터치 시 즉각적인 햅틱/상태 전환 피드백.
  - 드릴 카드 클릭 시 터치 리플 효과.

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **Compose 프리뷰 및 테스트 친화성**: 모든 컴포넌트는 상태 호이스팅(State Hoisting)이 적용되어 독립적인 `@Preview` 및 Compose UI 테스트가 가능해야 한다.
- **모듈 규칙 준수**: `:core:ui`는 `:core:model`만을 참조하며, 비즈니스 로직 모듈(`:core:coach`, `:core:fusion`)에 대한 역참조가 없어야 한다 (`verifyModuleDependencies` 통과).

---

## 7. Error Handling & Edge Cases (오류 처리 및 예외 상황)

- **인과 결함 진단이 없는 경우 (`primaryFlawDiagnosis == null`)**: `CausalDiagnosisCard` 영역을 건너뛰고 깔끔하게 강점과 추천 드릴만 렌더링.
- **빈 강점/액션 아이템/추천 드릴 목록**: 빈 영역 없이 컴팩트하게 카드 높이가 자동 조절됨.
- **긴 텍스트 줄바꿈**: 총평 또는 코칭 큐 문장이 길어져도 텍스트가 잘리지 않고 자연스럽게 멀티라인 래핑.

---

## 8. Acceptance Criteria (수용 기준)

- **AC-1 (리포트 카드 정상 렌더링)**: `AiCoachReportCard`가 주어진 `AiCoachReport`의 총평, 강점, 액션 아이템, 추천 드릴을 모두 화면에 표시해야 한다.
- **AC-2 (인과 진단 카드 렌더링)**: `CausalDiagnosisCard`가 관측 현상, 근본 원인, 코칭 큐 3단계를 시각적으로 명확히 구분하여 표시해야 한다.
- **AC-3 (출처 배지 분기)**: `isFallbackReport == true`일 때 `⚡ 로컬 룰 엔진 분석`, `false`일 때 `✨ Gemini AI 분석` 배지가 표시되어야 한다.
- **AC-4 (톤 셀렉터 동작)**: `CoachToneSelector`에서 다른 톤 클릭 시 `onToneSelected` 콜백이 정확한 `CoachTone` 값을 전달해야 한다.
- **AC-5 (스켈레톤 로딩 렌더링)**: `AiCoachLoadingSkeleton`이 크래시 없이 로딩 안내 문구와 함께 정상 렌더링되어야 한다.
- **AC-6 (빌드 및 테스트 통과)**: 선언된 테스트 명령이 0 failure로 통과해야 한다.

---

## 9. Testing Instructions (테스트 명령)

```bash
cd TennisDocAI
export JAVA_HOME=/home/keunu/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
export ANDROID_HOME=/home/keunu/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

# 모듈 의존성 및 UI 단위 테스트
./gradlew :core:ui:test verifyModuleDependencies --rerun-tasks
```
