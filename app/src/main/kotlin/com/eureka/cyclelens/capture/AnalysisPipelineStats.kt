package com.eureka.cyclelens.capture

data class AnalysisPipelineStats(
    val acceptedFrames: Long = 0,
    val copiedFrames: Long = 0,
    val poolMissDrops: Long = 0,
    val queueDrops: Long = 0,
    val copyFailureDrops: Long = 0,
    val processedFrames: Long = 0,
    val processingErrors: Long = 0,
    val copiedFps: Float = 0f,
    val processedFps: Float = 0f,
    val averageCopyTimeMs: Float = 0f,
    val maxCopyTimeMs: Float = 0f,
    val averageProcessingTimeMs: Float = 0f,
    val maxProcessingTimeMs: Float = 0f,
    val averageQueueLatencyMs: Float = 0f,
    val maxQueueLatencyMs: Float = 0f,
    val poolAvailable: Int = 0,
    val poolInUse: Int = 0,
    val queueSize: Int = 0,
    val poolCapacity: Int = 0,
    val bufferByteSize: Int = 0,
    val totalPoolByteSize: Long = 0,
    val lastDebugChecksum: Long? = null,
)

internal class AnalysisPipelineStatsAccumulator(
    private val clock: NanoClock = NanoClock(System::nanoTime),
) {
    private var acceptedFrames = 0L
    private var copiedFrames = 0L
    private var poolMissDrops = 0L
    private var queueDrops = 0L
    private var copyFailureDrops = 0L
    private var processedFrames = 0L
    private var processingErrors = 0L
    private var copyTimeNs = 0L
    private var maxCopyTimeNs = 0L
    private var processingTimeNs = 0L
    private var maxProcessingTimeNs = 0L
    private var queueLatencyNs = 0L
    private var maxQueueLatencyNs = 0L
    private var dequeuedFrames = 0L
    private var copiedAtLastSnapshot = 0L
    private var processedAtLastSnapshot = 0L
    private var lastSnapshotNs = clock.now()
    private var lastDebugChecksum: Long? = null

    @Synchronized fun onAccepted() { acceptedFrames++ }
    @Synchronized fun onPoolMiss() { poolMissDrops++ }
    @Synchronized fun onCopyFailure() { copyFailureDrops++ }

    @Synchronized
    fun onCopied(durationNs: Long) {
        copiedFrames++
        val safe = durationNs.coerceAtLeast(0L)
        copyTimeNs += safe
        maxCopyTimeNs = maxOf(maxCopyTimeNs, safe)
    }

    @Synchronized
    fun onQueueDrop() {
        queueDrops++
    }

    @Synchronized
    fun onDequeued(latencyNs: Long) {
        dequeuedFrames++
        val safe = latencyNs.coerceAtLeast(0L)
        queueLatencyNs += safe
        maxQueueLatencyNs = maxOf(maxQueueLatencyNs, safe)
    }

    @Synchronized
    fun onProcessed(durationNs: Long, checksum: Long?) {
        processedFrames++
        val safe = durationNs.coerceAtLeast(0L)
        processingTimeNs += safe
        maxProcessingTimeNs = maxOf(maxProcessingTimeNs, safe)
        if (checksum != null) lastDebugChecksum = checksum
    }

    @Synchronized
    fun onProcessingError(durationNs: Long) {
        processingErrors++
        onProcessed(durationNs, checksum = null)
    }

    @Synchronized
    fun snapshot(
        pool: FrameBufferPool?,
        queueSize: Int,
        nowNs: Long = clock.now(),
    ): AnalysisPipelineStats {
        val durationNs = (nowNs - lastSnapshotNs).coerceAtLeast(0L)
        val copiedInWindow = copiedFrames - copiedAtLastSnapshot
        val processedInWindow = processedFrames - processedAtLastSnapshot
        copiedAtLastSnapshot = copiedFrames
        processedAtLastSnapshot = processedFrames
        lastSnapshotNs = nowNs
        return AnalysisPipelineStats(
            acceptedFrames = acceptedFrames,
            copiedFrames = copiedFrames,
            poolMissDrops = poolMissDrops,
            queueDrops = queueDrops,
            copyFailureDrops = copyFailureDrops,
            processedFrames = processedFrames,
            processingErrors = processingErrors,
            copiedFps = copiedInWindow.ratePerSecond(durationNs),
            processedFps = processedInWindow.ratePerSecond(durationNs),
            averageCopyTimeMs = copyTimeNs.averageMilliseconds(copiedFrames),
            maxCopyTimeMs = maxCopyTimeNs.milliseconds(),
            averageProcessingTimeMs = processingTimeNs.averageMilliseconds(processedFrames),
            maxProcessingTimeMs = maxProcessingTimeNs.milliseconds(),
            averageQueueLatencyMs = queueLatencyNs.averageMilliseconds(dequeuedFrames),
            maxQueueLatencyMs = maxQueueLatencyNs.milliseconds(),
            poolAvailable = pool?.availableCount ?: 0,
            poolInUse = pool?.inUseCount ?: 0,
            queueSize = queueSize,
            poolCapacity = pool?.capacity ?: 0,
            bufferByteSize = pool?.bufferByteSize ?: 0,
            totalPoolByteSize = pool?.totalByteSize ?: 0,
            lastDebugChecksum = lastDebugChecksum,
        )
    }

    private fun Long.ratePerSecond(durationNs: Long): Float = if (durationNs <= 0L) 0f else
        (toDouble() * NANOS_PER_SECOND / durationNs).toFloat()

    private fun Long.averageMilliseconds(samples: Long): Float = if (samples <= 0L) 0f else
        (toDouble() / samples / NANOS_PER_MILLISECOND).toFloat()

    private fun Long.milliseconds(): Float = (toDouble() / NANOS_PER_MILLISECOND).toFloat()

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}
