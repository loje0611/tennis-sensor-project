# TASK-042 — Lab 화면 'Clean Sunlit Court' 밝고 세련된 프리미엄 UI/UX 개편

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-15 | PM | 최초 작성 (야외 코트 및 밝은 실내 조명 환경에 최적화된 Clean Sunlit Court 화이트 크리스탈 글래스, 비비드 테니스 라임/코트블루 액센트, 듀얼 스트로크 고대비 스켈레톤, 프리미엄 스포츠 HUD 리디자인 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 야외 테니스 코트 및 밝은 실내 조명 환경(직사광선, 화면 난반사)에서의 시인성을 극대화하고, 나이키/애플 피트니스 수준의 세련되고 역동적인 스포츠 비전 AI 룩앤필을 제공하기 위해 `:feature:lab` 모듈의 전체 UI/UX를 **"Clean Sunlit Court (서니 코트 화이트 & 비비드 라임)"** 디자인 시스템으로 전면 개편하는 작업을 규정합니다.

투박한 솔리드 블랙 박스를 걷어내고, **크리스탈 화이트 프로스트 글래스모피즘**, **스노우 화이트 캡슐형 드릴 셀렉터**, 밝은 코트/흰색 옷에서도 또렷한 **듀얼 스트로크 고대비 스켈레톤**, 그리고 **선명한 비비드 컬러의 프로 스포츠 실시간 HUD**를 구축합니다.

### 1.2 범위
- `:feature:lab` 모듈 UI 컴포넌트 리디자인 (`io.github.loje0611.tennisdoc.feature.lab.ui`):
  - `LabSessionControlHeader`:
    - 배경: 퓨어 화이트 프로스트 글래스 (`Color(0xE6FFFFFF)`) + 미세한 라이트 테두리 + 부드러운 화이트/블루 앰비언트 섀도우.
    - 타이포그래피: 고대비 딥 챠콜 (`#1A1A1E`) + **`MichromaFont`** (`02:15 | 12 SWINGS`).
    - 버튼: 청량한 로얄 코트 블루 그라디언트 [측정 시작] (`#0066FF` ➔ `#00AAFF`) 및 비비드 코랄 레드 [측정 종료] (`#FF3B30` ➔ `#FF6B6B`).
    - 센서 인디케이터: 부드러운 브리딩 펄스(Breathing Pulse) 애니메이션.
  - `DrillSelectorBar`:
    - 스노우 화이트 캡슐 세그먼트 칩.
    - 선택된 칩: 퓨어 화이트 배경 + 로얄 블루 테두리 (`1.5dp`) + 볼드 텍스트 + 테니스공 라임 악센트 도트.
    - 미선택 칩: 반투명 소프트 화이트 (`Color(0xAAFFFFFF)`) + 옅은 그레이 테두리.
  - `PoseOverlayCanvas`:
    - 햇빛 및 밝은 코트/흰색 옷 대응 **듀얼 스트로크 고대비 스켈레톤 (Dual-Stroke High Contrast)**:
      - 상체(팔/손목): 딥 네이비 외곽선 + 일렉트릭 스카이블루 코어
      - 하체(골반/다리): 딥 포레스트 외곽선 + 테니스공 비비드 라임 코어
      - 주요 관절(손목, 어깨, 골반) 2중 링 강조.
  - `LabRealtimeFeedbackCard`:
    - 퓨어 화이트 스포츠 HUD 카드 (`Color(0xF5FFFFFF)`) + 20dp 라운딩 + 소프트 섀도우.
    - 라켓 페이스 뱃지: `SQUARE 0°` (비비드 에메랄드 `#10B981`), `OPEN +12°` (비비드 앰버 `#F59E0B`), `CLOSED -8°` (로얄 코트 블루 `#3B82F6`).
    - 5단계 운동 체인: 밝고 선명한 에메랄드 그린 노드-링크 파이프라인.
    - 인과 코칭 팁 박스: 소프트 옐로우 틴트 컨테이너 (`Color(0xFFFFFBEB)`) + 골드 악센트 보더.
  - `FarFieldFeedbackOverlay` & `SessionCompletionDialog`:
    - 밝은 라이트 테마에 맞춘 고대비 대형 HUD 및 완성도 높은 세션 요약 모달 다이얼로그.
- 단위 테스트 및 Compose UI 렌더링 검증.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **Clean Sunlit Court 디자인 시스템**: 밝은 야외 환경에서의 난반사를 억제하고 높은 디스플레이 휘도를 활용하는 화이트 글래스모피즘 및 비비드 스포츠 컬러 기반 디자인.
- **듀얼 스트로크 스켈레톤 (Dual-Stroke Skeleton)**: 어두운 외곽선과 밝은 코어를 중첩 렌더링하여 배경 색상(흰색 옷, 밝은 클레이/하드코트)에 관계없이 100% 식별력을 보장하는 기법.

### 2.2 참고 문서
- 디자인 테마 토큰: [`TennisDocColorScheme.kt`](file:///home/keunu/personal-project/tennis-sensor-project/TennisDocAI/core/ui/src/main/java/io/github/loje0611/tennisdoc/core/ui/theme/TennisDocColorScheme.kt)
- Lab 카메라 UX 명세: [`docs/specs/TASK-041-lab-camera-mode-ux-self-training-coaching.md`](TASK-041-lab-camera-mode-ux-self-training-coaching.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 크리스탈 화이트 글래스모피즘 세션 컨트롤 헤더 (`LabSessionControlHeader`)
- 배경을 `Color(0xE6FFFFFF)` (또는 `Color(0xF2FFFFFF)`)의 프로스트 화이트 글래스로 구성하고, 모서리를 16dp 라운딩 처리한다.
- 텍스트 타이포그래피에 딥 챠콜(`Color(0xFF1A1A1E)`)과 `:core:ui`의 `MichromaFont`를 적용한다.
- [측정 시작] 버튼에 로얄 코트 블루 그라디언트(`Brush.horizontalGradient(listOf(Color(0xFF0066FF), Color(0xFF00AAFF)))`)를 적용한다.
- [측정 종료] 버튼에 비비드 코랄 레드 그라디언트(`Brush.horizontalGradient(listOf(Color(0xFFFF3B30), Color(0xFFFF6B6B)))`)를 적용한다.
- 센서 연결 상태 점(Dot)에 테니스 라임(`Color(0xFF10B981)`)의 부드러운 숨쉬기(Breathing) 애니메이션을 적용한다.

### FR-2: 스노우 화이트 캡슐 드릴 선택 바 (`DrillSelectorBar`)
- 개별 드릴 아이템을 둥근 캡슐 형태(CircleShape / RoundedCornerShape(20.dp))로 렌더링한다.
- 선택된 드릴:
  - 배경: 퓨어 화이트 (`Color.White`)
  - 테두리: `1.5dp` 로얄 블루 (`Color(0xFF0066FF)`)
  - 텍스트: 볼드 딥 챠콜 + 테니스공 비비드 라임 악센트 도트(또는 라켓/서브 아이콘)
- 미선택 드릴:
  - 배경: 반투명 소프트 화이트 (`Color(0xAAFFFFFF)`)
  - 텍스트: 서브 그레이 (`Color(0xFF555560)`)

### FR-3: 듀얼 스트로크 고대비 스켈레톤 비전 캔버스 (`PoseOverlayCanvas`)
- 밝은 야외 및 흰색 옷 착용 시에도 관절선이 또렷하게 보이도록 모든 관절 선과 포인트에 2중 스트로크를 적용한다:
  - **상체 (어깨, 팔, 손목)**:
    - 외곽선(Outer Stroke): `Color(0xFF0B192C)` (딥 네이비, 4.5dp)
    - 코어(Inner Core): `Color(0xFF00D2FF)` (일렉트릭 스카이블루, 2.5dp)
  - **하체 (골반, 무릎, 발목)**:
    - 외곽선(Outer Stroke): `Color(0xFF0A2E12)` (딥 포레스트, 4.5dp)
    - 코어(Inner Core): `Color(0xFF10B981)` 또는 `Color(0xFF39FF14)` (비비드 테니스 라임, 2.5dp)
  - 주요 관절 포인트(어깨, 손목, 골반): 딥 아웃라인 링 + 화이트/네온 코어 원.

### FR-4: 퓨어 화이트 프로 스포츠 실시간 피드백 HUD 카드 (`LabRealtimeFeedbackCard`)
- 카드를 퓨어 화이트 (`Color(0xF8FFFFFF)`) 컨테이너 + 20dp 라운딩 + 은은한 그림자로 구성한다.
- **라켓 페이스 뱃지**:
  - `SQUARE`: 비비드 에메랄드 라임 (`#10B981`) 뱃지 + 화이트 텍스트
  - `OPEN`: 비비드 앰버 오렌지 (`#F59E0B`) 뱃지 + 화이트 텍스트
  - `CLOSED`: 로얄 코트 블루 (`#3B82F6`) 뱃지 + 화이트 텍스트
- **5단계 운동 체인 그래프**:
  - 5개 라운드 노드와 연결 화살표를 선명한 에메랄드 그린(`Color(0xFF059669)`)으로 시각화.
- **인과 코칭 팁 박스**:
  - 소프트 옐로우 틴트 (`Color(0xFFFFFBEB)`) 배경 + 앰버 골드 (`Color(0xFFF59E0B)`) 테두리 및 전구 💡 아이콘.

### FR-5: 대형 원거리 HUD 및 세션 완료 다이얼로그 라이트 스타일링
- `FarFieldFeedbackOverlay`: 전면 카메라 셀프 모드 시 스윙 직후 3초 대형 팝업에 고대비 퓨어 화이트 글래스 + 비비드 페이스 뱃지 적용.
- `SessionCompletionDialog`: 라이트 테마에 맞춘 선명한 훈련 리포트 카드 및 로얄 블루 [리플레이 보기] 버튼 적용.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

- 기존 `LabUiState`, `CameraFacingMode`, `FarFieldHudState` 데이터 구조 그대로 유지 (UI 스타일링 및 렌더링 계층 개편).

---

## 5. UI/UX 요구사항
- **햇빛 직사광선 시인성**: 야외 100,000 Lux 조도 환경에서도 글씨와 관절 스켈레톤이 또렷하게 식별되어야 함.
- **부드러운 인터랙션**: 칩 선택 시 200ms 애니메이션 트랜지션 및 햅틱 피드백.
- **일관된 디자인 언어**: `:core:ui`의 `MichromaFont` 및 스포츠 컬러 시스템과 100% 정합.

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 성능 최적화
- 듀얼 스트로크 캔버스 렌더링 시 드로우콜 최적화(배치 그리기)로 30fps 카메라 오버레이 렌더링 프레임 드랍 0 유지.

---

## 7. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `LabSessionControlHeader`가 프로스트 화이트 글래스 배경, `MichromaFont` 타이포그래피, 로얄 코트 블루 [측정 시작] 버튼으로 렌더링된다.
- [ ] **AC-2**: `DrillSelectorBar`가 스노우 화이트 캡슐 세그먼트 칩 및 로얄 블루 테두리로 렌더링된다.
- [ ] **AC-3**: `PoseOverlayCanvas`가 딥 아웃라인 + 비비드 코어의 듀얼 스트로크 고대비 스켈레톤으로 렌더링되어 밝은 배경에서도 선명하게 표시된다.
- [ ] **AC-4**: `LabRealtimeFeedbackCard`가 퓨어 화이트 스포츠 HUD 카드 및 고대비 페이스 뱃지/5단계 체인 노드로 렌더링된다.
- [ ] **AC-5**: `LabUiTest` 및 `PoseOverlayCanvasTest` 단위/UI 테스트가 100% 통과한다.
- [ ] **AC-6**: `./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug` 명령이 0 Failures로 통과한다.

---

## 8. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :feature:lab:test :app:testDebugUnitTest verifyModuleDependencies :app:assembleDebug
```
