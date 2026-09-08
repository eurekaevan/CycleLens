package com.eureka.cyclelens.capture

import kotlin.math.min

internal class TemporalEventAnalyzer(
    private val config: TemporalDetectionConfig = TemporalDetectionConfig(),
    private val clock: NanoClock = NanoClock(System::nanoTime),
) : FrameAnalyzer {
    private val workspace = TemporalAnalysisWorkspace(
        reductionFactor = config.spatialReductionFactor,
        gridCellSize = config.gridCellSize,
    )
    private val tracker = TemporalChangeTracker(config)
    private var hasPreviousFrame = false
    private var lastTimestampNs: Long? = null
    private var lastGeometry: CaptureGeometry? = null
    private var lastArenaRect: PixelRect? = null

    internal val workspaceGeneration: Int get() = workspace.generation

    override fun analyze(frame: OwnedFrameBuffer): AnalysisResult {
        check(frame.ownership == FrameBufferOwnership.PROCESSING) {
            "Analyzer may read frames only while PROCESSING"
        }
        val totalStarted = clock.now()
        val timestampRegression = lastTimestampNs?.let { frame.descriptor.timestampNs < it } == true
        val mappingChanged = lastGeometry?.let {
            it != frame.descriptor.geometry || lastArenaRect != frame.descriptor.arenaRect
        } == true
        val resized = workspace.prepare(frame.width, frame.height)
        if (timestampRegression || mappingChanged || resized) resetHistoryOnly()

        var stageStarted = clock.now()
        workspace.downsample(frame.readOnlyPixels(), frame.width, frame.height, frame.rowStride)
        val lumaNs = durationSince(stageStarted)

        if (!hasPreviousFrame) {
            workspace.promoteCurrentToPrevious()
            hasPreviousFrame = true
            lastTimestampNs = frame.descriptor.timestampNs
            rememberMapping(frame)
            return AnalysisResult(
                timestampNs = frame.descriptor.timestampNs,
                analysisWidth = workspace.analysisWidth,
                analysisHeight = workspace.analysisHeight,
                candidates = emptyList(),
                events = emptyList(),
                activeTrackCount = 0,
                timings = AnalyzerStageTimingsNs(
                    luma = lumaNs,
                    total = durationSince(totalStarted),
                ),
            )
        }

        stageStarted = clock.now()
        val difference = workspace.difference(config.pixelDifferenceThreshold)
        val differenceNs = durationSince(stageStarted)

        stageStarted = clock.now()
        val globalRatio = difference.changedPixels.toFloat() / difference.totalPixels
        workspace.aggregateGrid(
            config,
            suppressGlobalChange = globalRatio >= config.globalChangeSuppressionRatio,
        )
        val gridNs = durationSince(stageStarted)

        stageStarted = clock.now()
        val candidates = extractCandidates(frame)
        val extractionNs = durationSince(stageStarted)

        stageStarted = clock.now()
        val tracking = tracker.update(candidates, frame.descriptor.timestampNs)
        val trackingNs = durationSince(stageStarted)

        workspace.promoteCurrentToPrevious()
        lastTimestampNs = frame.descriptor.timestampNs
        rememberMapping(frame)
        return AnalysisResult(
            timestampNs = frame.descriptor.timestampNs,
            analysisWidth = workspace.analysisWidth,
            analysisHeight = workspace.analysisHeight,
            candidates = candidates,
            events = tracking.events,
            activeTrackCount = tracking.activeTrackCount,
            timings = AnalyzerStageTimingsNs(
                luma = lumaNs,
                difference = differenceNs,
                gridAggregation = gridNs,
                candidateExtraction = extractionNs,
                temporalGrouping = trackingNs,
                total = durationSince(totalStarted),
            ),
        )
    }

    override fun reset() {
        resetHistoryOnly()
        lastTimestampNs = null
        lastGeometry = null
        lastArenaRect = null
    }

    private fun resetHistoryOnly() {
        hasPreviousFrame = false
        tracker.reset()
        workspace.clearActivity()
    }

    private fun rememberMapping(frame: OwnedFrameBuffer) {
        lastGeometry = frame.descriptor.geometry
        lastArenaRect = frame.descriptor.arenaRect
    }

    /** Uses 8-neighbor connectivity so diagonally touching active cells form one region. */
    private fun extractCandidates(frame: OwnedFrameBuffer): List<EventCandidate> {
        workspace.visitedCells.fill(false)
        val candidates = ArrayList<EventCandidate>(config.maxCandidatesPerFrame + 1)
        for (start in workspace.activeCells.indices) {
            if (!workspace.activeCells[start] || workspace.visitedCells[start]) continue
            var head = 0
            var tail = 0
            workspace.traversalQueue[tail++] = start
            workspace.visitedCells[start] = true
            var cells = 0
            var minCellX = Int.MAX_VALUE
            var minCellY = Int.MAX_VALUE
            var maxCellX = Int.MIN_VALUE
            var maxCellY = Int.MIN_VALUE
            var changedPixels = 0
            var differenceSum = 0L
            var regionPixels = 0
            while (head < tail) {
                val cell = workspace.traversalQueue[head++]
                val cellX = cell % workspace.gridWidth
                val cellY = cell / workspace.gridWidth
                cells++
                minCellX = minOf(minCellX, cellX)
                minCellY = minOf(minCellY, cellY)
                maxCellX = maxOf(maxCellX, cellX)
                maxCellY = maxOf(maxCellY, cellY)
                changedPixels += workspace.changedCounts[cell]
                differenceSum += workspace.differenceSums[cell]
                regionPixels += workspace.cellAreas[cell]
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val neighborX = cellX + dx
                        val neighborY = cellY + dy
                        if (neighborX !in 0 until workspace.gridWidth ||
                            neighborY !in 0 until workspace.gridHeight
                        ) continue
                        val neighbor = neighborY * workspace.gridWidth + neighborX
                        if (workspace.activeCells[neighbor] && !workspace.visitedCells[neighbor]) {
                            workspace.visitedCells[neighbor] = true
                            workspace.traversalQueue[tail++] = neighbor
                        }
                    }
                }
            }
            if (cells < config.minRegionCells || changedPixels == 0) continue
            val changedRatio = changedPixels.toFloat() / regionPixels
            val meanChangedDifference = differenceSum.toFloat() / regionPixels
            val candidate = EventCandidate(
                bounds = normalizedBounds(frame, minCellX, minCellY, maxCellX, maxCellY),
                strength = (changedRatio * meanChangedDifference / 255f).coerceIn(0f, 1f),
                changedAreaRatio = changedRatio,
                timestampNs = frame.descriptor.timestampNs,
            )
            val insertion = candidates.binarySearch(candidate, CANDIDATE_ORDER)
                .let { if (it < 0) -it - 1 else it }
            candidates.add(insertion, candidate)
            if (candidates.size > config.maxCandidatesPerFrame) candidates.removeAt(candidates.lastIndex)
        }
        return candidates
    }

    private fun normalizedBounds(
        frame: OwnedFrameBuffer,
        minCellX: Int,
        minCellY: Int,
        maxCellX: Int,
        maxCellY: Int,
    ): NormalizedRect {
        val factor = config.spatialReductionFactor
        val gridPixels = config.gridCellSize * factor
        val arena = frame.descriptor.arenaRect
        val left = arena.left + minCellX * gridPixels
        val top = arena.top + minCellY * gridPixels
        val right = arena.left + min(frame.width, (maxCellX + 1) * gridPixels)
        val bottom = arena.top + min(frame.height, (maxCellY + 1) * gridPixels)
        val outputWidth = frame.descriptor.geometry.outputWidth.toFloat()
        val outputHeight = frame.descriptor.geometry.outputHeight.toFloat()
        return NormalizedRect(
            left = left / outputWidth,
            top = top / outputHeight,
            right = right / outputWidth,
            bottom = bottom / outputHeight,
        )
    }

    private fun durationSince(startedNs: Long): Long = (clock.now() - startedNs).coerceAtLeast(0L)

    private companion object {
        val CANDIDATE_ORDER: Comparator<EventCandidate> =
            compareByDescending<EventCandidate> { it.strength }
                .thenByDescending { it.changedAreaRatio }
                .thenBy { it.bounds.top }
                .thenBy { it.bounds.left }
                .thenBy { it.bounds.bottom }
                .thenBy { it.bounds.right }
    }
}
