package com.eureka.cyclelens.capture

/**
 * Synchronously borrows a PROCESSING frame. Implementations must not retain the frame or its
 * storage after [analyze] returns.
 */
fun interface FrameAnalyzer {
    fun analyze(frame: OwnedFrameBuffer): AnalysisResult

    fun reset() = Unit
}

data class TemporalDetectionConfig(
    val spatialReductionFactor: Int = 4,
    val pixelDifferenceThreshold: Int = 24,
    val gridCellSize: Int = 8,
    val activeCellChangedRatio: Float = 0.18f,
    val activeCellMeanDifference: Float = 10f,
    val globalChangeSuppressionRatio: Float = 0.75f,
    val minRegionCells: Int = 2,
    val maxCandidatesPerFrame: Int = 8,
    val maxTrackedChanges: Int = 16,
    val trackingIouThreshold: Float = 0.20f,
    val trackExpirationNs: Long = 350_000_000L,
    val trackCooldownNs: Long = 700_000_000L,
) {
    init {
        require(spatialReductionFactor > 0)
        require(pixelDifferenceThreshold in 0..255)
        require(gridCellSize > 0)
        require(activeCellChangedRatio in 0f..1f)
        require(activeCellMeanDifference in 0f..255f)
        require(globalChangeSuppressionRatio in 0f..1f)
        require(minRegionCells > 0)
        require(maxCandidatesPerFrame > 0)
        require(maxTrackedChanges >= maxCandidatesPerFrame)
        require(trackingIouThreshold in 0f..1f)
        require(trackExpirationNs >= 0L)
        require(trackCooldownNs >= trackExpirationNs)
    }
}

data class EventCandidate(
    val bounds: NormalizedRect,
    val strength: Float,
    val changedAreaRatio: Float,
    val timestampNs: Long,
)

enum class TemporalChangePhase {
    START,
    UPDATE,
    END,
}

data class TemporalChangeEvent(
    val trackId: Long,
    val phase: TemporalChangePhase,
    val candidate: EventCandidate,
    val ageFrames: Int,
)

data class AnalyzerStageTimingsNs(
    val luma: Long = 0L,
    val difference: Long = 0L,
    val gridAggregation: Long = 0L,
    val candidateExtraction: Long = 0L,
    val temporalGrouping: Long = 0L,
    val total: Long = 0L,
)

data class AnalysisResult(
    val timestampNs: Long,
    val analysisWidth: Int,
    val analysisHeight: Int,
    val candidates: List<EventCandidate>,
    val events: List<TemporalChangeEvent>,
    val activeTrackCount: Int,
    val timings: AnalyzerStageTimingsNs,
)
