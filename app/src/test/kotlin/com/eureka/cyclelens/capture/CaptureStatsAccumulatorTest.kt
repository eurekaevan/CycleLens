package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureStatsAccumulatorTest {
    @Test
    fun `zero frames reports zero rates`() {
        val clock = TestClock()
        val accumulator = CaptureStatsAccumulator(CaptureSize(1440, 3120), clock)

        clock.nowNs = SECOND
        val stats = accumulator.snapshot()

        assertEquals(0L, stats.receivedFrames)
        assertEquals(0f, stats.currentFps)
        assertEquals(0f, stats.averageFps)
        assertNull(stats.lastFrameTimestampNs)
    }

    @Test
    fun `frames update counter metadata and rolling rates`() {
        val clock = TestClock()
        val accumulator = CaptureStatsAccumulator(CaptureSize(1440, 3120), clock)

        repeat(3) { index -> accumulator.onFrame(frame(timestampNs = index.toLong())) }
        clock.nowNs = SECOND
        val first = accumulator.snapshot()

        assertEquals(3L, first.receivedFrames)
        assertEquals(3f, first.currentFps)
        assertEquals(3f, first.averageFps)
        assertEquals(2L, first.lastFrameTimestampNs)
        assertEquals(1, first.planeCount)
        assertEquals(4, first.pixelStride)
        assertEquals(5_760, first.rowStride)

        repeat(2) { accumulator.onFrame(frame(timestampNs = 10L + it)) }
        clock.nowNs = 2 * SECOND
        val second = accumulator.snapshot()

        assertEquals(5L, second.receivedFrames)
        assertEquals(2f, second.currentFps)
        assertEquals(2.5f, second.averageFps)
    }

    @Test
    fun `resize and visibility update the next snapshot without a frame`() {
        val clock = TestClock()
        val accumulator = CaptureStatsAccumulator(CaptureSize(1440, 3120), clock)

        accumulator.resize(CaptureSize(1080, 2340))
        accumulator.setCapturedContentVisible(false)
        clock.nowNs = SECOND
        val stats = accumulator.snapshot()

        assertEquals(1080, stats.width)
        assertEquals(2340, stats.height)
        assertEquals(false, stats.capturedContentVisible)
    }

    private fun frame(timestampNs: Long) = FrameMetadata(
        width = 1440,
        height = 3120,
        timestampNs = timestampNs,
        planeCount = 1,
        rowStride = 5_760,
        pixelStride = 4,
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
