package io.github.loje0611.tennisdoc.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import io.github.loje0611.tennisdoc.core.data.db.dao.SwingSessionDao
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LabRawRecordDaoTest {

    private lateinit var database: TennisDocDatabase
    private lateinit var sessionDao: SwingSessionDao
    private lateinit var labDao: LabRawRecordDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TennisDocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = database.swingSessionDao()
        labDao = database.labRawRecordDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun databaseVersionIs9() {
        assertEquals(9, database.openHelper.writableDatabase.version)
    }

    @Test
    fun insertAndGetByIdAndSession() = runTest {
        val sessionId = UUID.randomUUID().toString()
        sessionDao.insertSession(
            SwingSessionEntity(
                sessionId = sessionId,
                sessionName = "Lab drill",
                startTime = 10L,
                sessionType = SessionType.LAB.name,
                drillType = DrillType.FOREHAND.name,
            ),
        )

        val rowId = labDao.insert(
            LabRawRecordEntity(
                sessionId = sessionId,
                drillType = DrillType.FOREHAND.name,
                timestampMillis = 20L,
                imuRawJson = """[{"ax":0.1}]""",
                visionPosesJson = """[{"landmarks":[]}]""",
                impactOffsetMs = 15L,
            ),
        )
        assertTrue(rowId > 0L)

        val loaded = labDao.getRecordById(rowId)
        assertNotNull(loaded)
        assertEquals(sessionId, loaded!!.sessionId)
        assertEquals(DrillType.FOREHAND.name, loaded.drillType)
        assertEquals("""[{"ax":0.1}]""", loaded.imuRawJson)
        assertEquals(15L, loaded.impactOffsetMs)

        val bySession = labDao.getRecordsBySessionId(sessionId).first()
        assertEquals(1, bySession.size)
        assertEquals(rowId, bySession[0].id)
    }

    @Test
    fun matchSessionDefaultsSessionTypeAndNullDrillType() = runTest {
        val sessionId = UUID.randomUUID().toString()
        sessionDao.insertSession(
            SwingSessionEntity(
                sessionId = sessionId,
                sessionName = "Match",
                startTime = 1L,
            ),
        )
        val stored = sessionDao.getSessionById(sessionId)!!
        assertEquals("MATCH", stored.sessionType)
        assertNull(stored.drillType)
    }

    @Test
    fun deleteSessionCascadesLabRawRecords() = runTest {
        val sessionId = UUID.randomUUID().toString()
        sessionDao.insertSession(
            SwingSessionEntity(
                sessionId = sessionId,
                sessionName = "Lab",
                startTime = 1L,
                sessionType = SessionType.LAB.name,
                drillType = DrillType.SERVE.name,
            ),
        )
        labDao.insert(
            LabRawRecordEntity(
                sessionId = sessionId,
                drillType = DrillType.SERVE.name,
                timestampMillis = 2L,
                imuRawJson = "[]",
                visionPosesJson = "[]",
            ),
        )
        assertEquals(1, labDao.getRecordsBySessionId(sessionId).first().size)

        sessionDao.deleteSessionById(sessionId)

        assertNull(sessionDao.getSessionById(sessionId))
        assertTrue(labDao.getRecordsBySessionId(sessionId).first().isEmpty())
    }

    @Test
    fun insertWithoutParentSessionViolatesForeignKey() = runTest {
        try {
            labDao.insert(
                LabRawRecordEntity(
                    sessionId = "missing-session",
                    drillType = DrillType.FOREHAND_VOLLEY.name,
                    timestampMillis = 1L,
                    imuRawJson = "[]",
                    visionPosesJson = "[]",
                ),
            )
            fail("Expected SQLiteConstraintException for missing sessionId")
        } catch (e: SQLiteConstraintException) {
            assertTrue(e.message?.contains("FOREIGN KEY") == true || e.message != null)
        }
    }
}
