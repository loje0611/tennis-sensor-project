package io.github.loje0611.tennisdoc.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.loje0611.tennisdoc.core.data.db.TennisDocDatabase
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.UUID

/**
 * TASK-016 AC-7 보완: [SwingHistoryRepository.generateCsvString] 실경로를 Room 인메모리로 검증.
 * (기기/에뮬레이터 필요 — `connectedDebugAndroidTest`)
 */
@RunWith(AndroidJUnit4::class)
class SwingHistoryRepositoryCsvInstrumentedTest {

    private lateinit var database: TennisDocDatabase
    private lateinit var repository: SwingHistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TennisDocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SwingHistoryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun generateCsvStringSerializesHeaderAndEvent() = runTest {
        val sessionId = UUID.randomUUID().toString()
        val tsMillis = 1_700_000_000_000L
        val dao = database.swingSessionDao()
        dao.insertSession(
            SwingSessionEntity(
                sessionId = sessionId,
                sessionName = "csv-test",
                startTime = tsMillis,
                totalSwingCount = 1,
            ),
        )
        dao.insertSwingEvent(
            SwingEventEntity(
                sessionId = sessionId,
                categoryKey = "forehand topspin",
                timestampMillis = tsMillis,
                power = 85,
                spin = 90,
                timing = 78,
                fluidity = 88,
                stability = 92,
                consistency = 80,
                rawMaxAccel = 12.5f,
                rawDurationMs = 250,
                rawGyroFollow = 350.5f,
            ),
        )

        val csv = repository.generateCsvString(sessionId = sessionId)
        val lines = csv.trim().lines()
        assertEquals(SwingHistoryRepository.CSV_HEADER, lines[0])
        assertEquals(2, lines.size)
        val format = java.text.SimpleDateFormat(SwingHistoryRepository.CSV_TIMESTAMP_PATTERN, java.util.Locale.US)
        val expectedTs = format.format(Date(tsMillis))
        assertTrue(lines[1].startsWith(expectedTs))
        assertTrue(lines[1].contains("forehand topspin,85,90,78,88,92,80,12.50,250,350.5"))
    }
}
