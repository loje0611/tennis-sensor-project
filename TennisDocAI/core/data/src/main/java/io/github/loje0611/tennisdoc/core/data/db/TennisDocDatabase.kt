package io.github.loje0611.tennisdoc.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.loje0611.tennisdoc.core.data.db.dao.GlobalStatisticsDao
import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import io.github.loje0611.tennisdoc.core.data.db.dao.SwingSessionDao
import io.github.loje0611.tennisdoc.core.data.db.entity.GlobalStatisticsEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SessionSwingCountEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity

@Database(
    entities = [
        SwingSessionEntity::class,
        SessionSwingCountEntity::class,
        SwingEventEntity::class,
        GlobalStatisticsEntity::class,
        LabRawRecordEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class TennisDocDatabase : RoomDatabase() {

    abstract fun swingSessionDao(): SwingSessionDao
    abstract fun globalStatisticsDao(): GlobalStatisticsDao
    abstract fun labRawRecordDao(): LabRawRecordDao

    companion object {
        @Volatile
        private var instance: TennisDocDatabase? = null

        /**
         * v5→v6: 스키마 변경 없음 (마이그레이션 인프라 도입 기점).
         * 이후 스키마 변경 시 여기에 Migration 추가.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op: 스키마 동일, 마이그레이션 인프라 도입용 버전 범프
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE swing_events ADD COLUMN rawMaxAccel REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE swing_events ADD COLUMN rawDurationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE swing_events ADD COLUMN rawGyroFollow REAL NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE swing_sessions ADD COLUMN sessionType TEXT NOT NULL DEFAULT 'MATCH'")
                db.execSQL("ALTER TABLE swing_sessions ADD COLUMN drillType TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lab_raw_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        drillType TEXT NOT NULL,
                        timestampMillis INTEGER NOT NULL,
                        imuRawJson TEXT NOT NULL,
                        visionPosesJson TEXT NOT NULL,
                        impactOffsetMs INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(sessionId) REFERENCES swing_sessions(sessionId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_raw_records_sessionId ON lab_raw_records(sessionId)")
            }
        }

        fun getInstance(context: Context): TennisDocDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TennisDocDatabase::class.java,
                    "swingsense.db",
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
