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
class Migration8To9Test {

    private val dbName = "task043-migration-8-9.db"

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(dbName)
    }

    @Test
    fun migrate8to9_addsAiCoachReportColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
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
                                sessionType TEXT NOT NULL DEFAULT 'MATCH',
                                drillType TEXT DEFAULT NULL,
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
                durationMillis, forehandVolleyCount, backhandVolleyCount, sessionType
            ) VALUES ('s-8to9', 'Old Session 8', 1000, 4, 0, 0, 0, 'LAB')
            """.trimIndent(),
        )

        TennisDocDatabase.MIGRATION_8_9.migrate(db)

        db.query("SELECT sessionId, sessionName, aiCoachReportJson, aiReportGeneratedAt FROM swing_sessions WHERE sessionId = 's-8to9'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s-8to9", cursor.getString(0))
            assertEquals("Old Session 8", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }

        db.close()
        openHelper.close()
    }
}
