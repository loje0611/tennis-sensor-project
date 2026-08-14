package io.github.loje0611.tennisdoc.core.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private val dbName = "task029-migration-7-8.db"

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(dbName)
    }

    @Test
    fun migrate7to8_preservesSessionsAndCreatesLabRawRecords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS swing_sessions (
                                sessionId TEXT NOT NULL,
                                sessionName TEXT NOT NULL,
                                startTime INTEGER NOT NULL,
                                endTime INTEGER,
                                totalSwingCount INTEGER NOT NULL,
                                durationMillis INTEGER NOT NULL,
                                forehandVolleyCount INTEGER NOT NULL,
                                backhandVolleyCount INTEGER NOT NULL,
                                PRIMARY KEY(sessionId)
                            )
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )

        val db = openHelper.writableDatabase
        db.execSQL(
            """
            INSERT INTO swing_sessions (
                sessionId, sessionName, startTime, totalSwingCount,
                durationMillis, forehandVolleyCount, backhandVolleyCount
            ) VALUES ('legacy-1', 'Old Session', 1000, 4, 0, 0, 0)
            """.trimIndent(),
        )

        TennisDocDatabase.MIGRATION_7_8.migrate(db)

        db.query("SELECT sessionName, sessionType, drillType, totalSwingCount FROM swing_sessions WHERE sessionId = 'legacy-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Old Session", cursor.getString(0))
            assertEquals("MATCH", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals(4, cursor.getInt(3))
        }

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='lab_raw_records'").use { cursor ->
            assertTrue("lab_raw_records must exist after MIGRATION_7_8", cursor.moveToFirst())
        }

        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_lab_raw_records_sessionId'").use { cursor ->
            assertTrue("sessionId index must exist after MIGRATION_7_8", cursor.moveToFirst())
        }

        db.close()
        openHelper.close()
    }
}
