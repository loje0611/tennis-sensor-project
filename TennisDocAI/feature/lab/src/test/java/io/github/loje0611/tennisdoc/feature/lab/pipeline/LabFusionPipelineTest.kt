package io.github.loje0611.tennisdoc.feature.lab.pipeline

import io.github.loje0611.tennisdoc.core.data.db.dao.LabRawRecordDao
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabFusionPipelineTest {

    private class FakeLabRawRecordDao : LabRawRecordDao {
        val insertedRecords = mutableListOf<LabRawRecordEntity>()

        override suspend fun insert(record: LabRawRecordEntity): Long {
            insertedRecords.add(record)
            return insertedRecords.size.toLong()
        }

        override fun getRecordsBySessionId(sessionId: String): Flow<List<LabRawRecordEntity>> {
            return flowOf(insertedRecords.filter { it.sessionId == sessionId })
        }

        override suspend fun getRecordById(id: Long): LabRawRecordEntity? {
            return insertedRecords.firstOrNull { it.id == id }
        }

        override suspend fun deleteRecordsBySessionId(sessionId: String): Int {
            val initialSize = insertedRecords.size
            insertedRecords.removeAll { it.sessionId == sessionId }
            return initialSize - insertedRecords.size
        }
    }

    private fun generateMockPose(yVal: Float): PoseFrame {
        val landmarks = (0..32).map { i ->
            if (i == 16) PoseLandmark(0.5f, yVal, 0.2f, 1f)
            else PoseLandmark(0.5f, 0.5f, 0f, 1f)
        }
        return PoseFrame(landmarks = landmarks)
    }

    private fun generateMockImu(timestampMs: Long, gyroZ: Float, accelX: Float): ImuDataPoint {
        return ImuDataPoint(
            timestampMs = timestampMs,
            accelX = accelX,
            accelY = 0f,
            accelZ = 0f,
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = gyroZ
        )
    }

    @Test
    fun `AC-3 AC-4 AC-5 onSwingTriggered runs fusion and anomaly detection and persists to DAO`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeDao = FakeLabRawRecordDao()
        val pipeline = LabFusionPipelineImpl(
            labRawRecordDao = fakeDao,
            ioDispatcher = testDispatcher
        )

        // Feed mock poses
        for (i in 0..20) {
            pipeline.feedPoseFrame(generateMockPose(0.8f - i * 0.02f))
        }

        // Feed mock IMU
        for (i in 0..50) {
            val ts = i * 20L
            val gyro = if (i == 25) 1500f else 100f
            val accel = if (i == 25) 15f else 1f
            pipeline.feedImuSample(generateMockImu(ts, gyro, accel))
        }

        val swing = pipeline.onSwingTriggered(
            sessionId = "session-test-123",
            drillType = DrillType.FOREHAND_TOPSPIN
        )

        assertNotNull(swing)
        assertEquals(swing, pipeline.latestFusedSwing.value)

        // AC-4: Anomaly report is generated
        val anomalyReport = pipeline.latestAnomalyReport.value
        assertNotNull(anomalyReport)
        assertEquals(DrillType.FOREHAND_TOPSPIN, anomalyReport!!.drillType)

        // Advance dispatcher for DB insert
        testScheduler.advanceUntilIdle()

        // AC-5: DAO record is inserted
        assertEquals(1, fakeDao.insertedRecords.size)
        val record = fakeDao.insertedRecords.first()
        assertEquals("session-test-123", record.sessionId)
        assertEquals("FOREHAND_TOPSPIN", record.drillType)
        assertTrue(record.imuRawJson.contains("gx"))
        assertTrue(record.visionPosesJson.contains("landmarks"))
        assertNotNull(pipeline.currentBaseline.value)
        assertEquals(1, pipeline.currentBaseline.value!!.totalSwings)
    }

    @Test
    fun `onSwingTriggered with empty buffer returns null and does not persist`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeDao = FakeLabRawRecordDao()
        val pipeline = LabFusionPipelineImpl(
            labRawRecordDao = fakeDao,
            ioDispatcher = testDispatcher,
        )

        val swing = pipeline.onSwingTriggered("session-empty", DrillType.SERVE)
        testScheduler.advanceUntilIdle()

        assertNull(swing)
        assertNull(pipeline.latestFusedSwing.value)
        assertTrue(fakeDao.insertedRecords.isEmpty())
    }

    @Test
    fun `reset clears fused swing and anomaly report`() = runTest {
        val pipeline = LabFusionPipelineImpl()
        pipeline.feedImuSample(generateMockImu(0L, 100f, 1f))
        pipeline.onSwingTriggered("session-reset", DrillType.VOLLEY)
        assertNotNull(pipeline.latestFusedSwing.value)

        pipeline.reset()
        assertNull(pipeline.latestFusedSwing.value)
        assertNull(pipeline.latestAnomalyReport.value)
    }
}
