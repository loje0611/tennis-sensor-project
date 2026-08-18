# TASK-053 명세서: 비디오 + 스윙 궤적(Arc) 리플레이 UI 개편 및 History 연동

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-18 | PM | 최초 작성 (Phase 5 리플레이 UX 전면 개편: IMU 파형 차트 제거, Media3 ExoPlayer 기반 실제 비디오 재생, 스윙 궤적 아크 오버레이, History 비디오 유무 기반 조건부 리플레이 진입, 자동 저장 OFF 시 리플레이 메뉴 숨김 처리) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
[`TASK-051`](TASK-051-video-storage-and-preferences-infrastructure.md)에서 비디오 스토리지 인프라를, [`TASK-052`](TASK-052-camerax-swing-clip-video-recording.md)에서 CameraX 2초 스윙 클립 녹화 파이프라인을 구축하여 실제 스윙 MP4 클립이 `LabRawRecordEntity.videoPath`에 저장되고 있습니다.

본 태스크(`TASK-053`)는 **기존 리플레이 화면을 사용자 친화적으로 전면 개편**합니다.

현재 리플레이 화면은 검은 배경에 33개 관절 스켈레톤 뼈대 선과 IMU 센서 파형 그래프를 표시하고 있으나, 일반 사용자(테니스 동호인)에게는 이 정보들이 해석하기 어렵고 실질적인 도움이 되지 않습니다.

개편의 핵심 원칙:
1. **실제 스윙 영상을 배경으로 재생**하고, 그 위에 **손목/라켓 스윙 궤적 아크(Motion Trail)**만 직관적으로 오버레이한다.
2. 공학적 IMU 파형 차트와 복잡한 33개 관절 스켈레톤은 **완전히 제거**한다.
3. 비디오 리플레이 아래에 **직관적인 스윙 분석 카드**(궤적 유형, 라켓 면 상태, AI 한 줄 코칭)를 표시한다.
4. **비디오 영상이 없는 스윙은 리플레이 진입 버튼을 숨김 처리**한다 (자동 저장 OFF 시 또는 보관 한도 초과로 삭제된 경우).
5. History 세션 상세 화면에서도 비디오가 저장된 스윙에 한해 리플레이 진입이 가능하다.

### 1.2 범위
- `gradle/libs.versions.toml` 및 `:feature:lab/build.gradle.kts`:
  - `androidx.media3:media3-exoplayer` 및 `androidx.media3:media3-ui` 의존성 추가.
- `:feature:lab` 모듈 리플레이 UI 전면 개편 (`io.github.loje0611.tennisdoc.feature.lab.replay`):
  - `LabReplayScreen` 재구성: 비디오 재생 영역 + 스윙 궤적 오버레이 + 분석 카드.
  - `SwingVideoPlayer` 신규 컴포저블: Media3 ExoPlayer 기반 MP4 비디오 재생.
  - `SwingTrailOverlay` 신규 컴포저블: 손목 궤적 아크(Motion Trail) 렌더링.
  - `SwingAnalysisSummaryCard` 신규 컴포저블: 궤적 유형, 라켓 면 상태, AI 코칭 요약.
  - `ImuWaveformChart` 사용 제거 (컴포저블 호출만 제거, 소스 파일 자체는 유지).
  - `PoseReplayCanvas` 사용 제거 (컴포저블 호출만 제거, 소스 파일 자체는 유지).
  - `LabReplayViewModel` 확장: `videoPath` 로딩 및 비디오 유무 상태 관리, 스윙 궤적 데이터 생성.
  - `LabReplayUiState` 확장: `videoPath: String?`, `hasVideo: Boolean`, `swingTrailPoints: List<TrailPoint>` 필드 추가.
- `:feature:history` 모듈:
  - `SessionDetailScreen`의 `LabSwingSummaryCard`에서 비디오 유무에 따른 리플레이 버튼 조건부 표시.
  - `LabSwingSummaryItem`에 `hasVideo: Boolean` 필드 추가.
  - `SessionDetailViewModel`에서 `LabRawRecordEntity.videoPath` 유무 반영.
- `:feature:lab` `SessionCompletionDialog`:
  - 비디오가 저장된 스윙이 없을 경우 "리플레이 보기" 버튼 숨김.
- 단위 및 UI 테스트:
  - `SwingTrailOverlayTest`: 궤적 데이터로부터 정상 렌더링 검증.
  - `LabReplayVideoIntegrationTest`: 비디오 유무에 따른 UI 분기 검증.
  - `HistoryReplayVisibilityTest`: History 스윙 목록에서 비디오 유무별 리플레이 버튼 표시/숨김 검증.

---

## 2. Definitions & References (정의 및 참조)

### 2.1 주요 정의
- **스윙 궤적 아크 (Motion Trail)**: 스윙 과정에서 손목(wrist, 랜드마크 인덱스 15 또는 16) 관절이 이동한 경로를 부드러운 곡선으로 시각화한 그래픽 요소. 프레임별 2D 정규화 좌표(0.0~1.0)를 비디오 캔버스 크기에 맞추어 렌더링하며, 임팩트 시점에 가까울수록 밝은 네온 색상 및 두꺼운 선으로 그라데이션 처리.
- **`TrailPoint`**: 궤적 상 단일 포인트를 나타내는 데이터 클래스 (`x: Float`, `y: Float`, `progress: Float`). `progress`는 0.0(가장 오래된 포인트, 흐릿함)에서 1.0(임팩트/최신 포인트, 선명함)까지의 정규화 값.
- **Media3 ExoPlayer**: Android Jetpack 미디어 라이브러리로서 로컬 MP4 파일을 재생하는 비디오 플레이어.

### 2.2 참고 문서
- 비디오 스토리지 인프라: [`docs/specs/TASK-051-video-storage-and-preferences-infrastructure.md`](TASK-051-video-storage-and-preferences-infrastructure.md)
- 비디오 녹화 파이프라인: [`docs/specs/TASK-052-camerax-swing-clip-video-recording.md`](TASK-052-camerax-swing-clip-video-recording.md)
- 기존 리플레이 명세(참조만): [`docs/specs/TASK-039-synchronized-replay-diagnostic-viewer.md`](TASK-039-synchronized-replay-diagnostic-viewer.md)
- 스윙 궤적 분류: [`SwingPathClassifier.kt`](../../TennisDocAI/core/vision/src/main/java/io/github/loje0611/tennisdoc/core/vision/analyzer/SwingPathClassifier.kt) (`getWristTrajectory3d`)
- History 리플레이 네비게이션: [`docs/specs/TASK-040-history-lab-session-detail-replay-navigation.md`](TASK-040-history-lab-session-detail-replay-navigation.md)

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: Media3 ExoPlayer 의존성 추가
- `gradle/libs.versions.toml`에 Media3 버전 및 라이브러리 선언:
  - `media3 = "1.5.1"` (또는 최신 안정 버전)
  - `androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }`
  - `androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }`
- `:feature:lab/build.gradle.kts`에 `implementation(libs.androidx.media3.exoplayer)`, `implementation(libs.androidx.media3.ui)` 추가.

### FR-2: `LabReplayUiState` 확장 및 `LabReplayViewModel` 비디오/궤적 데이터 관리
- `ReplayModels.kt`에 추가:
  ```kotlin
  data class TrailPoint(
      val x: Float,  // 0.0~1.0 정규화 X
      val y: Float,  // 0.0~1.0 정규화 Y
      val progress: Float  // 0.0(과거/흐릿) ~ 1.0(임팩트/선명)
  )
  ```
- `LabReplayUiState`에 다음 필드 추가:
  - `val videoPath: String? = null`
  - `val hasVideo: Boolean = false`
  - `val swingTrailPoints: List<TrailPoint> = emptyList()`
  - `val swingPathType: String = ""`  (예: "Topspin", "Flat", "Slice")
  - `val faceStateLabel: String = ""`  (예: "🟢 정타 (스퀘어)")
  - `val coachingOneLiner: String = ""` (AI 한 줄 피드백 또는 `FusionDiagnosis.coachingFeedback`)
- `LabReplayViewModel`:
  - `loadRecord(recordId)` 시 `LabRawRecordEntity.videoPath`를 읽어 `hasVideo = videoPath != null && File(videoPath).exists()` 판단.
  - 포즈 프레임에서 `SwingPathClassifier.getWristTrajectory3d(poseFrames, isRightHand)` 호출하여 전체 스윙 궤적 좌표 추출.
  - 추출된 좌표를 `List<TrailPoint>`로 변환: `progress = index.toFloat() / (totalPoints - 1)`.
  - `SwingPathClassifier.classifySwingPath()`로 궤적 유형 판별.
  - `FusedSwing.racketImpact.faceState`로 라켓 면 상태를 사용자 친화 레이블로 매핑:
    - `SQUARE` → "🟢 정타 (스퀘어)"
    - `OPEN` → "🟠 페이스 열림 (공이 뜨는 원인)"
    - `CLOSED` → "🔵 페이스 닫힘 (네트에 걸리는 원인)"
  - `FusedSwing.diagnosis.coachingFeedback`을 `coachingOneLiner`에 매핑.

### FR-3: `SwingVideoPlayer` 비디오 재생 컴포저블 구현 (`:feature:lab`)
- `io.github.loje0611.tennisdoc.feature.lab.replay.SwingVideoPlayer`:
  - `AndroidView`로 Media3 `PlayerView`를 렌더링.
  - 로컬 파일 경로(`videoPath`)에서 `MediaItem.fromUri(Uri.fromFile(File(videoPath)))`로 미디어 소스 설정.
  - ExoPlayer 인스턴스는 `remember` + `DisposableEffect`로 Lifecycle 관리 (onDispose에서 `player.release()`).
  - 반복 재생 모드(`Player.REPEAT_MODE_ALL`) 기본 설정.
  - `PlayerView`의 기본 컨트롤러 UI 비활성화 (`useController = false`): 자체 `SynchronizedTimelineController`를 사용.
  - ExoPlayer의 재생 상태/포지션을 `LabReplayViewModel`의 타임라인 제어와 동기화:
    - `seekTo(timestampMs)` 호출 시 `player.seekTo(timestampMs)` 실행.
    - `togglePlay()` 호출 시 `player.playWhenReady` 토글.
    - `setPlaybackSpeed(speed)` 호출 시 `player.setPlaybackSpeed(speed)` 적용.

### FR-4: `SwingTrailOverlay` 스윙 궤적 아크 오버레이 구현 (`:feature:lab`)
- `io.github.loje0611.tennisdoc.feature.lab.replay.SwingTrailOverlay`:
  - `Composable` Canvas로 비디오 위에 투명 레이어로 겹쳐 렌더링.
  - 입력: `swingTrailPoints: List<TrailPoint>`, `isImpact: Boolean`, `canvasSize: Size`.
  - 렌더링 규칙:
    - 각 `TrailPoint`의 `(x, y)`를 캔버스 크기에 맞추어 스케일.
    - 인접한 포인트들을 `Path`로 연결하여 부드러운 곡선(Bezier 또는 직선 보간) 렌더링.
    - **그라데이션 효과**: 궤적의 `progress` 값에 따라 선 굵기(2dp~6dp) 및 불투명도(alpha 0.2~1.0)를 점진적으로 변화. 오래된 구간은 흐릿하고 얇게, 임팩트 구간은 밝고 굵게.
    - **색상**: 네온 그린 계열 (`Color(0xFF00E676)`) 사용. 임팩트 순간 주변(±5 포인트)에 글로우 효과를 위해 `BlendMode.Screen` 및 반경 확대.
  - 임팩트 시점(`isImpact == true`)일 때: 임팩트 포인트에 💥 또는 `"IMPACT!"` 텍스트 뱃지 표시.

### FR-5: `SwingAnalysisSummaryCard` 직관적 분석 카드 구현 (`:feature:lab`)
- `io.github.loje0611.tennisdoc.feature.lab.replay.SwingAnalysisSummaryCard`:
  - Clean Sunlit Court 스타일 카드 (둥근 모서리 `16.dp`, `SwingTheme.colors.cardSurface`).
  - 카드 내 정보 표시:
    - **🎾 궤적 유형**: `swingPathType` (예: "📈 탑스핀 (상향 스윙 궤적)")
    - **🎯 임팩트 면**: `faceStateLabel` (예: "🟢 정타 (스퀘어)")
    - **💡 원포인트 코칭**: `coachingOneLiner` (예: "골반 → 어깨 → 손목 순서대로 힘이 잘 전달되고 있습니다.")

### FR-6: `LabReplayScreen` 전면 재구성 (`:feature:lab`)
- 개편된 화면 구성 (위에서 아래 순서):
  1. **`SwingVideoPlayer` + `SwingTrailOverlay`**: 비디오 위에 궤적 아크 오버레이를 `Box`로 겹침. 높이는 화면 폭의 4:3 비율(또는 `aspectRatio(3f / 4f)`) 적용.
  2. **`SynchronizedTimelineController`**: 기존 컴포넌트 재사용 (시크바, 재생/일시정지, 슬로우, 프레임 스텝, 임팩트 점프).
  3. **`SwingAnalysisSummaryCard`**: 궤적 유형, 라켓 면 상태, AI 코칭 한 줄 요약.
- **제거**: `PoseReplayCanvas`, `ImuWaveformChart`, `KineticChainSummaryCard` 호출을 `LabReplayScreen`에서 삭제.
- **비디오가 없는 경우 (`hasVideo == false`)**: "리플레이 데이터가 없습니다" 안내 메시지 표시 (기존 동작 유지).

### FR-7: History 세션 상세 화면 비디오 유무 기반 리플레이 조건부 표시 (`:feature:history`)
- `LabSwingSummaryItem`에 `val hasVideo: Boolean = false` 필드 추가.
- `SessionDetailViewModel`에서 `LabRawRecordEntity.videoPath`가 `null`이 아니고 물리 파일이 존재하는지 검사하여 `hasVideo` 반영.
- `SessionDetailScreen`의 `LabSwingSummaryCard`:
  - `hasVideo == true`인 스윙: `[🎬 영상 보기]` 뱃지 및 카드 탭 가능 → 리플레이 네비게이션 실행.
  - `hasVideo == false`인 스윙: `[🎬 영상 보기]` 뱃지를 숨기고 카드 탭 시 리플레이 네비게이션을 실행하지 않음. 수치 정보(궤적 유형, 라켓 면 상태, 에너지 전달 효율 등)만 카드에 텍스트로 표시.
- `SessionCompletionDialog`:
  - `summary.latestRecordId`에 해당하는 비디오가 없을 경우 "🎬 리플레이 보기" 버튼을 비활성화(또는 숨김) 처리.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

### 4.1 `TrailPoint`
```kotlin
package io.github.loje0611.tennisdoc.feature.lab.replay

data class TrailPoint(
    val x: Float,
    val y: Float,
    val progress: Float
)
```

### 4.2 `LabReplayUiState` 확장 필드
```kotlin
data class LabReplayUiState(
    // ... 기존 필드 유지 ...
    val videoPath: String? = null,
    val hasVideo: Boolean = false,
    val swingTrailPoints: List<TrailPoint> = emptyList(),
    val swingPathType: String = "",
    val faceStateLabel: String = "",
    val coachingOneLiner: String = ""
)
```

### 4.3 `LabSwingSummaryItem` 확장 필드
```kotlin
data class LabSwingSummaryItem(
    // ... 기존 필드 유지 ...
    val hasVideo: Boolean = false
)
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

### 5.1 리플레이 화면 레이아웃 (LabReplayScreen)
```
┌──────────────────────────────────────────────┐
│ [←] 스윙 비디오 리플레이                  [⭐]│
├──────────────────────────────────────────────┤
│                                              │
│     [ 실제 스윙 비디오 (ExoPlayer) ]         │
│     (✨ 네온 그린 스윙 궤적 아크 오버레이)   │
│                                              │
│  [▶] ──────●────────── [0.5x] [⏮] [⏭] [🎯] │
├──────────────────────────────────────────────┤
│ 🎾 스윙 궤적 분석                             │
│   • 궤적 유형 : 📈 탑스핀 (상향 스윙 궤적)    │
│   • 임팩트 면 : 🟢 정타 (스퀘어)              │
│   • 💡 "골반→어깨→손목 순서대로 힘 전달 양호"  │
└──────────────────────────────────────────────┘
```

### 5.2 History 세션 상세 화면 스윙 카드
```
┌──────────────────────────────────────────┐
│ #12 • 18:32:10               [🎬 영상 보기]│  ← hasVideo==true
│ 📈 탑스핀 포핸드 | 🟢 스퀘어 | 효율 88%   │
└──────────────────────────────────────────┘
┌──────────────────────────────────────────┐
│ #11 • 18:31:45                           │  ← hasVideo==false (버튼 없음)
│ ⚡ 플랫 포핸드   | 🟠 열림   | 효율 72%   │
└──────────────────────────────────────────┘
```

### 5.3 스타일 가이드
- **궤적 아크 색상**: 네온 그린 (`Color(0xFF00E676)`) 기본, 임팩트 구간 글로우 (`Color(0xFF69F0AE)`, alpha 0.6).
- **비디오 플레이어 배경**: 라운드 코너 12dp, 클립 처리.
- **분석 카드**: `SwingTheme.colors.cardSurface` 배경, `MichromaFont` 헤더.
- 라켓 면 상태 레이블은 한국어로 표시:
  - `SQUARE` → "🟢 정타 (스퀘어)"
  - `OPEN` → "🟠 페이스 열림 (공이 뜨는 원인)"
  - `CLOSED` → "🔵 페이스 닫힘 (네트에 걸리는 원인)"

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **ExoPlayer 메모리 관리**: `DisposableEffect`에서 `player.release()`를 보장하여 메모리 누수 방지.
- **궤적 렌더링 성능**: Canvas 드로잉은 `drawPath` 단일 호출로 처리하여 프레임 드롭 없이 60fps UI 유지.
- **Media3 의존성 범위**: `:feature:lab` 모듈에만 `implementation`으로 한정하여 다른 모듈로의 전이 의존성 차단.
- **하위 호환성**: 기존 `PoseReplayCanvas.kt`, `ImuWaveformChart.kt`, `KineticChainSummaryCard.kt` 소스 파일은 삭제하지 않고 유지. `LabReplayScreen`에서의 호출(사용)만 제거하여 다른 테스트 코드나 향후 개발자 모드 재활용에 영향을 미치지 않도록 한다.

---

## 7. Error Handling & Edge Cases (예외 처리 및 엣지 케이스)

- **비디오 파일 누락/손상 시**: `File(videoPath).exists()` 검사 실패 시 `hasVideo = false`로 폴백하여 빈 상태 안내 표시. 앱 크래시 방지.
- **포즈 프레임이 비어있을 때**: 궤적 아크를 그리지 않고 비디오만 단독 재생.
- **비디오가 없는 레코드에 대한 리플레이 네비게이션 시도**: `LabReplayScreen`에서 `hasVideo == false`일 때 "리플레이 데이터가 없습니다" 안내 표시 (방어적 처리).
- **ExoPlayer 초기화 실패**: `try-catch`로 감싸고 실패 시 비디오 영역에 "영상을 재생할 수 없습니다" 대체 텍스트 표시.

---

## 8. Acceptance Criteria (인수 기준)

- [ ] `gradle/libs.versions.toml`에 `media3-exoplayer` 및 `media3-ui` 의존성이 선언되고 `:feature:lab`에서 정상 resolve된다.
- [ ] `LabReplayScreen`에서 `ImuWaveformChart`, `PoseReplayCanvas`, `KineticChainSummaryCard`의 **호출이 제거**되고, 대신 `SwingVideoPlayer`, `SwingTrailOverlay`, `SwingAnalysisSummaryCard`가 렌더링된다.
- [ ] `hasVideo == true`인 레코드의 리플레이 화면에서 ExoPlayer 기반 비디오가 정상 재생되고, 위에 스윙 궤적 아크가 오버레이된다.
- [ ] `hasVideo == false`인 레코드의 리플레이 화면에서 "리플레이 데이터가 없습니다" 안내가 표시된다.
- [ ] History `SessionDetailScreen`의 `LabSwingSummaryCard`에서 `hasVideo == true`인 스윙에만 `[🎬 영상 보기]` 뱃지가 표시되고 탭 시 리플레이 화면으로 정상 이동한다.
- [ ] `hasVideo == false`인 스윙 카드는 탭해도 리플레이 화면으로 이동하지 않는다.
- [ ] `SessionCompletionDialog`에서 비디오가 저장된 스윙이 없을 경우 "리플레이 보기" 버튼이 비활성화 또는 숨김 처리된다.
- [ ] 라켓 면 상태가 한국어 레이블("🟢 정타 (스퀘어)", "🟠 페이스 열림", "🔵 페이스 닫힘")로 표시된다.
- [ ] `./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug` 명령이 오류 없이 통과한다.

---

## 9. Testing Instructions (테스트 지침)

```bash
cd /home/keunu/personal-project/tennis-sensor-project/TennisDocAI
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```
Specific unit test execution:
```bash
./gradlew :feature:lab:testDebugUnitTest :feature:history:testDebugUnitTest :app:testDebugUnitTest
```
