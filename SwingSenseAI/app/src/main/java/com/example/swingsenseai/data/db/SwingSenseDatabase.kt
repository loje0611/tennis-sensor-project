package com.example.swingsenseai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.swingsenseai.data.db.dao.GlobalStatisticsDao
import com.example.swingsenseai.data.db.dao.SwingSessionDao
import com.example.swingsenseai.data.db.entity.GlobalStatisticsEntity
import com.example.swingsenseai.data.db.entity.SessionSwingCountEntity
import com.example.swingsenseai.data.db.entity.SwingEventEntity
import com.example.swingsenseai.data.db.entity.SwingSessionEntity

@Database(
    entities = [
        SwingSessionEntity::class,
        SessionSwingCountEntity::class,
        SwingEventEntity::class,
        GlobalStatisticsEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class SwingSenseDatabase : RoomDatabase() {

    abstract fun swingSessionDao(): SwingSessionDao
    abstract fun globalStatisticsDao(): GlobalStatisticsDao

    companion object {
        @Volatile
        private var instance: SwingSenseDatabase? = null

        /**
         * v5→v6: 스키마 변경 없음 (마이그레이션 인프라 도입 기점).
         * 이후 스키마 변경 시 여기에 Migration 추가.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op: 스키마 동일, 마이그레이션 인프라 도입용 버전 범프
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE swing_events ADD COLUMN rawMaxAccel REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE swing_events ADD COLUMN rawDurationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE swing_events ADD COLUMN rawGyroFollow REAL NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): SwingSenseDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SwingSenseDatabase::class.java,
                    "swingsense.db",
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
