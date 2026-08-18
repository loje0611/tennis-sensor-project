# TASK-052 명세서: CameraX 2초 스윙 클립 자동 녹화 및 융합 파이프라인 연동

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-18 | PM | 최초 작성 (Phase 5 스윙 비디오 녹화: 롤링 프레임 버퍼링, H.264 MP4 인코더, LabFusionPipeline 비디오 저장 및 VideoFileManager 보관 정책 연동) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
[`TASK-051`](TASK-051-video-storage-and-preferences-infrastructure.md)을 통해 비디오 저장을 위한 Room DB v10 스키마(`videoPath`), `VideoPreferencesRepository`(DataStore), `VideoFileManager`(LRU 자동 정리), 그리고 설정 화면(`SettingsScreen`) 인프라가 구축되었습니다.
본 태스크(`TASK-052`)는 **CameraX 실시간 프리뷰 스트림에서 스윙 감지 시점 전후 2~3초 분량의 비디오 클립을 자동으로 캡처하여 H.264 MP4 파일로 인코딩하고, DB 레코드(`LabRawRecordEntity.videoPath`)에 매핑**하는 핵심 녹화 파이프라인을 구현합니다.

사용자가 스윙을 시작한 후에 임팩트가 감지되므로, 일반적인 Start/Stop 녹화 대신 **롤링 프레임 버퍼(Rolling Circular Frame Buffer)**를 운영하여 테이크백부터 임팩트, 팔로우스루까지 온전한 스윙 동작 2초 클립을 손실 없이 보존합니다.

### 1.2 범위
- `:feature:lab` 모듈:
  - `SwingVideoFrame` (타임스탬프 및 프레임 비트맵 데이터 캡슐화) 및 `SwingVideoBuffer` (3000ms 윈도우 롤링 서큘러 버퍼) 구현.
  - `SwingVideoEncoder` 인터페이스 및 구현체 (`MediaCodec` + `MediaMuxer` 기반 480p 30fps H.264 MP4 인코더).
  - `PoseAnalysisAnalyzer` 및 `LabFusionPipeline`:
    - 카메라 프레임 수신 시 비디오 버퍼로 프레임 전달 (`feedVideoFrame`).
    - `onSwingTriggered` 시 `VideoPreferencesRepository.autoSaveVideoEnabled` 상태 확인.
    - 활성화 시 `SwingVideoEncoder`로 MP4 파일 생성 ➔ `LabRawRecordEntity.videoPath` 저장 ➔ `VideoFileManager.enforceRetentionPolicy` 비동기 트리거.
    - 비활성화 시 비디오 인코딩을 건너뛰고 `videoPath = null`로 저장하여 불필요한 연산 및 용량 소모 차단.
- 단위 및 Robolectric 테스트:
  - `SwingVideoBufferTest`: 버퍼 시간 초과 프레임 자동 프루닝(Pruning) 및 스냅샷 정합성 검증.
  - `SwingVideoEncoderTest`: 프레임 목록으로부터 유효한 MP4 파일 생성 및 파일 크기/헤더 검증.
  - `LabFusionPipelineVideoTest`: 자동 저장 ON/OFF에 따른 `videoPath` 저장 동작 및 보관 정책 호출 검증.

---

## 2. Definitions & References (정의 및 참조)

### 2.1 주요 정의
- **롤링 프레임 버퍼 (`SwingVideoBuffer`)**: 최근 3초간의 카메라 프레임을 메모리에 유지하다가 스윙 트리거 시점에 과거~현재 프레임을 즉시 추출하는 순환 큐.
- **`SwingVideoEncoder`**: 수집된 비트맵/프레임 시계열을 안드로이드 하드웨어 가속 미디어 코덱(`MediaCodec` AVC/H.264)을 이용해 480p 30fps `.mp4` 파일로 압축 인코딩하는 엔진.
- **스윙 전후 윈도우**: 임팩트 시점을 기준으로 테이크백(약 1초 전)부터 팔로우스루(약 1초 후)까지 총 약 2초(60프레임) 분량의 영상.

### 2.2 참고 문서
- 스토리지 인프라 명세: [`docs/specs/TASK-051-video-storage-and-preferences-infrastructure.md`](TASK-051-video-storage-and-preferences-infrastructure.md)
- CameraX 파이프라인 명세: [`docs/specs/TASK-027-camerax-frame-pipeline.md`](TASK-027-camerax-frame-pipeline.md)
- 실시간 융합 파이프라인 명세: [`docs/specs/TASK-037-realtime-fusion-pipeline-integration.md`](TASK-037-realtime-fusion-pipeline-integration.md)

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: `SwingVideoBuffer` 롤링 프레임 버퍼 구현 (`:feature:lab`)
- `io.github.loje0611.tennisdoc.feature.lab.pipeline.SwingVideoBuffer`:
  - 기본 버퍼 윈도우: `bufferDurationMs = 3000L` (최대 3초).
  - `fun addFrame(bitmap: Bitmap, timestampMs: Long)`:
    - 메모리 효율을 위해 비트맵 복사본(또는 재활용 버퍼)을 큐에 보관하고, `timestampMs < now - bufferDurationMs`인 오래된 프레임은 비트맵 `recycle()` 후 큐에서 제거.
  - `fun snapshot(): List<SwingVideoFrame>`:
    - 현재 큐에 보관된 프레임 목록(타임스탬프 오름차순)을 안전하게 복사하여 반환.
  - `fun clear()`: 큐의 모든 비트맵을 `recycle()`하고 비움.

### FR-2: `SwingVideoEncoder` MP4 비디오 인코더 구현 (`:feature:lab`)
- `io.github.loje0611.tennisdoc.feature.lab.pipeline.SwingVideoEncoder`:
  ```kotlin
  interface SwingVideoEncoder {
      suspend fun encodeToMp4(
          frames: List<SwingVideoFrame>,
          outputFile: File,
          width: Int = 480,
          height: Int = 640,
          fps: Int = 30,
          bitrate: Int = 1_500_000
      ): Boolean
  }
  ```
- `MediaCodec` (MIME: `video/avc`) 및 `MediaMuxer` (Format: `MUXER_OUTPUT_MPEG_4`)를 사용하여 Surface 인풋 또는 Canvas 렌더링을 통해 MP4 파일 작성.
- 프레임 리스트가 비어있거나 인코딩 실패 시 예외를 전파하지 않고 `false` 반환 및 임시 파일 정리(Fail-safe).

### FR-3: `PoseAnalysisAnalyzer` 프레임 공급 확장 (`:feature:lab`)
- `PoseAnalysisAnalyzer`에 비디오 프레임 전달 콜백 추가:
  - `onFrameAvailable: ((Bitmap, Long) -> Unit)? = null`
  - 카메라에서 회전 보정된 `processedBitmap`을 `PoseLandmarkerWrapper`로 전달함과 동시에 `onFrameAvailable` 콜백으로 전달.

### FR-4: `LabFusionPipeline` 비디오 저장 및 보관 정책 통합 (`:feature:lab`)
- `LabFusionPipelineImpl`에 의존성 주입:
  - `videoPreferencesRepository: VideoPreferencesRepository? = null`
  - `videoFileManager: VideoFileManager? = null`
  - `videoEncoder: SwingVideoEncoder = SwingVideoEncoderImpl()`
  - `videoBuffer: SwingVideoBuffer = SwingVideoBuffer()`
- **동작 흐름**:
  1. `feedVideoFrame(bitmap: Bitmap, timestampMs: Long)`: `videoBuffer.addFrame(bitmap, timestampMs)` 호출.
  2. `onSwingTriggered(sessionId, drillType)` 호출 시:
     - `videoPreferencesRepository.autoSaveVideoEnabled.first()` 확인.
     - `autoSaveVideoEnabled == true`인 경우:
       - `val videoFrames = videoBuffer.snapshot()`
       - `val tempRecordId = System.currentTimeMillis()` (또는 UUID)
       - `val targetFile = videoFileManager.generateVideoFile(sessionId, tempRecordId)`
       - `val success = videoEncoder.encodeToMp4(videoFrames, targetFile)`
       - 인코딩 성공 시 `videoPath = targetFile.absolutePath`, 실패 시 `null`.
       - `LabRawRecordEntity(..., videoPath = videoPath)`로 Room DB 저장.
       - 저장 완료 후 `videoPreferencesRepository.videoRetentionOption.first()`를 읽어 `videoFileManager.enforceRetentionPolicy(option.maxCount)`를 백그라운드 코루틴으로 실행.
     - `autoSaveVideoEnabled == false`인 경우:
       - 인코딩을 수행하지 않고 `videoPath = null`로 Room DB 저장.
  3. `reset()` 호출 시 `videoBuffer.clear()` 수행.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

### 4.1 `SwingVideoFrame` & `SwingVideoBuffer`
```kotlin
package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Bitmap

data class SwingVideoFrame(
    val timestampMs: Long,
    val bitmap: Bitmap
)

class SwingVideoBuffer(
    private val bufferDurationMs: Long = 3000L
) {
    fun addFrame(bitmap: Bitmap, timestampMs: Long)
    fun snapshot(): List<SwingVideoFrame>
    fun clear()
}
```

### 4.2 `SwingVideoEncoder`
```kotlin
package io.github.loje0611.tennisdoc.feature.lab.pipeline

import java.io.File

interface SwingVideoEncoder {
    suspend fun encodeToMp4(
        frames: List<SwingVideoFrame>,
        outputFile: File,
        width: Int = 480,
        height: Int = 640,
        fps: Int = 30,
        bitrate: Int = 1_500_000
    ): Boolean
}
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- 본 태스크는 백엔드 및 실시간 미디어 파이프라인 태스크로서 별도 신규 UI 화면을 추가하지 않습니다.
- 기존 `LabScreen` 카메라 프리뷰 및 포즈 오버레이 화면에 버벅임(Jank)이나 FPS 저하가 발생하지 않도록 비디오 인코딩 작업은 반드시 백그라운드 I/O 스레드에서 비동기 처리되어야 합니다.

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **메모리 보호**: 롤링 버퍼에 적재되는 비트맵은 최대 3초(약 90프레임 이하)로 엄격히 제한되며, 오래된 비트맵은 즉시 `recycle()`하여 OOM(Out of Memory)을 방지.
- **인코딩 지연 최소화**: 2초 분량 MP4 인코딩 시간은 일반적인 모바일 기기 기준 300ms 이내에 완료되어야 함.
- **비디오 파일 용량**: H.264 인코딩 결과 단일 2초 스윙 클립 파일 크기는 **600 KB 이하**로 유지.

---

## 7. Error Handling & Edge Cases (예외 처리 및 엣지 케이스)

- **프레임 버퍼가 비어있을 때**: 인코딩을 시도하지 않고 `videoPath = null`로 안전하게 리턴.
- **디스크 공간 부족 또는 I/O 에러 시**: 에러 로그를 남기고 크래시 없이 `videoPath = null`로 DB에 저장(무중단 보장).
- **카메라 회전(세로/가로) 대응**: `PoseAnalysisAnalyzer`에서 이미 회전 보정된 비트맵을 전달받으므로 일관된 세로 비율(480×640)로 인코딩.

---

## 8. Acceptance Criteria (인수 기준)

- [ ] `SwingVideoBuffer`에 3초 이상의 프레임을 지속 주입했을 때, 오래된 프레임이 정리되고 최근 3초 이내 프레임만 유지된다.
- [ ] `SwingVideoEncoder`가 전달받은 프레임 리스트를 유효한 MP4 파일로 정상 인코딩한다.
- [ ] `autoSaveVideoEnabled == true`인 상태에서 `onSwingTriggered` 호출 시 생성된 MP4 파일 경로가 `LabRawRecordEntity.videoPath`에 저장된다.
- [ ] `autoSaveVideoEnabled == false`인 상태에서 `onSwingTriggered` 호출 시 비디오 인코딩이 생략되고 `videoPath`는 `null`이 된다.
- [ ] 스윙 저장 완료 후 `VideoFileManager.enforceRetentionPolicy`가 정상 호출되어 보관 한도가 유지된다.
- [ ] `./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug` 명령이 오류 없이 통과한다.

---

## 9. Testing Instructions (테스트 지침)

```bash
cd /home/keunu/personal-project/tennis-sensor-project/TennisDocAI
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```
Specific unit test execution:
```bash
./gradlew :feature:lab:testDebugUnitTest
```
