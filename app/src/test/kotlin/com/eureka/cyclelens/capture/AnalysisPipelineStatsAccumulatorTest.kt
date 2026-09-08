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
        stats.onProcessed(
            durationNs = 8_000_000,
            checksum = 123,
            artificialDelayMs = 100,
            result = resultWithTimings(),
        )
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
        assertEquals(100, snapshot.artificialDelayMs)
        assertEquals(1f, snapshot.analyzerTimings.luma.averageMs)
        assertEquals(6f, snapshot.analyzerTimings.total.maxMs)
        assertEquals(1, snapshot.latestAnalysis?.activeTrackCount)
        assertEquals(1, snapshot.analysisResultFrames)
        assertEquals(0, snapshot.candidateFrames)
        assertEquals(0f, snapshot.averageCandidatesPerFrame)
        assertEquals(0, snapshot.maxCandidatesPerFrame)
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

    @Test
    fun `candidate and temporal event counters aggregate without retaining frames`() {
        val stats = AnalysisPipelineStatsAccumulator(MutableClock())
        val candidate = EventCandidate(
            bounds = NormalizedRect(0f, 0f, 0.5f, 0.5f),
            strength = 0.5f,
            changedAreaRatio = 0.25f,
            timestampNs = 1,
        )
        val events = listOf(
            TemporalChangeEvent(1, TemporalChangePhase.START, candidate, 1),
            TemporalChangeEvent(2, TemporalChangePhase.UPDATE, candidate, 2),
            TemporalChangeEvent(3, TemporalChangePhase.END, candidate, 3),
        )
        stats.onProcessed(
            1,
            null,
            result = resultWith(candidates = listOf(candidate, candidate), events = events),
        )
        stats.onProcessed(
            1,
            null,
            result = resultWith(candidates = emptyList(), events = emptyList()),
        )

        val snapshot = stats.snapshot(pool = null, queueSize = 0)

        assertEquals(2, snapshot.analysisResultFrames)
        assertEquals(1, snapshot.candidateFrames)
        assertEquals(2, snapshot.totalCandidates)
        assertEquals(1f, snapshot.averageCandidatesPerFrame)
        assertEquals(2, snapshot.maxCandidatesPerFrame)
        assertEquals(1, snapshot.startEvents)
        assertEquals(1, snapshot.updateEvents)
        assertEquals(1, snapshot.endEvents)
    }

    private class MutableClock(var nowNs: Long = 0) : NanoClock {
        override fun now(): Long = nowNs
    }

    private fun resultWithTimings() = AnalysisResult(
        timestampNs = 1,
        analysisWidth = 10,
        analysisHeight = 20,
        candidates = emptyList(),
        events = emptyList(),
        activeTrackCount = 1,
        timings = AnalyzerStageTimingsNs(
            luma = 1_000_000,
            difference = 2_000_000,
            gridAggregation = 3_000_000,
            candidateExtraction = 4_000_000,
            temporalGrouping = 5_000_000,
            total = 6_000_000,
        ),
    )

    private fun resultWith(
        candidates: List<EventCandidate>,
        events: List<TemporalChangeEvent>,
    ) = AnalysisResult(
        timestampNs = 1,
        analysisWidth = 10,
        analysisHeight = 20,
        candidates = candidates,
        events = events,
        activeTrackCount = 0,
        timings = AnalyzerStageTimingsNs(),
    )

    private companion object {
        const val SECOND = 1_000_000_000L
    }
}
