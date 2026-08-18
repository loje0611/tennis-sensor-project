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
class Migration9To10Test {

    private val dbName = "task051-migration-9-10.db"

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(dbName)
    }

    @Test
    fun migrate9to10_addsVideoPathAndPreservesExistingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS lab_raw_records (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                sessionId TEXT NOT NULL,
                                drillType TEXT NOT NULL,
                                timestampMillis INTEGER NOT NULL,
                                imuRawJson TEXT NOT NULL,
                                visionPosesJson TEXT NOT NULL,
                                impactOffsetMs INTEGER NOT NULL DEFAULT 0
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
            INSERT INTO lab_raw_records (
                sessionId, drillType, timestampMillis, imuRawJson, visionPosesJson, impactOffsetMs
            ) VALUES ('s-9to10', 'SERVE', 2000, '[{"ax":0.2}]', '[{"landmarks":[1]}]', 8)
            """.trimIndent(),
        )

        TennisDocDatabase.MIGRATION_9_10.migrate(db)

        db.query("PRAGMA table_info(lab_raw_records)").use { cursor ->
            var foundVideoPath = false
            var videoPathNotNull = 1
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "videoPath") {
                    foundVideoPath = true
                    videoPathNotNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                }
            }
            assertTrue("videoPath column must exist after MIGRATION_9_10", foundVideoPath)
            assertEquals(0, videoPathNotNull)
        }

        db.query(
            """
            SELECT sessionId, drillType, imuRawJson, visionPosesJson, impactOffsetMs, videoPath
            FROM lab_raw_records WHERE sessionId = 's-9to10'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s-9to10", cursor.getString(0))
            assertEquals("SERVE", cursor.getString(1))
            assertEquals("""[{"ax":0.2}]""", cursor.getString(2))
            assertEquals("""[{"landmarks":[1]}]""", cursor.getString(3))
            assertEquals(8L, cursor.getLong(4))
            assertTrue(cursor.isNull(5))
        }

        db.close()
        openHelper.close()
    }
}
