# TASK-051 명세서: 스윙 비디오 스토리지 & 설정 인프라 구축

## Revision History

| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-18 | PM | 최초 작성 (Phase 5 실스윙 비디오 연동 인프라: Room DB v10 마이그레이션, VideoPreferences DataStore, VideoFileManager 스토리지 LRU 관리 및 SettingsScreen UI 구현) |

---

## 1. Overview & Scope (개요 및 범위)

### 1.1 배경 및 목적
현재 앱은 Lab 연습 세션에서 33개 관절 랜드마크 좌표(`PoseFrame`)만 추출하여 수치와 스켈레톤 라인만 표시하고 있어, 사용자가 자신의 실제 스윙 폼과 미스샷 원인을 직관적으로 파악하기 어렵습니다.
본 태스크(`TASK-051`)는 **실제 스윙 비디오 클립을 영구 저장하고 리플레이에서 활용하기 위한 데이터 및 스토리지 인프라**를 구축합니다.
사용자가 기기 용량에 맞춰 **비디오 자동 저장 여부(ON/OFF)**와 **최대 보관 개수(20/50/100/200/무제한)**를 직접 제어할 수 있도록 DataStore 환경설정 및 `SettingsScreen` UI를 구현하고, Room DB v10 마이그레이션(`videoPath` 컬럼 추가) 및 오래된 영상을 자동 정리하는 `VideoFileManager`를 구축합니다.

### 1.2 범위
- `:core:model` 모듈:
  - `VideoRetentionOption` (보관 개수 옵션 열거형: `COUNT_20`, `COUNT_50`, `COUNT_100`, `COUNT_200`, `UNLIMITED`) 정의.
- `:core:data` 모듈:
  - `LabRawRecordEntity`에 `videoPath: String? = null` 컬럼 추가.
  - `TennisDocDatabase` 버전 9 ➔ 10 업그레이드 및 `MIGRATION_9_10` 구현.
  - `LabRawRecordDao`에 비디오 경로 갱신(`updateVideoPath`), 비디오 보유 레코드 조회 및 비디오 경로 null 처리 메서드 추가.
  - `VideoPreferencesRepository` 구현 (Jetpack DataStore 기반 비디오 자동 저장 ON/OFF 및 최대 보관 개수 영속화).
  - `VideoFileManager` 인터페이스 및 구현체 작성 (앱 내부 저장소 경로 관리, 사용 용량 계산, 개수 초과 시 FIFO 자동 삭제, 전체 비디오 캐시 일괄 삭제).
  - `CoreDataModule`에 `VideoPreferencesRepository`, `VideoFileManager` Hilt 싱글톤 바인딩.
- `:app` 모듈:
  - `SettingsViewModel`에 비디오 환경설정 및 저장소 사용량 상태(`savedVideoCount`, `usedStorageText`) 연동.
  - `SettingsScreen`에 **[📹 스윙 영상 & 저장소 설정]** 카드 섹션 구현 (토글 스위치, 보관 개수 드롭다운, 사용량 표시, 캐시 비우기 확인 다이얼로그).
- 단위 및 Robolectric 테스트:
  - `VideoPreferencesRepositoryTest`: DataStore CRUD 및 기본값 검증.
  - `VideoFileManagerTest`: 파일 생성, 크기 계산, 오래된 파일 우선 삭제(FIFO) 검증.
  - `TennisDocDatabaseMigrationTest`: `MIGRATION_9_10` 스키마 무결성 검증.
  - `VideoSettingsUiTest`: Compose UI 렌더링 및 설정 변경 액션 검증.

---

## 2. Definitions & References (정의 및 참조)

### 2.1 주요 정의
- **`videoPath`**: 로컬 파일 시스템 내 저장된 MP4 비디오 클립의 절대 파일 경로(예: `/data/user/0/.../files/swing_videos/swing_12345.mp4`).
- **`VideoRetentionOption`**: 기기 저장 공간 절약을 위해 보관할 최대 비디오 클립 수(기본값: 최근 50개, 약 25MB).
- **`MIGRATION_9_10`**: `lab_raw_records` 테이블에 `videoPath TEXT DEFAULT NULL` 컬럼을 안전하게 추가하는 Room 무손실 마이그레이션.
- **`VideoFileManager`**: 파일 I/O 및 디스크 용량 관리 전담 클래스로, DB 레코드 상태와 실제 물리 파일의 정합성을 보장.

### 2.2 참고 문서
- Room DB 스키마: [`TennisDocDatabase.kt`](../../TennisDocAI/core/data/src/main/java/io/github/loje0611/tennisdoc/core/data/db/TennisDocDatabase.kt)
- AI 코치 설정 참조: [`AiCoachPreferencesRepository.kt`](../../TennisDocAI/core/data/src/main/java/io/github/loje0611/tennisdoc/core/data/repository/AiCoachPreferencesRepository.kt)
- 설정 화면 UI: [`SettingsScreen.kt`](../../TennisDocAI/app/src/main/java/io/github/loje0611/tennisdoc/ui/settings/SettingsScreen.kt)

---

## 3. Functional Requirements (기능 요구사항)

### FR-1: `VideoRetentionOption` 도메인 모델 정의 (`:core:model`)
- `io.github.loje0611.tennisdoc.core.model.VideoRetentionOption` 정의:
  ```kotlin
  enum class VideoRetentionOption(val maxCount: Int, val displayName: String, val approximateSize: String) {
      COUNT_20(20, "최근 20개", "약 10 MB"),
      COUNT_50(50, "최근 50개 (권장)", "약 25 MB"),
      COUNT_100(100, "최근 100개", "약 50 MB"),
      COUNT_200(200, "최근 200개", "약 100 MB"),
      UNLIMITED(-1, "무제한", "수동 관리");

      companion object {
          fun fromCount(count: Int): VideoRetentionOption =
              entries.firstOrNull { it.maxCount == count } ?: COUNT_50
      }
  }
  ```

### FR-2: `LabRawRecordEntity` 확장 및 `MIGRATION_9_10` (`:core:data`)
- `LabRawRecordEntity`에 `val videoPath: String? = null` 컬럼 추가.
- `TennisDocDatabase`:
  - `version = 10`
  - `MIGRATION_9_10` 정의:
    ```sql
    ALTER TABLE lab_raw_records ADD COLUMN videoPath TEXT DEFAULT NULL;
    ```
  - `getInstance()`의 마이그레이션 목록에 `MIGRATION_9_10` 추가.
- `LabRawRecordDao` 메서드 추가:
  - `@Query("UPDATE lab_raw_records SET videoPath = :videoPath WHERE id = :id") suspend fun updateVideoPath(id: Long, videoPath: String?)`
  - `@Query("SELECT * FROM lab_raw_records WHERE videoPath IS NOT NULL ORDER BY timestampMillis ASC") suspend fun getRecordsWithVideoAsc(): List<LabRawRecordEntity>`
  - `@Query("SELECT COUNT(*) FROM lab_raw_records WHERE videoPath IS NOT NULL") fun observeVideoRecordCount(): Flow<Int>`

### FR-3: `VideoPreferencesRepository` 구현 (`:core:data`)
- `preferencesDataStore(name = "video_preferences")` 기반 구현.
- **스트림**:
  - `val autoSaveVideoEnabled: Flow<Boolean>` (기본값: `true`)
  - `val videoRetentionOption: Flow<VideoRetentionOption>` (기본값: `VideoRetentionOption.COUNT_50`)
- **수정 메서드**:
  - `suspend fun setAutoSaveVideoEnabled(enabled: Boolean)`
  - `suspend fun setVideoRetentionOption(option: VideoRetentionOption)`

### FR-4: `VideoFileManager` 스토리지 관리자 구현 (`:core:data`)
- 인터페이스 및 구현체 (`VideoFileManagerImpl`):
  ```kotlin
  interface VideoFileManager {
      fun getVideoDirectory(): File
      fun generateVideoFile(sessionId: String, recordId: Long): File
      fun getUsedStorageBytes(): Long
      fun formatStorageSize(bytes: Long): String
      suspend fun deleteVideoFile(filePath: String): Boolean
      suspend fun clearAllVideos(): Int
      suspend fun enforceRetentionPolicy(maxCount: Int): Int
  }
  ```
- **보관 정책 실행 (`enforceRetentionPolicy`)**:
  - `maxCount <= 0` (무제한)일 경우 정리 생략.
  - `maxCount > 0`인 경우: `labRawRecordDao.getRecordsWithVideoAsc()`를 조회하여 현재 비디오 레코드 수가 `maxCount`를 초과할 때, **가장 오래된 레코드의 물리 비디오 파일(`.mp4`)을 삭제**하고 DB의 `videoPath`를 `null`로 갱신. (JSON 수치 및 포즈 데이터는 삭제하지 않고 유지)
- **전체 캐시 삭제 (`clearAllVideos`)**:
  - 저장 디렉토리 내의 모든 비디오 파일을 삭제하고, DB의 모든 `videoPath`를 `null`로 일괄 정리.

### FR-5: `SettingsViewModel` & `SettingsScreen` UI 구현 (`:app`)
- **`SettingsViewModel`**:
  - `videoPreferencesRepository`와 `videoFileManager` 주입.
  - UI State에 `autoSaveVideoEnabled: StateFlow<Boolean>`, `videoRetentionOption: StateFlow<VideoRetentionOption>`, `savedVideoCount: StateFlow<Int>`, `usedStorageText: StateFlow<String>` 노출.
  - 액션: `toggleAutoSaveVideo(Boolean)`, `selectVideoRetentionOption(VideoRetentionOption)`, `clearVideoCache()`.
- **`SettingsScreen` [📹 스윙 영상 & 저장소 설정] 섹션**:
  - Clean Sunlit Court 스타일 카드 컴포넌트(`SwingTheme.colors.cardSurface`, 둥근 모서리 `16.dp`).
  - **스윙 영상 자동 저장 스위치**:
    - 타이틀: "스윙 영상 자동 저장", 서브텍스트: "스윙 감지 시 2초 비디오 클립을 저장합니다."
    - Switch 토글 동작.
  - **최대 보관 클립 수 선택기**:
    - `autoSaveVideoEnabled == true`일 때만 활성화(비활성화 시 반투명).
    - 드롭다운(ExposedDropdownMenuBox)을 통해 20개 / 50개(권장) / 100개 / 200개 / 무제한 선택.
  - **저장소 사용량 및 비디오 캐시 삭제**:
    - 현재 저장된 영상 수 및 사용 용량 표시 (예: "저장된 비디오: 32개 / 16.4 MB").
    - **[🗑️ 비디오 캐시 전체 삭제]** 아웃라인 버튼 ➔ 클릭 시 확인 다이얼로그(`AlertDialog`) 팝업 후 삭제 실행.

---

## 4. Interfaces & Data Structures (인터페이스 및 데이터 구조)

### 4.1 `VideoPreferencesRepository`
```kotlin
package io.github.loje0611.tennisdoc.core.data.repository

import io.github.loje0611.tennisdoc.core.model.VideoRetentionOption
import kotlinx.coroutines.flow.Flow

interface VideoPreferencesRepository {
    val autoSaveVideoEnabled: Flow<Boolean>
    val videoRetentionOption: Flow<VideoRetentionOption>
    suspend fun setAutoSaveVideoEnabled(enabled: Boolean)
    suspend fun setVideoRetentionOption(option: VideoRetentionOption)
}
```

### 4.2 `VideoFileManager`
```kotlin
package io.github.loje0611.tennisdoc.core.data.repository

import java.io.File

interface VideoFileManager {
    fun getVideoDirectory(): File
    fun generateVideoFile(sessionId: String, recordId: Long): File
    fun getUsedStorageBytes(): Long
    fun formatStorageSize(bytes: Long): String
    suspend fun deleteVideoFile(filePath: String): Boolean
    suspend fun clearAllVideos(): Int
    suspend fun enforceRetentionPolicy(maxCount: Int): Int
}
```

---

## 5. UI/UX Requirements (UI/UX 요구사항)

- **테마 일관성**: Clean Sunlit Court 테마 컬러(`SwingTheme.colors`) 및 폰트(`MichromaFont`) 준수.
- **카드 배치**: `SettingsScreen` 내 `[🤖 AI 코치 설정]` 카드 하단에 `[📹 스윙 영상 & 저장소 설정]` 카드를 배치.
- **인터랙션 피드백**:
  - 비디오 캐시 삭제 시 `Toast` 메시지: `"스윙 영상 캐시가 모두 삭제되었습니다."` 노출.
  - 보관 개수 변경 시 즉시 백그라운드에서 `enforceRetentionPolicy`가 트리거되어 초과 파일 정리.

---

## 6. Non-Functional Requirements (비기능 요구사항)

- **안전한 디스크 I/O**: 파일 삭제 및 용량 계산은 반드시 `Dispatchers.IO` 코루틴 컨텍스트에서 비동기로 수행.
- **데이터베이스 무결성**: Room DB 마이그레이션 시 기존 버전(v9)의 모든 데이터(세션, 이벤트, AI 코치 리포트 등)가 손실 없이 보존되어야 함.
- **저장소 안전성**: 앱 삭제 시 비디오 파일도 함께 정리되도록 `context.filesDir` (또는 `context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)`) 하위 전용 디렉토리 사용.

---

## 7. Error Handling & Edge Cases (예외 처리 및 엣지 케이스)

- **파일 삭제 시 실제 파일이 없는 경우**: 예외를 던지지 않고 `false` 반환 및 DB 컬럼만 정상 갱신.
- **저장 용량이 0 Byte인 경우**: `"0 MB"`로 정상 포맷팅하여 표시.
- **마이그레이션 실패 방지**: `videoPath`는 `NULLABLE`로 선언되어 기존 v9 레코드와의 완전한 하위 호환성 유지.

---

## 8. Acceptance Criteria (인수 기준)

- [ ] `MIGRATION_9_10` 실행 시 `lab_raw_records` 테이블에 `videoPath` 컬럼이 생성되고 기존 데이터가 보존된다.
- [ ] `VideoPreferencesRepository`에 `autoSaveVideoEnabled`(기본값 true) 및 `videoRetentionOption`(기본값 COUNT_50)이 정상 저장/복원된다.
- [ ] `VideoFileManager.enforceRetentionPolicy(50)` 호출 시 50개를 초과하는 가장 오래된 비디오 파일이 삭제되고 DB의 `videoPath`가 null로 갱신된다.
- [ ] `VideoFileManager.clearAllVideos()` 호출 시 모든 비디오 파일이 디스크에서 삭제된다.
- [ ] `SettingsScreen`에 `[📹 스윙 영상 & 저장소 설정]` 카드가 렌더링되고, 토글 및 보관 개수 드롭다운 변경이 즉시 반영된다.
- [ ] `./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug` 명령이 오류 없이 통과한다.

---

## 9. Testing Instructions (테스트 지침)

```bash
cd /home/keunu/personal-project/tennis-sensor-project/TennisDocAI
./gradlew verifyModuleDependencies verifyJniBindings test assembleDebug
```
Specific unit test execution:
```bash
./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
```
