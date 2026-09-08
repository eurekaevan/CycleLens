package com.eureka.cyclelens.capture

data class TimingStats(
    val averageMs: Float = 0f,
    val maxMs: Float = 0f,
)

data class AnalyzerTimingStats(
    val luma: TimingStats = TimingStats(),
    val difference: TimingStats = TimingStats(),
    val gridAggregation: TimingStats = TimingStats(),
    val candidateExtraction: TimingStats = TimingStats(),
    val temporalGrouping: TimingStats = TimingStats(),
    val total: TimingStats = TimingStats(),
)

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
    val artificialDelayMs: Long = 0L,
    val analyzerTimings: AnalyzerTimingStats = AnalyzerTimingStats(),
    val latestAnalysis: AnalysisResult? = null,
    val analysisResultFrames: Long = 0L,
    val candidateFrames: Long = 0L,
    val totalCandidates: Long = 0L,
    val averageCandidatesPerFrame: Float = 0f,
    val maxCandidatesPerFrame: Int = 0,
    val startEvents: Long = 0L,
    val updateEvents: Long = 0L,
    val endEvents: Long = 0L,
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
    private var artificialDelayMs = 0L
    private var latestAnalysis: AnalysisResult? = null
    private var analysisResultFrames = 0L
    private var candidateFrames = 0L
    private var totalCandidates = 0L
    private var maxCandidatesPerFrame = 0
    private var startEvents = 0L
    private var updateEvents = 0L
    private var endEvents = 0L
    private val analyzerTimings = AnalyzerTimingAccumulator()

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
    fun onProcessed(
        durationNs: Long,
        checksum: Long?,
        artificialDelayMs: Long? = null,
        result: AnalysisResult? = null,
    ) {
        processedFrames++
        val safe = durationNs.coerceAtLeast(0L)
        processingTimeNs += safe
        maxProcessingTimeNs = maxOf(maxProcessingTimeNs, safe)
        if (artificialDelayMs != null) {
            this.artificialDelayMs = artificialDelayMs.coerceAtLeast(0L)
        }
        if (checksum != null) lastDebugChecksum = checksum
        if (result != null) {
            latestAnalysis = result
            analyzerTimings.add(result.timings)
            analysisResultFrames++
            val candidateCount = result.candidates.size
            totalCandidates += candidateCount
            if (candidateCount > 0) candidateFrames++
            maxCandidatesPerFrame = maxOf(maxCandidatesPerFrame, candidateCount)
            result.events.forEach { event ->
                when (event.phase) {
                    TemporalChangePhase.START -> startEvents++
                    TemporalChangePhase.UPDATE -> updateEvents++
                    TemporalChangePhase.END -> endEvents++
                }
            }
        }
    }

    @Synchronized
    fun onProcessingError(durationNs: Long, artificialDelayMs: Long? = null) {
        processingErrors++
        onProcessed(durationNs, checksum = null, artificialDelayMs = artificialDelayMs)
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
            artificialDelayMs = artificialDelayMs,
            analyzerTimings = analyzerTimings.snapshot(),
            latestAnalysis = latestAnalysis,
            analysisResultFrames = analysisResultFrames,
            candidateFrames = candidateFrames,
            totalCandidates = totalCandidates,
            averageCandidatesPerFrame = if (analysisResultFrames == 0L) 0f else
                totalCandidates.toFloat() / analysisResultFrames,
            maxCandidatesPerFrame = maxCandidatesPerFrame,
            startEvents = startEvents,
            updateEvents = updateEvents,
            endEvents = endEvents,
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

    private class AnalyzerTimingAccumulator {
        private var samples = 0L
        private val sums = LongArray(STAGE_COUNT)
        private val maxima = LongArray(STAGE_COUNT)

        fun add(timings: AnalyzerStageTimingsNs) {
            samples++
            add(LUMA, timings.luma)
            add(DIFFERENCE, timings.difference)
            add(GRID, timings.gridAggregation)
            add(EXTRACTION, timings.candidateExtraction)
            add(TRACKING, timings.temporalGrouping)
            add(TOTAL, timings.total)
        }

        fun snapshot(): AnalyzerTimingStats = AnalyzerTimingStats(
            luma = timing(LUMA),
            difference = timing(DIFFERENCE),
            gridAggregation = timing(GRID),
            candidateExtraction = timing(EXTRACTION),
            temporalGrouping = timing(TRACKING),
            total = timing(TOTAL),
        )

        private fun timing(index: Int) = TimingStats(
            averageMs = if (samples == 0L) 0f else
                (sums[index].toDouble() / samples / NANOS_PER_MILLISECOND).toFloat(),
            maxMs = (maxima[index].toDouble() / NANOS_PER_MILLISECOND).toFloat(),
        )

        private fun add(index: Int, value: Long) {
            val safe = value.coerceAtLeast(0L)
            sums[index] += safe
            maxima[index] = maxOf(maxima[index], safe)
        }

        private companion object {
            const val LUMA = 0
            const val DIFFERENCE = 1
            const val GRID = 2
            const val EXTRACTION = 3
            const val TRACKING = 4
            const val TOTAL = 5
            const val STAGE_COUNT = 6
            const val NANOS_PER_MILLISECOND = 1_000_000.0
        }
    }
}
