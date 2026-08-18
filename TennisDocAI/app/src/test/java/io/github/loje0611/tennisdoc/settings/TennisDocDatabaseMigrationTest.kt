package io.github.loje0611.tennisdoc.settings

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import io.github.loje0611.tennisdoc.core.data.db.TennisDocDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TennisDocDatabaseMigrationTest {

    private val dbName = "task051-migration-9-10-unit.db"

    @After
    fun tearDown() {
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @Test
    fun migrate9to10_addsNullableVideoPathAndPreservesLabRawRecords() {
        val context = RuntimeEnvironment.getApplication()
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
            ) VALUES ('legacy-session', 'FOREHAND', 1234, '[{"ax":1}]', '[{"landmarks":[]}]', 15)
            """.trimIndent(),
        )

        TennisDocDatabase.MIGRATION_9_10.migrate(db)

        db.query("PRAGMA table_info(lab_raw_records)").use { cursor ->
            val names = mutableListOf<String>()
            val notNullByName = mutableMapOf<String, Int>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                names.add(name)
                notNullByName[name] = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
            }
            assertTrue("videoPath column must exist after MIGRATION_9_10", names.contains("videoPath"))
            assertEquals(0, notNullByName["videoPath"])
        }

        db.query(
            """
            SELECT sessionId, drillType, timestampMillis, imuRawJson, visionPosesJson, impactOffsetMs, videoPath
            FROM lab_raw_records WHERE sessionId = 'legacy-session'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-session", cursor.getString(0))
            assertEquals("FOREHAND", cursor.getString(1))
            assertEquals(1234L, cursor.getLong(2))
            assertEquals("""[{"ax":1}]""", cursor.getString(3))
            assertEquals("""[{"landmarks":[]}]""", cursor.getString(4))
            assertEquals(15L, cursor.getLong(5))
            assertTrue(cursor.isNull(6))
        }

        db.close()
        openHelper.close()
    }
}
