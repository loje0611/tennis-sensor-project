# TASK-027 — `:feature:lab` CameraX 프레임 파이프라인 및 Preview/Pose Overlay 구현

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-13 | PM | 최초 작성 (CameraX ImageAnalysis, 480p 30fps 해상도 고정 및 TASK-026 PoseLandmarker 연동 명세) |
| v2 | 2026-08-14 | PM | AndroidManifest.xml 카메라 권한(`android.permission.CAMERA`) 및 `uses-feature` 선언 누락 수정 (FR-1, FR-5, AC-6, AC-7 보강) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Android CameraX 패키지(`androidx.camera:*`)를 `:feature:lab` 모듈에 구축하여 실시간 카메라 프리뷰 스트림과 `ImageAnalysis` 분석 파이프라인을 바인딩하는 작업을 정의합니다. SPIKE-01 벤치마크 검증 결과에 따라 480p(640×480) 해상도로 카메라 스트림을 구성하며, 프레임별 이미지를 `TASK-026`에서 구현한 `PoseLandmarkerWrapper`로 전달하여 추출된 `PoseFrame` 관절 좌표를 Compose UI 포즈 오버레이 캔버스(`PoseOverlayCanvas`)에 실시간 시각화합니다. 또한 Android 런타임 권한 동작을 위한 매니페스트 권한 및 피처 선언을 포함합니다.

### 1.2 범위
- `gradle/libs.versions.toml`에 CameraX 최신 안정화 라이브러리(`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) 추가 및 `:feature:lab/build.gradle.kts` 설정.
- `AndroidManifest.xml`(`:feature:lab` 또는 `:app`)에 `<uses-permission android:name="android.permission.CAMERA" />` 및 `<uses-feature android:name="android.hardware.camera" android:required="false" />` 선언 추가.
- `PoseAnalysisAnalyzer` (CameraX `ImageAnalysis.Analyzer` 구현체) 구축:
  - `ImageProxy` 프레임을 변환하여 `PoseLandmarkerWrapper`로 전달.
  - `imageProxy.close()`를 `finally` 블록에서 반드시 호출하여 프레임 버퍼 잠김 방지.
  - 백프레셔 전략: `STRATEGY_KEEP_ONLY_LATEST` (최신 프레임 유지를 통한 지연 차단).
- `LabScreen` Compose UI 및 `PoseOverlayCanvas` 구현:
  - `AndroidView(PreviewView)`를 사용한 실시간 라이브 카메라 프리뷰.
  - 33개 3D 관절 랜드마크 및 뼈대(Skeleton) 연결선(어깨-팔꿈치-손목, 골반-무릎-발목, 상체 박스) 오버레이 렌더링.
  - 실시간 프레임 처리 속도(FPS 및 ms 지연) 디버그 칩 표시.
- 카메라 런타임 권한(`Manifest.permission.CAMERA`) 상태 처리 및 Lifecycle 바인딩/해제 관리.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **`CameraX`**: Android 공식 라이프사이클 인식 카메라 API.
- **`ImageAnalysis.Analyzer`**: 카메라 프레임 버퍼를 수신하여 알림을 받는 콜백 인터페이스.
- **`STRATEGY_KEEP_ONLY_LATEST`**: 처리 중인 프레임이 있을 때 수신되는 중간 프레임을 드롭하고 가장 최신 프레임만 유지하는 전략.
- **`PoseOverlayCanvas`**: 추출된 33개 3D 랜드마크 좌표를 프리뷰 비율에 맞게 2D Canvas에 뼈대 라인으로 그리는 Compose 컴포넌트.

### 2.2 참고 문서
- SPIKE-01 벤치마크 보고서: [`docs/SPIKE-01-mediapipe-benchmark-report.md`](../SPIKE-01-mediapipe-benchmark-report.md)
- MediaPipe 연동 명세: [`docs/specs/TASK-026-mediapipe-pose-landmarker-integration.md`](TASK-026-mediapipe-pose-landmarker-integration.md)
- `:core:vision` 도메인 모델: [`docs/specs/TASK-021-core-vision-angle-calculator.md`](TASK-021-core-vision-angle-calculator.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: Version Catalog, 의존성 및 AndroidManifest 권한 설정
- `gradle/libs.versions.toml`에 CameraX 의존성을 선언한다:
  - `androidx-camera-core`
  - `androidx-camera-camera2`
  - `androidx-camera-lifecycle`
  - `androidx-camera-view`
- `:feature:lab/build.gradle.kts`에 해당 라이브러리를 `implementation`으로 추가한다.
- **매니페스트 권한 선언**: `app/src/main/AndroidManifest.xml` (또는 `:feature:lab/src/main/AndroidManifest.xml`)에 아래 요소를 선언하여 런타임 권한 요청의 전제 조건을 확보한다:
  - `<uses-permission android:name="android.permission.CAMERA" />`
  - `<uses-feature android:name="android.hardware.camera" android:required="false" />`

### FR-2: CameraX 프레임 분석기 (`PoseAnalysisAnalyzer`) 구현
- `ImageAnalysis.Analyzer` 인터페이스를 구현하는 `PoseAnalysisAnalyzer` 클래스를 작성한다.
- `analyze(imageProxy: ImageProxy)` 메서드 내에서:
  - `ImageProxy`를 비트맵/YUV 데이터로 추출.
  - `PoseLandmarkerWrapper.processImage()`를 호출하여 `PoseFrame` 획득.
  - 획득된 `PoseFrame`을 UI 상태 업데이트 콜백으로 전달.
  - **반드시 `finally` 블록에서 `imageProxy.close()`를 수행한다.**

### FR-3: 해상도 및 백프레셔 설정
- SPIKE-01 권고사항에 따라 `ImageAnalysis` 및 `Preview`의 Target Resolution을 640×480 (480p)으로 고정한다.
- `ImageAnalysis.Builder`에 `setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)`를 적용한다.

### FR-4: `LabScreen` Compose UI 및 오버레이 렌더링
- `:feature:lab` 모듈 내에 `LabScreen` Compose 컴포넌트를 구현한다.
- `AndroidView`를 이용해 CameraX `PreviewView`를 렌더링한다.
- `PreviewView` 위에 `PoseOverlayCanvas`를 투명 레이어로 겹쳐 33개 랜드마크 관절 포인트(Circle)와 뼈대 연결선(Line)을 그린다.
  - 주요 연결: 상체(어깨-어깨-골반-골반-어깨), 오른팔(어깨-팔꿈치-손목), 왼팔(어깨-팔꿈치-손목), 오른다리(골반-무릎-발목), 왼다리(골반-무릎-발목).

### FR-5: 카메라 런타임 권한 및 생명주기 관리
- `Manifest.permission.CAMERA`에 대해 런타임 권한 요청 상태를 관리한다.
- 권한이 승인되지 않은 경우: 안내 메시지와 "권한 허용" 버튼을 표시하고, 클릭 시 시스템 권한 요청 다이얼로그(`launchPermissionRequest`)를 호출한다.
- 권한이 승인된 경우: 실시간 카메라 프리뷰 및 오버레이(`CameraPreviewWithOverlay`)를 렌더링한다.
- LifecycleOwner의 `ON_STOP` / `ON_DESTROY` 이벤트 시 `ProcessCameraProvider.unbindAll()`을 통해 카메라 자원을 안전하게 반납한다.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

### 4.1 `PoseAnalysisAnalyzer` 클래스 구조
```kotlin
package io.github.loje0611.tennisdoc.feature.lab.pipeline

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.feature.lab.landmarker.PoseLandmarkerWrapper

class PoseAnalysisAnalyzer(
    private val landmarkerWrapper: PoseLandmarkerWrapper,
    private val onPoseExtracted: (PoseFrame?) -> Unit
) : ImageAnalysis.Analyzer {
    override fun analyze(imageProxy: ImageProxy) {
        try {
            // 이미지 데이터 파싱 및 PoseLandmarkerWrapper 전달
            val poseFrame = landmarkerWrapper.processImage(
                bitmap = imageProxy.toBitmap(),
                frameIndex = imageProxy.sequenceNumber,
                timestampMs = imageProxy.imageInfo.timestamp / 1_000_000
            )
            onPoseExtracted(poseFrame)
        } finally {
            imageProxy.close()
        }
    }
}
```

### 4.2 `LabScreen` Compose 진입점
```kotlin
package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LabScreen(
    modifier: Modifier = Modifier
)
```

---

## 5. UI/UX 요구사항

- **테마 적용**: `:core:ui` 디자인 시스템 테마 색상 및 스타일 준수.
- **카메라 프리뷰**: 비율 유지 렌더링 (`ScaleType.FILL_CENTER` 또는 `FIT_CENTER`).
- **스켈레톤 오버레이**:
  - 관절 키포인트: 반지름 4dp 원형 마커 (Primary Accent Color).
  - 뼈대 연결선: 두께 2dp 라인 (Secondary Accent Color).
- **디버그 오버레이**: 좌측 상단 반투명 칩에 현재 FPS (예: `29.9 FPS | 39ms`) 표시.

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 성능 및 안정성
- Frame drop 최소화: `STRATEGY_KEEP_ONLY_LATEST` 사용.
- `ImageProxy.close()` 누락으로 인한 카메라 스티킹(Freezing) 방지.

### 6.2 모듈 격리
- CameraX 관련 UI 컴포넌트 및 분석기는 `:feature:lab` 내에 국한되며 외부 모듈과의 결합을 생성하지 않는다.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **`ImageProxy.toBitmap()` 실패 또는 예외**: Catch 구문에서 안전하게 처리하고 `imageProxy.close()`가 반드시 실행되도록 보장.
- **카메라 미지원 / 에뮬레이터 환경**: `ProcessCameraProvider` 바인딩 실패 시 UI에 에러 상태 메시지 표시.
- **권한 거부**: 권한 요청 UI 안내 표시 및 재요청 지원.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `libs.versions.toml` 및 `:feature:lab/build.gradle.kts`에 CameraX 의존성이 추가되고 컴파일에 성공한다 (`./gradlew :feature:lab:assembleDebug`).
- [ ] **AC-2**: `PoseAnalysisAnalyzer`가 `ImageProxy`를 파싱하여 `onPoseExtracted` 콜백을 호출하고, `finally` 구문에서 `imageProxy.close()`를 항상 수행한다.
- [ ] **AC-3**: `LabScreen` Compose UI에 `PreviewView` 라이브 프리뷰와 `PoseOverlayCanvas` 포즈 뼈대 오버레이 컴포넌트가 배치된다.
- [ ] **AC-4**: `PoseAnalysisAnalyzer` 단위 테스트에서 Mock `ImageProxy`가 전달되었을 때 `close()` 호출 및 콜백 동작이 검증된다.
- [ ] **AC-5**: `./gradlew :feature:lab:test` 및 `./gradlew verifyModuleDependencies` 명령이 0 Failures로 통과한다.
- [ ] **AC-6**: 매니페스트(`app/src/main/AndroidManifest.xml` 또는 `:feature:lab/src/main/AndroidManifest.xml`)에 `<uses-permission android:name="android.permission.CAMERA" />` 및 `<uses-feature android:name="android.hardware.camera" android:required="false" />`가 선언되어 있어 런타임 권한 요청 시 OS 권한 팝업이 정상 동작한다.
- [ ] **AC-7**: `./gradlew :app:assembleDebug` 빌드가 성공한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :feature:lab:test verifyModuleDependencies :app:assembleDebug
```
