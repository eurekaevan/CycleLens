package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals

class AnalysisPipelineStatsAccumulatorTest {
    @Test
    fun `counters rates averages maxima and defensive negative timings are independent`() {
        val clock = MutableClock()
        val stats = AnalysisPipelineStatsAccumulator(clock)
        repeat(3) { stats.onAccepted() }
        stats.onCopied(2_000_000)
        stats.onCopied(4_000_000)
        stats.onPoolMiss()
        stats.onQueueDrop()
        stats.onCopyFailure()
        stats.onDequeued(-1)
        stats.onDequeued(6_000_000)
        stats.onProcessed(8_000_000, 123)
        stats.onProcessingError(12_000_000)
        clock.nowNs = SECOND

        val snapshot = stats.snapshot(pool = null, queueSize = 0)

        assertEquals(3, snapshot.acceptedFrames)
        assertEquals(2, snapshot.copiedFrames)
        assertEquals(1, snapshot.poolMissDrops)
        assertEquals(1, snapshot.queueDrops)
        assertEquals(1, snapshot.copyFailureDrops)
        assertEquals(2, snapshot.processedFrames)
        assertEquals(1, snapshot.processingErrors)
        assertEquals(2f, snapshot.copiedFps)
        assertEquals(2f, snapshot.processedFps)
        assertEquals(3f, snapshot.averageCopyTimeMs)
        assertEquals(4f, snapshot.maxCopyTimeMs)
        assertEquals(10f, snapshot.averageProcessingTimeMs)
        assertEquals(12f, snapshot.maxProcessingTimeMs)
        assertEquals(3f, snapshot.averageQueueLatencyMs)
        assertEquals(6f, snapshot.maxQueueLatencyMs)
        assertEquals(123, snapshot.lastDebugChecksum)
    }

    @Test
    fun `empty and regressing snapshot windows report zero rates`() {
        val clock = MutableClock(SECOND)
        val stats = AnalysisPipelineStatsAccumulator(clock)
        clock.nowNs = 0

        val snapshot = stats.snapshot(pool = null, queueSize = 0)

        assertEquals(0f, snapshot.copiedFps)
        assertEquals(0f, snapshot.processedFps)
        assertEquals(0f, snapshot.averageCopyTimeMs)
        assertEquals(0f, snapshot.averageQueueLatencyMs)
    }

    private class MutableClock(var nowNs: Long = 0) : NanoClock {
        override fun now(): Long = nowNs
    }

    private companion object {
        const val SECOND = 1_000_000_000L
    }
}
