package io.github.loje0611.tennisdoc.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.loje0611.tennisdoc.core.data.db.TennisDocDatabase
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TASK-030 AC-3 / AC-5 / AC-6: 실제 Room에서 startSession → insertSwingEvent → finalizeSession.
 */
@RunWith(AndroidJUnit4::class)
class SwingHistoryRepositorySessionLifecycleTest {

    private lateinit var database: TennisDocDatabase
    private lateinit var repository: SwingHistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TennisDocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SwingHistoryRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startSessionCreatesLabForehandTopspinRow() = runTest {
        val sid = repository.startSession(
            sessionType = SessionType.LAB,
            drillType = DrillType.FOREHAND,
            startTimeMillis = 1_700_000_000_000L,
        )

        val stored = repository.getSessionDetail(sid)
        assertNotNull(stored)
        assertEquals(SessionType.LAB.name, stored!!.session.sessionType)
        assertEquals(DrillType.FOREHAND.name, stored.session.drillType)
        assertEquals(0, stored.session.totalSwingCount)
        assertEquals(null, stored.session.endTime)
    }

    @Test
    fun swingEventIsStoredUnderActiveSessionId() = runTest {
        val sid = repository.startSession(
            sessionType = SessionType.LAB,
            drillType = DrillType.FOREHAND,
        )
        repository.insertSwingEvent(
            SwingEventEntity(
                sessionId = sid,
                categoryKey = "forehand topspin",
                timestampMillis = System.currentTimeMillis(),
                power = 80,
                spin = 70,
                timing = 60,
                fluidity = 50,
                stability = 40,
                consistency = 30,
            ),
        )

        val events = repository.getSwingEventsForSession(sid)
        assertEquals(1, events.size)
        assertEquals(sid, events[0].sessionId)
        assertEquals("forehand topspin", events[0].categoryKey)
    }

    @Test
    fun finalizeSessionPersistsEndTimeCountAndTypes() = runTest {
        val sid = repository.startSession(
            sessionType = SessionType.LAB,
            drillType = DrillType.FOREHAND,
            startTimeMillis = 1_000L,
        )
        val endTime = 6_000L
        repository.finalizeSession(
            sessionId = sid,
            endTime = endTime,
            totalSwingCount = 4,
            durationMillis = 5_000L,
            fhVolley = 0,
            bhVolley = 0,
            breakdownNormalized = mapOf("forehand topspin" to 4),
        )

        val stored = repository.getSessionDetail(sid)!!.session
        assertEquals(endTime, stored.endTime)
        assertEquals(4, stored.totalSwingCount)
        assertEquals(5_000L, stored.durationMillis)
        assertEquals(SessionType.LAB.name, stored.sessionType)
        assertEquals(DrillType.FOREHAND.name, stored.drillType)
        val breakdown = repository.getSessionDetail(sid)!!.breakdown
        assertTrue(breakdown.any { it.categoryKey == "forehand topspin" && it.count == 4 })
    }

    @Test
    fun ac3_saveAiCoachReportUpdatesSessionAndLeavesMissingIdQuiet() = runTest {
        val sid = repository.startSession(
            sessionType = SessionType.LAB,
            drillType = DrillType.FOREHAND,
            startTimeMillis = 1_000L,
        )
        val before = repository.getSessionDetail(sid)!!.session
        assertEquals(null, before.aiCoachReportJson)
        assertEquals(null, before.aiReportGeneratedAt)

        val json = """{"overallSummary":"Keep the face square"}"""
        repository.saveAiCoachReport(sid, json, 3_000L)

        val after = repository.getSessionDetail(sid)!!.session
        assertEquals(json, after.aiCoachReportJson)
        assertEquals(3_000L, after.aiReportGeneratedAt)
        assertEquals(SessionType.LAB.name, after.sessionType)
        assertEquals(DrillType.FOREHAND.name, after.drillType)

        repository.saveAiCoachReport("no-such-session", "{}", 4_000L)
        assertEquals(null, repository.getSessionDetail("no-such-session"))
        assertEquals(json, repository.getSessionDetail(sid)!!.session.aiCoachReportJson)
    }
}
