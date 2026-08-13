# TASK-026 — `:feature:lab` MediaPipe Pose Landmarker SDK 연동 및 `PoseFrame` 추출

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-13 | PM | 최초 작성 (SPIKE-01 벤치마크 결과 및 :core:vision 데이터 계약 반영) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Google MediaPipe Tasks Vision Android SDK (`com.google.mediapipe:tasks-vision`)를 `:feature:lab` 모듈에 연동하고, 카메라 이미지 프레임으로부터 33개 3D 관절 랜드마크를 추출하여 `:core:vision`의 `PoseFrame` 도메인 모델로 변환하는 엔진을 구축하는 작업을 정의합니다.

### 1.2 범위
- `gradle/libs.versions.toml`에 MediaPipe Tasks Vision SDK 의존성 추가 및 `:feature:lab/build.gradle.kts` 설정.
- `:feature:lab/src/main/assets/pose_landmarker_lite.task` 모델 파일 배치 및 로딩 파이프라인 구축.
- `MediaPipePoseLandmarkerWrapper` 클래스 구현: 이미지(Bitmap/ImageProxy) 입력 받아 MediaPipe 33개 3D 관절 포즈 추론.
- MediaPipe `PoseLandmarkerResult` 데이터 구조를 `:core:vision` 모듈의 `PoseFrame` 및 `Landmark3D` 계약 형태로 매핑.
- C++ 네이티브 메모리 해제를 위한 `AutoCloseable` 리소스 수명주기 관리.
- JVM 단독 단위 테스트를 위한 Mock/Stub 구조 및 안전망 테스트 추가.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **`PoseLandmarker`**: MediaPipe Tasks Vision 패키지의 포즈 추출 핵심 API.
- **`pose_landmarker_lite.task`**: 실시간 모바일 모드에 최적화된 랜드마크 추출 모델 (약 5.5MB).
- **`PoseFrame`**: `:core:vision` 모듈에서 정의한 3D 관절 데이터 계약 (`frameIndex`, `timestampMs`, `landmarks: List<Landmark3D>`).
- **`Landmark3D`**: `:core:vision` 모듈의 단일 관절 좌표 (`id`, `x`, `y`, `z`, `visibility`, `presence`).

### 2.2 참고 문서
- SPIKE-01 벤치마크 보고서: [`docs/SPIKE-01-mediapipe-benchmark-report.md`](../SPIKE-01-mediapipe-benchmark-report.md)
- `:core:vision` 데이터 계약 명세: [`docs/specs/TASK-021-core-vision-angle-calculator.md`](TASK-021-core-vision-angle-calculator.md)
- Phase 2 통합 실행 계획: [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 의존성 및 버전 카탈로그 설정
- `gradle/libs.versions.toml`에 `mediapipe-tasks-vision = "0.10.14"` (또는 최신 호환 버전) 라이브러리를 추가한다.
- `:feature:lab/build.gradle.kts`에 `implementation(libs.mediapipe.tasks.vision)` 및 `:core:vision` 모듈 의존성을 추가한다.

### FR-2: Asset 모델 파일 관리 및 로더
- `:feature:lab/src/main/assets/pose_landmarker_lite.task` 에셋 파일 로드 패스와 에셋 관리자 인터페이스를 정의한다.
- 모델 파일 부재 시 명시적 `IllegalStateException`을 발생시킨다.

### FR-3: MediaPipe PoseLandmarker 옵션 및 초기화
- `PoseLandmarkerOptions` 설정:
  - `BaseOptions`: ModelAssetPath 설정 (`pose_landmarker_lite.task`), `Delegate.CPU` 사용.
  - `RunningMode`: `IMAGE` (또는 `LIVE_STREAM`).
  - `numPoses`: `1` (단일 테니스 선수 포즈 추적).
  - `minPoseDetectionConfidence`: `0.5f`
  - `minPoseTrackingConfidence`: `0.5f`
  - `minPosePresenceConfidence`: `0.5f`

### FR-4: Landmark3D 및 `PoseFrame` 매핑
- MediaPipe 추론 결과(`PoseLandmarkerResult`)의 33개 랜드마크를 `:core:vision`의 `PoseFrame`으로 변환한다.
- **인덱스 매핑 (0~32 1:1 보장)**:
  - `0`: Nose, `11`: Left Shoulder, `12`: Right Shoulder, `13`: Left Elbow, `14`: Right Elbow, `15`: Left Wrist, `16`: Right Wrist
  - `23`: Left Hip, `24`: Right Hip, `25`: Left Knee, `26`: Right Knee, `27`: Left Ankle, `28`: Right Ankle
- `visibility` 및 `presence` 값은 MediaPipe 결과에서 안전하게 추출하여 `Landmark3D` 객체에 대입한다 (값이 없을 경우 기본값 `1.0f`).

### FR-5: 리소스 수명주기 및 메모리 관리
- `PoseLandmarkerWrapper`는 `AutoCloseable`을 구현한다.
- `close()` 호출 시 네이티브 `PoseLandmarker` 인스턴스를 안전하게 해제(`close()`)하고 다중 호출(Idempotent) 시에도 예외를 발생시키지 않는다.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

### 4.1 핵심 인터페이스
```kotlin
package io.github.loje0611.tennisdoc.feature.lab.landmarker

import android.graphics.Bitmap
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

interface PoseLandmarkerWrapper : AutoCloseable {
    val isInitialized: Boolean
    
    fun processImage(
        bitmap: Bitmap,
        frameIndex: Long,
        timestampMs: Long
    ): PoseFrame?
}
```

### 4.2 클래스 구조 및 의존성
- `MediaPipePoseLandmarkerWrapper`: `PoseLandmarkerWrapper` 구현체.
- Context 또는 AssetManager를 주입받아 초기화 수행.
- JVM 단위 테스트 환경에서는 Native library 미로드 시 가짜/스텁 객체(`FakePoseLandmarkerWrapper`)로 대체 가능하도록 인터페이스 제공.

---

## 5. UI/UX 요구사항
- **N/A (엔진 및 SDK 연동 백엔드 모듈)**

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 성능 (Performance)
- SPIKE-01 벤치마크 결과에 따라 480p (640×480) 이미지 기준 프레임당 평균 지연 40ms 이내 처리.

### 6.2 모듈 격리 (Module Isolation)
- MediaPipe Tasks Vision Android SDK 관련 클래스는 `:feature:lab` 모듈 내부로 캡슐화되며, 외부 모듈에는 `:core:vision`의 `PoseFrame` 결과 객체만 노출한다.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **사람 미검출 (Poses = 0)**: `processImage`는 예외를 던지지 않고 `null`을 반환하거나 빈 landmarks 목록을 가진 `PoseFrame`을 반환한다.
- **이미지 비트맵 무효화 (Recycled / Invalid Bitmap)**: `IllegalArgumentException`을 던지거나 안전하게 log 후 `null` 반환.
- **Native Library 미로드 (JVM UnitTest 환경)**: JVM 단위 테스트 실행 시 `.so` 라이브러리 부재로 인한 크래시를 방지하기 위해 스텁/인터페이스 기반 테스트 지원.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `libs.versions.toml` 및 `:feature:lab/build.gradle.kts`에 MediaPipe Tasks Vision 의존성이 추가되고 모듈 빌드가 성공한다 (`./gradlew :feature:lab:assembleDebug`).
- [ ] **AC-2**: `pose_landmarker_lite.task` 에셋 파일이 `:feature:lab/src/main/assets/`에 정상 배치된다.
- [ ] **AC-3**: `MediaPipePoseLandmarkerWrapper`가 33개 관절 좌표를 `:core:vision`의 `PoseFrame` 규격으로 매핑하여 반환한다.
- [ ] **AC-4**: 33개 관절 인덱스(0~32) 및 x, y, z 좌표 매핑 단위 테스트가 통과한다.
- [ ] **AC-5**: `close()` 호출 시 네이티브 리소스가 해제되며 다중 호출에도 에러가 발생하지 않는다.
- [ ] **AC-6**: `./gradlew :feature:lab:test` 및 `./gradlew verifyModuleDependencies` 명령이 에러 없이 통과한다 (Unit Tests 0 failures).

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :feature:lab:test verifyModuleDependencies assembleDebug
```
