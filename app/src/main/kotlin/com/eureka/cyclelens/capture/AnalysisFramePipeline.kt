package com.eureka.cyclelens.capture

import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal fun interface FrameSleeper {
    @Throws(InterruptedException::class)
    fun sleep(milliseconds: Long)
}

internal data class AnalysisPipelineShutdownState(
    val workerTerminated: Boolean,
    val queueSize: Int,
    val currentPoolInUse: Int,
)

/**
 * Owns the fixed buffer pool, capacity-one latest-frame queue, and only analysis consumer.
 * Producer methods never wait for the consumer and never allocate fallback pixel storage.
 */
internal class AnalysisFramePipeline(
    initialGeometry: CaptureGeometry,
    private val processingDelayMs: () -> Long,
    private val checksumEnabled: Boolean,
    private val clock: NanoClock = NanoClock(System::nanoTime),
    private val sleeper: FrameSleeper = FrameSleeper(Thread::sleep),
    private val analyzer: FrameAnalyzer = TemporalEventAnalyzer(clock = clock),
    private val poolCapacity: Int = FrameBufferPool.DEFAULT_CAPACITY,
    private val queueCapacity: Int = AnalysisFrameQueue.DEFAULT_CAPACITY,
) : AutoCloseable {
    private data class Generation(val pool: FrameBufferPool)

    private val queue = AnalysisFrameQueue(queueCapacity)
    private val stats = AnalysisPipelineStatsAccumulator(clock)
    private val workerScheduled = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, WORKER_NAME).apply { isDaemon = true }
    }
    @Volatile private var generation: Generation? = generationFor(initialGeometry)
    @Volatile private var closed = false
    @Volatile private var closingPool: FrameBufferPool? = null
    @Volatile var shutdownState: AnalysisPipelineShutdownState? = null
        private set
    private var nextChecksumAtNs = 0L
    private var analyzedPool: FrameBufferPool? = null

    fun submit(
        source: ByteBuffer,
        sourceLayout: RgbaSourceLayout,
        descriptor: AnalysisFrameDescriptor,
    ) {
        stats.onAccepted()
        val active = generation
        if (closed || active == null) {
            stats.onPoolMiss()
            return
        }
        val frame = active.pool.tryAcquire(descriptor)
        if (frame == null) {
            stats.onPoolMiss()
            return
        }
        val copyStartedNs = clock.now()
        val result = try {
            RgbaArenaCopier.copy(
                source = source,
                sourceLayout = sourceLayout,
                arenaRect = descriptor.arenaRect,
                destination = frame.writablePixels(),
            )
        } catch (_: RuntimeException) {
            ArenaCopyResult.Failure(ArenaCopyFailure.COPY_EXCEPTION)
        }
        val copyDurationNs = (clock.now() - copyStartedNs).coerceAtLeast(0L)
        if (result is ArenaCopyResult.Failure) {
            stats.onCopyFailure()
            frame.release()
            return
        }
        stats.onCopied(copyDurationNs)
        val offer = queue.offer(frame, clock.now())
        if (offer.droppedOldest) stats.onQueueDrop()
        if (offer.accepted) scheduleWorker()
    }

    fun rejectUncopyableAcceptedFrame() {
        stats.onAccepted()
        stats.onCopyFailure()
    }

    /** Replaces only the geometry-sized generation; an in-flight old lease releases to its old pool. */
    fun replaceGeometry(geometry: CaptureGeometry) {
        check(!closed) { "Pipeline is closed" }
        val replacement = generationFor(geometry)
        val previous = generation
        generation = replacement
        val drained = queue.clear()
        repeat(drained) { stats.onQueueDrop() }
        previous?.pool?.dispose()
    }

    fun snapshot(): AnalysisPipelineStats = stats.snapshot(
        pool = generation?.pool,
        queueSize = queue.size,
    )

    override fun close() {
        if (closed) return
        closed = true
        val previous = generation
        generation = null
        closingPool = previous?.pool
        queue.close()
        previous?.pool?.dispose()
        executor.shutdownNow()
    }

    fun awaitClosed(timeoutMs: Long): Boolean {
        require(timeoutMs >= 0L) { "Timeout must not be negative" }
        val terminated = executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)
        if (terminated) {
            val pool = closingPool
            shutdownState = AnalysisPipelineShutdownState(
                workerTerminated = true,
                queueSize = queue.size,
                currentPoolInUse = pool?.inUseCount ?: 0,
            )
            closingPool = null
        }
        return terminated
    }

    private fun scheduleWorker() {
        if (!workerScheduled.compareAndSet(false, true)) return
        try {
            executor.execute(::drainQueue)
        } catch (_: RuntimeException) {
            workerScheduled.set(false)
            val cleared = queue.clear()
            repeat(cleared) { stats.onQueueDrop() }
        }
    }

    private fun drainQueue() {
        while (true) {
            val frame = queue.poll()
            if (frame == null) {
                workerScheduled.set(false)
                if (queue.size == 0 || !workerScheduled.compareAndSet(false, true)) return
                continue
            }
            val processingStartedNs = clock.now()
            stats.onDequeued((processingStartedNs - frame.enqueuedAtNs).coerceAtLeast(0L))
            var actualProcessingNs = 0L
            var configuredDelayMs = 0L
            var analysisCompleted = false
            try {
                if (analyzedPool !== frame.pool) {
                    analyzer.reset()
                    analyzedPool = frame.pool
                }
                val result = analyzer.analyze(frame)
                val afterAnalysisNs = clock.now()
                val checksum = if (checksumEnabled && afterAnalysisNs >= nextChecksumAtNs) {
                    nextChecksumAtNs = afterAnalysisNs.saturatedPlus(CHECKSUM_INTERVAL_NS)
                    sparseChecksum(frame.readOnlyPixels())
                } else {
                    null
                }
                actualProcessingNs = (clock.now() - processingStartedNs).coerceAtLeast(0L)
                analysisCompleted = true
                configuredDelayMs = processingDelayMs().coerceAtLeast(0L)
                if (configuredDelayMs > 0L) sleeper.sleep(configuredDelayMs)
                stats.onProcessed(
                    durationNs = actualProcessingNs,
                    checksum = checksum,
                    artificialDelayMs = configuredDelayMs,
                    result = result,
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                stats.onProcessingError(
                    durationNs = if (analysisCompleted) actualProcessingNs else clock.now() - processingStartedNs,
                    artificialDelayMs = configuredDelayMs,
                )
                return
            } catch (_: RuntimeException) {
                stats.onProcessingError(
                    durationNs = if (analysisCompleted) actualProcessingNs else clock.now() - processingStartedNs,
                    artificialDelayMs = configuredDelayMs,
                )
            } finally {
                frame.release()
            }
        }
    }

    private fun generationFor(geometry: CaptureGeometry): Generation {
        val arena = ClashRoyaleCaptureLayout.arenaRegion.toPixelRect(geometry)
        return Generation(FrameBufferPool(arena.width, arena.height, poolCapacity))
    }

    private fun sparseChecksum(buffer: ByteBuffer): Long {
        var checksum = FNV_OFFSET_BASIS
        var index = 0
        while (index < buffer.limit()) {
            checksum = (checksum xor (buffer.get(index).toLong() and 0xffL)) * FNV_PRIME
            index += CHECKSUM_SAMPLE_STRIDE_BYTES
        }
        if (buffer.limit() > 0) {
            checksum = (checksum xor (buffer.get(buffer.limit() - 1).toLong() and 0xffL)) * FNV_PRIME
        }
        return checksum
    }

    private fun Long.saturatedPlus(value: Long): Long =
        if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value

    private companion object {
        const val WORKER_NAME = "CycleLensAnalysisFrames"
        const val CHECKSUM_SAMPLE_STRIDE_BYTES = 4_096
        const val CHECKSUM_INTERVAL_NS = 1_000_000_000L
        const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
    }
}
