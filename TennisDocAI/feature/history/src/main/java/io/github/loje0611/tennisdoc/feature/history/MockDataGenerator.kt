package io.github.loje0611.tennisdoc.feature.history

import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

object MockDataGenerator {
    private const val MOCK_SWING_COUNT = 200

    private val CATEGORY_WEIGHTS = listOf(
        "forehand topspin" to 35,
        "backhand topspin" to 25,
        "forehand slice" to 10,
        "backhand slice" to 12,
        "forehand volley" to 9,
        "backhand volley" to 9,
    )

    private fun randomCategory(rng: Random): String {
        val total = CATEGORY_WEIGHTS.sumOf { it.second }
        var roll = rng.nextInt(total)
        for ((key, weight) in CATEGORY_WEIGHTS) {
            roll -= weight
            if (roll < 0) return key
        }
        return CATEGORY_WEIGHTS.last().first
    }

    private fun randomMetrics(category: String, rng: Random): MockMetrics {
        fun biasedRandom(mean: Int, spread: Int = 35): Int {
            val raw = (rng.nextInt(101) + rng.nextInt(101) + rng.nextInt(101)) / 3
            val biased = (raw + mean) / 2
            val noise = rng.nextInt(-spread, spread + 1)
            return (biased + noise).coerceIn(0, 100)
        }

        return when {
            category.contains("volley") -> MockMetrics(
                power = biasedRandom(mean = 40, spread = 30),
                spin = biasedRandom(mean = 30, spread = 28),
                timing = biasedRandom(mean = 72, spread = 25),
                smoothness = biasedRandom(mean = 65, spread = 28),
                stability = biasedRandom(mean = 78, spread = 25),
                consistency = biasedRandom(mean = 70, spread = 28),
            )
            category.contains("topspin") -> MockMetrics(
                power = biasedRandom(mean = 68, spread = 30),
                spin = biasedRandom(mean = 60, spread = 35),
                timing = biasedRandom(mean = 55, spread = 35),
                smoothness = biasedRandom(mean = 58, spread = 30),
                stability = biasedRandom(mean = 55, spread = 32),
                consistency = biasedRandom(mean = 62, spread = 28),
            )
            category.contains("slice") -> MockMetrics(
                power = biasedRandom(mean = 45, spread = 30),
                spin = biasedRandom(mean = 55, spread = 32),
                timing = biasedRandom(mean = 55, spread = 35),
                smoothness = biasedRandom(mean = 68, spread = 28),
                stability = biasedRandom(mean = 60, spread = 30),
                consistency = biasedRandom(mean = 65, spread = 28),
            )
            else -> MockMetrics(
                power = biasedRandom(mean = 50, spread = 35),
                spin = biasedRandom(mean = 50, spread = 35),
                timing = biasedRandom(mean = 50, spread = 35),
                smoothness = biasedRandom(mean = 50, spread = 35),
                stability = biasedRandom(mean = 50, spread = 35),
                consistency = biasedRandom(mean = 50, spread = 35),
            )
        }
    }

    private data class MockMetrics(
        val power: Int, val spin: Int, val timing: Int,
        val smoothness: Int, val stability: Int, val consistency: Int,
    )

    suspend fun generateAndInsert(repository: SwingHistoryRepository) {
        val rng = Random(System.nanoTime())

        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val yesterday = now - oneDayMs
        val oneMonthAgo = now - 30 * oneDayMs
        val sessionDayMillis = rng.nextLong(oneMonthAgo, yesterday + 1)

        val cal = Calendar.getInstance().apply {
            timeInMillis = sessionDayMillis
            set(Calendar.HOUR_OF_DAY, rng.nextInt(9, 19))
            set(Calendar.MINUTE, rng.nextInt(0, 60))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis
        val durationMs = rng.nextLong(20 * 60 * 1000L, 90 * 60 * 1000L)
        val endTime = startTime + durationMs

        val swings = (1..MOCK_SWING_COUNT).map { randomCategory(rng) }
        val breakdownMap = swings.groupingBy { it }.eachCount()
        val fhVolley = breakdownMap["forehand volley"] ?: 0
        val bhVolley = breakdownMap["backhand volley"] ?: 0

        val sessionId = UUID.randomUUID().toString()
        val session = SwingSessionEntity(
            sessionId = sessionId,
            sessionName = SwingSessionEntity.formatSessionName(startTime),
            startTime = startTime,
            endTime = endTime,
            totalSwingCount = 10,
            durationMillis = durationMs,
            forehandVolleyCount = fhVolley,
            backhandVolleyCount = bhVolley,
            sessionType = "LAB",
            drillType = "FOREHAND"
        )

        val intervalMs = durationMs / 10
        val events = swings.take(10).mapIndexed { index, category ->
            val m = randomMetrics(category, rng)
            val isVolley = category.contains("volley")
            SwingEventEntity(
                sessionId = sessionId,
                categoryKey = category,
                timestampMillis = startTime + intervalMs * index +
                    rng.nextLong(0, intervalMs.coerceAtLeast(1)),
                power = m.power,
                spin = m.spin,
                timing = m.timing,
                fluidity = m.smoothness,
                stability = m.stability,
                consistency = m.consistency,
                rawMaxAccel = if (isVolley) 2.5f + rng.nextFloat() * 1.5f
                              else 1.8f + rng.nextFloat() * 1.2f,
                rawDurationMs = if (isVolley) 60 + rng.nextInt(120)
                                else 160 + rng.nextInt(200),
                rawGyroFollow = if (isVolley) 80f + rng.nextFloat() * 120f
                                else 300f + rng.nextFloat() * 400f,
            )
        }

        repository.insertMockSession(session, breakdownMap, events)

        // FR-4: Insert 10 LabRawRecordEntity items for Lab Replay & SessionDetail
        for (i in 0 until 10) {
            val swingTime = startTime + intervalMs * i + 1000L
            val imuSamplesJson = buildString {
                append("[")
                for (s in 0 until 50) {
                    val ts = swingTime + s * 20L
                    val ax = String.format(java.util.Locale.US, "%.2f", rng.nextFloat() * 2f - 1f)
                    val ay = String.format(java.util.Locale.US, "%.2f", rng.nextFloat() * 2f - 1f)
                    val az = String.format(java.util.Locale.US, "%.2f", 1f + rng.nextFloat() * 2f)
                    val gx = String.format(java.util.Locale.US, "%.1f", rng.nextFloat() * 200f - 100f)
                    val gy = String.format(java.util.Locale.US, "%.1f", rng.nextFloat() * 500f + 200f)
                    val gz = String.format(java.util.Locale.US, "%.1f", rng.nextFloat() * 200f - 100f)
                    append("""{"ts":$ts,"ax":$ax,"ay":$ay,"az":$az,"gx":$gx,"gy":$gy,"gz":$gz}""")
                    if (s < 49) append(",")
                }
                append("]")
            }

            val visionPosesJson = buildString {
                append("[")
                for (f in 0 until 30) {
                    append("""{"landmarks":[""")
                    for (lm in 0 until 33) {
                        val x = String.format(java.util.Locale.US, "%.3f", 0.5f + (rng.nextFloat() - 0.5f) * 0.2f)
                        val y = String.format(java.util.Locale.US, "%.3f", 0.5f + (rng.nextFloat() - 0.5f) * 0.2f)
                        val z = String.format(java.util.Locale.US, "%.3f", (rng.nextFloat() - 0.5f) * 0.1f)
                        append("""{"x":$x,"y":$y,"z":$z,"v":0.95}""")
                        if (lm < 32) append(",")
                    }
                    append("]}")
                    if (f < 29) append(",")
                }
                append("]")
            }

            val rawRecord = io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity(
                sessionId = sessionId,
                drillType = "FOREHAND",
                timestampMillis = swingTime,
                imuRawJson = imuSamplesJson,
                visionPosesJson = visionPosesJson,
                impactOffsetMs = 500L
            )
            repository.insertLabRawRecord(rawRecord)
        }
    }
}
