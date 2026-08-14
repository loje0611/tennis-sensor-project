package io.github.loje0611.tennisdoc.feature.lab.pipeline

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

class LabFusionStreamBuffer(
    private val bufferDurationMs: Long = 3000L
) {
    private data class TimedPose(val timestampMs: Long, val frame: PoseFrame)

    private val poseLock = Any()
    private val imuLock = Any()

    private val poseQueue = ArrayDeque<TimedPose>()
    private val imuQueue = ArrayDeque<ImuDataPoint>()

    fun addPoseFrame(frame: PoseFrame) {
        val now = System.currentTimeMillis()
        synchronized(poseLock) {
            poseQueue.addLast(TimedPose(now, frame))
            prunePoses(now)
        }
    }

    fun addImuSample(sample: ImuDataPoint) {
        synchronized(imuLock) {
            imuQueue.addLast(sample)
            pruneImu(sample.timestampMs)
        }
    }

    fun snapshot(): Pair<List<PoseFrame>, List<ImuDataPoint>> {
        val poses = synchronized(poseLock) {
            poseQueue.map { it.frame }
        }
        val imu = synchronized(imuLock) {
            imuQueue.toList()
        }
        return Pair(poses, imu)
    }

    fun clear() {
        synchronized(poseLock) {
            poseQueue.clear()
        }
        synchronized(imuLock) {
            imuQueue.clear()
        }
    }

    private fun prunePoses(currentTimeMs: Long) {
        val threshold = currentTimeMs - bufferDurationMs
        while (poseQueue.isNotEmpty() && poseQueue.first().timestampMs < threshold) {
            poseQueue.removeFirst()
        }
    }

    private fun pruneImu(latestSampleTs: Long) {
        val threshold = latestSampleTs - bufferDurationMs
        while (imuQueue.isNotEmpty() && imuQueue.first().timestampMs < threshold) {
            imuQueue.removeFirst()
        }
    }
}
