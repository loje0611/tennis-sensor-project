package com.example.swingsenseai.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.swingsenseai.data.db.dao.SwingSessionDao
import com.example.swingsenseai.data.db.entity.SessionSwingCountEntity
import com.example.swingsenseai.data.db.entity.SwingEventEntity
import com.example.swingsenseai.data.db.entity.SwingSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SwingSessionDaoTest {

    private lateinit var database: SwingSenseDatabase
    private lateinit var dao: SwingSessionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SwingSenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.swingSessionDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun makeSession(id: String = UUID.randomUUID().toString(), total: Int = 10) =
        SwingSessionEntity(
            sessionId = id,
            sessionName = "Test Session",
            startTime = System.currentTimeMillis(),
            totalSwingCount = total,
        )

    @Test
    fun insertAndRetrieveSession() = runTest {
        val session = makeSession()
        dao.insertSession(session)
        val retrieved = dao.getSessionById(session.sessionId)
        assertNotNull(retrieved)
        assertEquals(session.sessionId, retrieved!!.sessionId)
        assertEquals("Test Session", retrieved.sessionName)
    }

    @Test
    fun observeSessionsReturnsDescendingOrder() = runTest {
        val older = makeSession().copy(startTime = 1000L)
        val newer = makeSession().copy(startTime = 2000L)
        dao.insertSession(older)
        dao.insertSession(newer)
        val list = dao.observeSessions().first()
        assertEquals(2, list.size)
        assertTrue(list[0].startTime >= list[1].startTime)
    }

    @Test
    fun deleteSessionCascadesBreakdownAndEvents() = runTest {
        val session = makeSession()
        dao.insertSession(session)
        dao.insertBreakdownRows(listOf(
            SessionSwingCountEntity(sessionId = session.sessionId, categoryKey = "forehand topspin", count = 5)
        ))
        dao.insertSwingEvent(SwingEventEntity(
            sessionId = session.sessionId,
            categoryKey = "forehand topspin",
            timestampMillis = System.currentTimeMillis(),
            power = 50, spin = 50, timing = 50,
            fluidity = 50, stability = 50, consistency = 50,
        ))

        dao.deleteSessionById(session.sessionId)

        assertNull(dao.getSessionById(session.sessionId))
        assertTrue(dao.getBreakdownForSession(session.sessionId).isEmpty())
        assertTrue(dao.getSwingEventsForSession(session.sessionId).isEmpty())
    }

    @Test
    fun finalizeSessionUpdatesFields() = runTest {
        val session = makeSession()
        dao.insertSession(session)

        dao.finalizeSession(
            sessionId = session.sessionId,
            endTime = 9999L,
            totalSwingCount = 42,
            durationMillis = 3600_000L,
            fhVolley = 5,
            bhVolley = 3,
        )

        val updated = dao.getSessionById(session.sessionId)!!
        assertEquals(42, updated.totalSwingCount)
        assertEquals(9999L, updated.endTime)
        assertEquals(5, updated.forehandVolleyCount)
    }

    @Test
    fun averageMetricsReturnsCorrectAverage() = runTest {
        val sid = UUID.randomUUID().toString()
        dao.insertSession(makeSession(id = sid))
        dao.insertSwingEvent(SwingEventEntity(
            sessionId = sid, categoryKey = "forehand topspin",
            timestampMillis = 1000L, power = 60, spin = 40,
            timing = 80, fluidity = 70, stability = 50, consistency = 90,
        ))
        dao.insertSwingEvent(SwingEventEntity(
            sessionId = sid, categoryKey = "forehand topspin",
            timestampMillis = 2000L, power = 80, spin = 60,
            timing = 60, fluidity = 50, stability = 70, consistency = 70,
        ))

        val avg = dao.getAverageMetrics(sid, "forehand topspin")
        assertNotNull(avg)
        assertEquals(70.0, avg!!.power, 0.1)
        assertEquals(50.0, avg.spin, 0.1)
    }

    @Test
    fun exportQueryIncludesVolleyAndSupportsOptionalRange() = runTest {
        val sid = UUID.randomUUID().toString()
        dao.insertSession(makeSession(id = sid))

        dao.insertSwingEvent(
            SwingEventEntity(
                sessionId = sid,
                categoryKey = "forehand volley",
                timestampMillis = 1_000L,
                power = 80,
                spin = 30,
                timing = 70,
                fluidity = 60,
                stability = 65,
                consistency = 68,
                rawMaxAccel = 2.2f,
                rawDurationMs = 120,
                rawGyroFollow = 110f,
            )
        )
        dao.insertSwingEvent(
            SwingEventEntity(
                sessionId = sid,
                categoryKey = "forehand topspin",
                timestampMillis = 2_000L,
                power = 75,
                spin = 72,
                timing = 62,
                fluidity = 58,
                stability = 61,
                consistency = 64,
                rawMaxAccel = 2.9f,
                rawDurationMs = 220,
                rawGyroFollow = 360f,
            )
        )

        val all = dao.getSwingEventsForExport(
            sessionId = null,
            startTimeMillis = null,
            endTimeMillis = null,
        )
        assertEquals(2, all.size)
        assertTrue(all.any { it.categoryKey == "forehand volley" })

        val ranged = dao.getSwingEventsForExport(
            sessionId = sid,
            startTimeMillis = 1_500L,
            endTimeMillis = 2_500L,
        )
        assertEquals(1, ranged.size)
        assertEquals("forehand topspin", ranged.first().categoryKey)
    }
}
