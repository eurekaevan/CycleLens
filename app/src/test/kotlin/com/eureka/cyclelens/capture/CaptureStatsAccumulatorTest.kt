package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureStatsAccumulatorTest {
    @Test
    fun `zero frames reports zero rates`() {
        val clock = TestClock()
        val accumulator = accumulator(clock)

        clock.nowNs = SECOND
        val stats = accumulator.snapshot()

        assertEquals(0L, stats.receivedFrames)
        assertEquals(0f, stats.incomingFps)
        assertEquals(0f, stats.acceptedFps)
        assertNull(stats.lastFrameTimestampNs)
    }

    @Test
    fun `frames update counter metadata and rolling rates`() {
        val clock = TestClock()
        val accumulator = accumulator(clock)

        repeat(3) { index -> accumulator.onFrame(frame(timestampNs = index.toLong())) }
        clock.nowNs = SECOND
        val first = accumulator.snapshot()

        assertEquals(3L, first.receivedFrames)
        assertEquals(3f, first.incomingFps)
        assertEquals(3f, first.acceptedFps)
        assertEquals(2L, first.lastFrameTimestampNs)
        assertEquals(1, first.planeCount)
        assertEquals(4, first.pixelStride)
        assertEquals(5_760, first.rowStride)

        repeat(2) { accumulator.onFrame(frame(timestampNs = 10L + it)) }
        clock.nowNs = 2 * SECOND
        val second = accumulator.snapshot()

        assertEquals(5L, second.receivedFrames)
        assertEquals(2f, second.incomingFps)
        assertEquals(2.5f, second.averageIncomingFps)
    }

    @Test
    fun `resize and visibility update the next snapshot without a frame`() {
        val clock = TestClock()
        val accumulator = accumulator(clock)

        accumulator.resize(CaptureProfile.BALANCED.geometryFor(3120, 1440))
        accumulator.setCapturedContentVisible(false)
        clock.nowNs = SECOND
        val stats = accumulator.snapshot()

        assertEquals(1560, stats.width)
        assertEquals(720, stats.height)
        assertEquals(false, stats.capturedContentVisible)
    }

    @Test
    fun `accepted and dropped frames have independent counters and rates`() {
        val clock = TestClock()
        val accumulator = accumulator(clock)
        repeat(12) { index ->
            accumulator.onFrame(frame(index.toLong()), accepted = index % 4 == 0)
        }
        clock.nowNs = SECOND

        val stats = accumulator.snapshot()

        assertEquals(12L, stats.receivedFrames)
        assertEquals(3L, stats.acceptedFrames)
        assertEquals(9L, stats.droppedFrames)
        assertEquals(12f, stats.incomingFps)
        assertEquals(3f, stats.acceptedFps)
    }

    private fun frame(timestampNs: Long) = FrameMetadata(
        width = 1440,
        height = 3120,
        timestampNs = timestampNs,
        planeCount = 1,
        rowStride = 5_760,
        pixelStride = 4,
    )

    private fun accumulator(clock: TestClock) = CaptureStatsAccumulator(
        initialGeometry = CaptureProfile.NATIVE.geometryFor(1440, 3120),
        profile = CaptureProfile.NATIVE,
        surfaceFrameRateHintRequested = true,
        surfaceFrameRateHintApplied = true,
        clock = clock,
    )

    private class TestClock(
        var nowNs: Long = 0L,
    ) : NanoClock {
        override fun now(): Long = nowNs
    }

    private companion object {
        const val SECOND = 1_000_000_000L
    }
}
