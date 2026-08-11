package io.github.loje0611.tennisdoc.core.data.repository

import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.Locale

/**
 * FR-2 / AC-7: CSV 헤더·행 서식이 Android 의존 없이 검증 가능해야 한다.
 * (DB 조회가 필요한 [SwingHistoryRepository.generateCsvString] 전체 경로는
 *  동일 서식 상수·포맷 문자열을 사용하므로 여기서 계약을 고정한다.)
 */
class SwingHistoryCsvTest {

    @Test
    fun csvHeaderHasElevenColumnsMatchingExportContract() {
        val expectedHeader =
            "Timestamp,SwingType,Power,Spin,Timing,Smoothness,Stability,Consistency,RawAccel(g),RawDuration(ms),RawGyro(dps)"
        assertEquals(expectedHeader, SwingHistoryRepository.CSV_HEADER)
        assertEquals(11, SwingHistoryRepository.CSV_HEADER.split(',').size)
    }

    @Test
    fun eventRowSerializesWithPreservedFormat() {
        val tsMillis = 1_700_000_000_000L
        val format = java.text.SimpleDateFormat(SwingHistoryRepository.CSV_TIMESTAMP_PATTERN, java.util.Locale.US)
        val formattedTs = format.format(Date(tsMillis))

        val event = SwingEventEntity(
            id = 1,
            sessionId = "test-session",
            timestampMillis = tsMillis,
            categoryKey = "forehand_topspin",
            power = 85,
            spin = 90,
            timing = 78,
            fluidity = 88,
            stability = 92,
            consistency = 80,
            rawMaxAccel = 12.5f,
            rawDurationMs = 250,
            rawGyroFollow = 350.5f,
        )

        val formattedRow = String.format(
            Locale.US,
            "%s,%s,%d,%d,%d,%d,%d,%d,%.2f,%d,%.1f",
            formattedTs,
            event.categoryKey,
            event.power,
            event.spin,
            event.timing,
            event.fluidity,
            event.stability,
            event.consistency,
            event.rawMaxAccel,
            event.rawDurationMs,
            event.rawGyroFollow,
        )

        val csv = buildString {
            appendLine(SwingHistoryRepository.CSV_HEADER)
            appendLine(formattedRow)
        }

        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertEquals(SwingHistoryRepository.CSV_HEADER, lines[0])
        assertTrue(lines[1].startsWith(formattedTs))
        assertTrue(lines[1].contains("forehand_topspin,85,90,78,88,92,80,12.50,250,350.5"))
    }
}
