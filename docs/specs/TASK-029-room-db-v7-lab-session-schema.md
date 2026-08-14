# TASK-029 — Room DB v8 마이그레이션 및 Lab 세션 원시 데이터 스키마 구축 (D-7.2)

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (Phase 3 D-7.2 원시 데이터 수집 스키마 및 DB v7➔v8 마이그레이션 명세) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Phase 3 Lab 모드 MVP 개발에 필요한 데이터 영속화 계층을 구축하는 작업을 정의합니다. 기존 Match 세션 중심의 데이터베이스 스키마에 `sessionType`(`MATCH`/`LAB`) 및 `drillType` 필드를 도입하고, 모델 재학습 및 Phase 5 지식 증류(Knowledge Distillation, D-7.3)의 필수 자산인 **`(드릴 라벨, IMU 50Hz 원시 시계열, 30fps PoseFrame 시계열)` 3차원 원시 세션 데이터(D-7.2)** 를 영속화하는 `lab_raw_records` 테이블과 DAO를 추가합니다.

### 1.2 범위
- `:core:model` 모듈에 공용 도메인 열거형 및 데이터 클래스 추가:
  - `SessionType` (`MATCH`, `LAB`)
  - `DrillType` (`FOREHAND_FLAT`, `FOREHAND_TOPSPIN`, `FOREHAND_SLICE`, `BACKHAND_FLAT`, `BACKHAND_TOPSPIN`, `BACKHAND_SLICE`, `SERVE`, `VOLLEY` 등)
  - `LabRawSwingRecord` 도메인 모델
- `:core:data` 모듈 스키마 및 엔티티 확장:
  - `SwingSessionEntity`에 `sessionType: String` (기본값 `"MATCH"`) 및 `drillType: String?` (기본값 `null`) 컬럼 추가.
  - 신규 엔티티 `LabRawRecordEntity` (`lab_raw_records` 테이블) 신설 및 Foreign Key (`swing_sessions.sessionId` CASCADE) 설정.
- Room DAO 인터페이스 확장:
  - `LabRawRecordDao` (또는 `SwingSessionDao` 내 원시 데이터 CRUD 메서드) 구현.
- `TennisDocDatabase` 버전 갱신 (7 ➔ 8) 및 무손실 마이그레이션 `MIGRATION_7_8` 구현:
  - 기존 v7 데이터 유지 및 신규 컬럼/테이블 무손실 생성.
  - Room 스키마 json (`8.json`) 자동 추출.
- 단위 테스트:
  - Robolectric / JVM 환경에서 `MIGRATION_7_8` 스키마 무결성 검증.
  - `LabRawRecordDao` CRUD 및 세션 삭제 시 CASCADE 삭제 동작 검증.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **`D-7.2`**: Lab 모드 실행 시 드릴 정답 라벨과 센서-비전 원시 데이터를 동시 수집하여 영구 보존하는 데이터 인프라 원칙.
- **`MIGRATION_7_8`**: Room Database v7에서 v8로의 스키마 점진적 마이그레이션 객체.
- **`LabRawRecordEntity`**: 단일 스윙 단위로 수집된 IMU 고주파(50Hz) 시계열 데이터와 비전 `PoseFrame` 리스트(JSON 직렬화)를 보관하는 엔티티.

### 2.2 참고 문서
- Phase 3 실행 계획: [`docs/PHASE3_PLAN.md`](../PHASE3_PLAN.md)
- 제품 방향 및 전략 결정: [`docs/PRODUCT_DIRECTION.md`](../PRODUCT_DIRECTION.md) (D-7.2, D-9.1)
- 기존 DB 스키마: [`core/data/schemas/.../7.json`](../../TennisDocAI/core/data/schemas/io.github.loje0611.tennisdoc.core.data.db.TennisDocDatabase/7.json)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: 도메인 모델 정의 (`:core:model`)
- `io.github.loje0611.tennisdoc.core.model` 패키지에 다음 타입들을 정의한다:
  - `enum class SessionType { MATCH, LAB }`
  - `enum class DrillType { FOREHAND_FLAT, FOREHAND_TOPSPIN, FOREHAND_SLICE, BACKHAND_FLAT, BACKHAND_TOPSPIN, BACKHAND_SLICE, SERVE, VOLLEY }`
  - `data class LabRawSwingRecord(val id: Long, val sessionId: String, val drillType: DrillType, val timestampMillis: Long, val imuRawJson: String, val visionPosesJson: String, val impactOffsetMs: Long)`

### FR-2: `SwingSessionEntity` 스키마 확장 (`:core:data`)
- `SwingSessionEntity`에 두 개의 필드를 추가한다:
  - `val sessionType: String = "MATCH"` (`NOT NULL`, 기본값 `"MATCH"`)
  - `val drillType: String? = null` (`NULLABLE`, 기본값 `null`)
- 기존 테이블 `swing_sessions`의 컬럼 구조를 온전히 보존하며 기본값으로 하위 호환성을 유지한다.

### FR-3: 신규 엔티티 `LabRawRecordEntity` 정의 (`:core:data`)
- 테이블명: `lab_raw_records`
- 필드 사양:
  - `id: Long` (기본키, `@PrimaryKey(autoGenerate = true)`)
  - `sessionId: String` (`NOT NULL`, Foreign Key: `SwingSessionEntity.sessionId`, `onDelete = CASCADE`)
  - `drillType: String` (`NOT NULL`, 예: `"FOREHAND_TOPSPIN"`)
  - `timestampMillis: Long` (`NOT NULL`)
  - `imuRawJson: String` (`NOT NULL`, 50Hz Accel/Gyro 시계열 JSON 직렬화 문자열)
  - `visionPosesJson: String` (`NOT NULL`, 30fps `PoseFrame` 리스트 JSON 직렬화 문자열)
  - `impactOffsetMs: Long` (`NOT NULL`, 기본값 `0L`)
- 인덱스: `Index("sessionId")` 설정.

### FR-4: Room DAO (`LabRawRecordDao`) 구현
- `io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao`:
  - `insert(record: LabRawRecordEntity): Long`
  - `getRecordsBySessionId(sessionId: String): Flow<List<LabRawRecordEntity>>`
  - `getRecordById(id: Long): LabRawRecordEntity?`
  - `deleteRecordsBySessionId(sessionId: String): Int`

### FR-5: Database 버전 갱신 및 `MIGRATION_7_8` 구현
- `TennisDocDatabase`:
  - `version = 8`
  - `@Database(entities = [SwingSessionEntity::class, SessionSwingCountEntity::class, SwingEventEntity::class, GlobalStatisticsEntity::class, LabRawRecordEntity::class])`
  - `abstract fun labRawRecordDao(): LabRawRecordDao`
  - `MIGRATION_7_8` 구현:
    ```sql
    ALTER TABLE swing_sessions ADD COLUMN sessionType TEXT NOT NULL DEFAULT 'MATCH';
    ALTER TABLE swing_sessions ADD COLUMN drillType TEXT DEFAULT NULL;
    CREATE TABLE IF NOT EXISTS lab_raw_records (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        sessionId TEXT NOT NULL,
        drillType TEXT NOT NULL,
        timestampMillis INTEGER NOT NULL,
        imuRawJson TEXT NOT NULL,
        visionPosesJson TEXT NOT NULL,
        impactOffsetMs INTEGER NOT NULL DEFAULT 0,
        FOREIGN KEY(sessionId) REFERENCES swing_sessions(sessionId) ON DELETE CASCADE
    );
    CREATE INDEX IF NOT EXISTS index_lab_raw_records_sessionId ON lab_raw_records(sessionId);
    ```
  - `getInstance()`의 `.addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)` 등록.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

### 4.1 도메인 모델 (`:core:model`)
```kotlin
package io.github.loje0611.tennisdoc.core.model

enum class SessionType {
    MATCH,
    LAB
}

enum class DrillType {
    FOREHAND_FLAT,
    FOREHAND_TOPSPIN,
    FOREHAND_SLICE,
    BACKHAND_FLAT,
    BACKHAND_TOPSPIN,
    BACKHAND_SLICE,
    SERVE,
    VOLLEY
}
```

### 4.2 엔티티 및 DAO 인터페이스 (`:core:data`)
```kotlin
package io.github.loje0611.tennisdoc.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lab_raw_records",
    foreignKeys = [
        ForeignKey(
            entity = SwingSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class LabRawRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val drillType: String,
    val timestampMillis: Long,
    val imuRawJson: String,
    val visionPosesJson: String,
    val impactOffsetMs: Long = 0L
)
```

---

## 5. UI/UX 요구사항
- **N/A (영속화 및 도메인 데이터 인프라 모듈)**

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 하위 호환성 및 무손실 마이그레이션
- 기존 v7에 저장된 세션 데이터는 마이그레이션 후에도 손실 없이 읽을 수 있어야 하며, `sessionType`은 자동으로 `"MATCH"`로 초기화되어야 한다.
- `fallbackToDestructiveMigration`이 마이그레이션 실패 시를 제외하고는 호출되지 않아야 한다.

### 6.2 스키마 스키마 파일 관리
- KSP Room 스키마 익스포트(`room.schemaLocation`)에 의해 `core/data/schemas/.../8.json`이 정상 생성되어야 한다.

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **존재하지 않는 `sessionId` 참조**: Foreign Key 제약 조건에 의해 SQLiteForeignKeyConstraintException 발생 확인.
- **세션 삭제 시 동작**: `SwingSessionEntity`가 삭제되면 해당 세션에 종속된 `lab_raw_records` 레코드도 함께 자동 삭제(CASCADE)되어야 함.
- **Null `drillType`**: Match 모드 세션인 경우 `drillType`은 `null`을 허용하며, Lab 모드에서는 유효한 문자열이 저장된다.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `:core:model`에 `SessionType` 및 `DrillType` 도메인 열거형이 정의되어 컴파일에 성공한다.
- [ ] **AC-2**: `SwingSessionEntity`에 `sessionType` 및 `drillType` 컬럼이 추가되고, `LabRawRecordEntity` 엔티티가 `lab_raw_records` 테이블로 정의된다.
- [ ] **AC-3**: `TennisDocDatabase` 버전이 8로 설정되고 `MIGRATION_7_8` 마이그레이션 로직이 구현된다.
- [ ] **AC-4**: `LabRawRecordDao` 인터페이스가 정의되고 Hilt 의존성 주입을 통해 정상 제공된다.
- [ ] **AC-5**: `MIGRATION_7_8` 실행 시 v7 테이블 데이터가 보존되며 신규 컬럼 및 테이블이 정상 생성되는 마이그레이션 단위 테스트가 통과한다.
- [ ] **AC-6**: `LabRawRecordDao`의 삽입, 조회, CASCADE 삭제 동작을 검증하는 단위 테스트가 통과한다.
- [ ] **AC-7**: `./gradlew :core:model:test :core:data:test verifyModuleDependencies` 명령이 0 Failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :core:model:test :core:data:test verifyModuleDependencies :app:assembleDebug
```
