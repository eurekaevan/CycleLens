package com.eureka.cyclelens.capture

import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnalysisFramePipelineTest {
    @Test
    fun `successful copy is consumed and released`() {
        val pipeline = pipeline(delayMs = 0)

        pipeline.submit(source(), layout(), descriptor(timestamp = 1))

        val stats = pipeline.awaitStats { it.processedFrames == 1L }
        assertEquals(1, stats.acceptedFrames)
        assertEquals(1, stats.copiedFrames)
        assertEquals(1, stats.processedFrames)
        assertEquals(0, stats.poolMissDrops)
        assertEquals(0, stats.queueDrops)
        assertEquals(3, stats.poolAvailable)
        assertNotNull(stats.lastDebugChecksum)
        pipeline.close()
        assertTrue(pipeline.awaitClosed(500))
    }

    @Test
    fun `copy failure releases writing lease and records structured drop`() {
        val pipeline = pipeline(delayMs = 0)

        pipeline.submit(ByteBuffer.allocate(10), layout(), descriptor(timestamp = 1))

        val stats = pipeline.snapshot()
        assertEquals(1, stats.acceptedFrames)
        assertEquals(0, stats.copiedFrames)
        assertEquals(1, stats.copyFailureDrops)
        assertEquals(3, stats.poolAvailable)
        assertEquals(0, stats.queueSize)
        pipeline.close()
    }

    @Test
    fun `capacity one pressure drops stale queued frame without backlog`() {
        val processingStarted = CountDownLatch(1)
        val releaseProcessing = CountDownLatch(1)
        val pipeline = AnalysisFramePipeline(
            initialGeometry = geometry(),
            processingDelayMs = { 100 },
            checksumEnabled = false,
            sleeper = FrameSleeper {
                processingStarted.countDown()
                releaseProcessing.await()
            },
        )
        pipeline.submit(source(), layout(), descriptor(timestamp = 1))
        assertTrue(processingStarted.await(1, TimeUnit.SECONDS))

        pipeline.submit(source(), layout(), descriptor(timestamp = 2))
        pipeline.submit(source(), layout(), descriptor(timestamp = 3))

        val underPressure = pipeline.snapshot()
        assertEquals(1, underPressure.queueDrops)
        assertEquals(1, underPressure.queueSize)
        assertEquals(2, underPressure.poolInUse)
        releaseProcessing.countDown()
        val finished = pipeline.awaitStats { it.processedFrames == 2L }
        assertEquals(2, finished.processedFrames)
        assertEquals(3, finished.poolAvailable)
        pipeline.close()
    }

    @Test
    fun `worker exception releases processing lease`() {
        val pipeline = AnalysisFramePipeline(
            initialGeometry = geometry(),
            processingDelayMs = { 20 },
            checksumEnabled = false,
            sleeper = FrameSleeper { error("synthetic processor failure") },
        )

        pipeline.submit(source(), layout(), descriptor(timestamp = 1))

        val stats = pipeline.awaitStats { it.processingErrors == 1L }
        assertEquals(1, stats.processedFrames)
        assertEquals(3, stats.poolAvailable)
        pipeline.close()
    }

    @Test
    fun `resize drains old queue and creates correctly sized generation`() {
        val processingStarted = CountDownLatch(1)
        val releaseProcessing = CountDownLatch(1)
        val pipeline = AnalysisFramePipeline(
            initialGeometry = geometry(),
            processingDelayMs = { 100 },
            checksumEnabled = false,
            sleeper = FrameSleeper {
                processingStarted.countDown()
                releaseProcessing.await()
            },
        )
        pipeline.submit(source(), layout(), descriptor(timestamp = 1))
        assertTrue(processingStarted.await(1, TimeUnit.SECONDS))
        pipeline.submit(source(), layout(), descriptor(timestamp = 2))

        val landscape = CaptureGeometry(200, 100, 200, 100)
        pipeline.replaceGeometry(landscape)

        val resized = pipeline.snapshot()
        val arena = ClashRoyaleCaptureLayout.arenaRegion.toPixelRect(landscape)
        assertEquals(0, resized.queueSize)
        assertEquals(1, resized.queueDrops)
        assertEquals(arena.width * arena.height * 4, resized.bufferByteSize)
        releaseProcessing.countDown()
        pipeline.close()
        assertTrue(pipeline.awaitClosed(500))
    }

    @Test
    fun `debug delays 0 20 and 100 are applied only on worker`() {
        listOf(0L, 20L, 100L).forEach { delay ->
            val observed = AtomicLong(-1)
            val pipeline = AnalysisFramePipeline(
                initialGeometry = geometry(),
                processingDelayMs = { delay },
                checksumEnabled = false,
                sleeper = FrameSleeper { observed.set(it) },
            )
            pipeline.submit(source(), layout(), descriptor(timestamp = delay))
            pipeline.awaitStats { it.processedFrames == 1L }
            assertEquals(if (delay == 0L) -1L else delay, observed.get())
            pipeline.close()
        }
    }

    @Test
    fun `stop interrupts worker drains queue and terminates`() {
        val started = CountDownLatch(1)
        val pipeline = AnalysisFramePipeline(
            initialGeometry = geometry(),
            processingDelayMs = { 100 },
            checksumEnabled = false,
            sleeper = FrameSleeper {
                started.countDown()
                CountDownLatch(1).await()
            },
        )
        pipeline.submit(source(), layout(), descriptor(timestamp = 1))
        assertTrue(started.await(1, TimeUnit.SECONDS))
        pipeline.submit(source(), layout(), descriptor(timestamp = 2))

        pipeline.close()

        assertTrue(pipeline.awaitClosed(500))
        assertEquals(0, pipeline.snapshot().queueSize)
        assertEquals(
            AnalysisPipelineShutdownState(
                workerTerminated = true,
                queueSize = 0,
                currentPoolInUse = 0,
            ),
            pipeline.shutdownState,
        )
    }

    private fun pipeline(delayMs: Long) = AnalysisFramePipeline(
        initialGeometry = geometry(),
        processingDelayMs = { delayMs },
        checksumEnabled = true,
    )

    private fun geometry() = CaptureGeometry(100, 100, 100, 100)

    private fun descriptor(timestamp: Long): AnalysisFrameDescriptor {
        val geometry = geometry()
        return AnalysisFrameDescriptor(
            timestamp,
            geometry,
            ClashRoyaleCaptureLayout.arenaRegion.toPixelRect(geometry),
        )
    }

    private fun layout() = RgbaSourceLayout(100, 100, 4, 400)

    private fun source() = ByteBuffer.allocate(40_000).apply {
        repeat(capacity()) { put((it and 0xff).toByte()) }
        flip()
    }

    private fun AnalysisFramePipeline.awaitStats(
        predicate: (AnalysisPipelineStats) -> Boolean,
    ): AnalysisPipelineStats {
        repeat(100) {
            val current = snapshot()
            if (predicate(current)) return current
            Thread.sleep(5)
        }
        error("Pipeline did not reach expected state: ${snapshot()}")
    }
}
