package com.eureka.cyclelens.capture

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemporalEventAnalyzerTest {
    @Test
    fun `first and identical frames produce no candidates`() {
        val analyzer = analyzer()

        assertTrue(analyze(analyzer, frame(timestamp = 0) { _, _ -> 0 }).candidates.isEmpty())
        assertTrue(analyze(analyzer, frame(timestamp = 1) { _, _ -> 0 }).candidates.isEmpty())
    }

    @Test
    fun `adjacent and diagonal active cells merge while isolated noise is filtered`() {
        val analyzer = analyzer(minRegionCells = 2)
        analyze(analyzer, frame(timestamp = 0) { _, _ -> 0 })

        val result = analyze(analyzer, frame(timestamp = 1) { x, y ->
            when {
                x < 2 && y < 2 -> 255
                x in 2..3 && y in 2..3 -> 255
                x in 6..7 && y < 2 -> 255
                else -> 0
            }
        })

        assertEquals(1, result.candidates.size)
        assertEquals(TemporalChangePhase.START, result.events.single().phase)
    }

    @Test
    fun `separate regions are deterministically truncated by strength`() {
        val analyzer = analyzer(minRegionCells = 1, maxCandidates = 2)
        analyze(analyzer, frame(width = 12, height = 4, timestamp = 0) { _, _ -> 0 })

        val result = analyze(analyzer, frame(width = 12, height = 4, timestamp = 1) { x, y ->
            when {
                x < 2 && y < 2 -> 80
                x in 4..5 && y < 2 -> 160
                x in 8..9 && y < 2 -> 240
                else -> 0
            }
        })

        assertEquals(2, result.candidates.size)
        assertTrue(result.candidates[0].strength > result.candidates[1].strength)
        assertTrue(result.candidates.none { it.bounds.left == 0f })
    }

    @Test
    fun `global brightness shift is suppressed`() {
        val analyzer = analyzer(minRegionCells = 1)
        analyze(analyzer, frame(timestamp = 0) { _, _ -> 10 })

        val result = analyze(analyzer, frame(timestamp = 1) { _, _ -> 100 })

        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `candidate bounds include arena offset in portrait and landscape`() {
        val portrait = CaptureGeometry(100, 200, 100, 200)
        val portraitArena = PixelRect(10, 20, 18, 24)
        val portraitAnalyzer = analyzer(minRegionCells = 1)
        analyze(portraitAnalyzer, frame(portrait, portraitArena, 0) { _, _ -> 0 })
        val portraitCandidate = analyze(portraitAnalyzer,
            frame(portrait, portraitArena, 1) { x, y -> if (x < 2 && y < 2) 255 else 0 },
        ).candidates.single()
        assertEquals(0.10f, portraitCandidate.bounds.left, 0.0001f)
        assertEquals(0.10f, portraitCandidate.bounds.top, 0.0001f)
        assertEquals(0.12f, portraitCandidate.bounds.right, 0.0001f)
        assertEquals(0.11f, portraitCandidate.bounds.bottom, 0.0001f)

        val landscape = CaptureGeometry(200, 100, 200, 100)
        val landscapeArena = PixelRect(20, 10, 28, 14)
        val landscapeAnalyzer = analyzer(minRegionCells = 1)
        analyze(landscapeAnalyzer, frame(landscape, landscapeArena, 0) { _, _ -> 0 })
        val landscapeCandidate = analyze(landscapeAnalyzer,
            frame(landscape, landscapeArena, 1) { x, y -> if (x < 2 && y < 2) 255 else 0 },
        ).candidates.single()
        assertEquals(0.10f, landscapeCandidate.bounds.left, 0.0001f)
        assertEquals(0.10f, landscapeCandidate.bounds.top, 0.0001f)
    }

    @Test
    fun `reset and timestamp regression establish a fresh baseline`() {
        val analyzer = analyzer(minRegionCells = 1)
        analyze(analyzer, frame(timestamp = 10) { _, _ -> 0 })
        assertTrue(
            analyze(analyzer, frame(timestamp = 11) { x, _ -> if (x < 4) 255 else 0 })
                .candidates.isNotEmpty(),
        )
        analyzer.reset()
        assertTrue(analyze(analyzer, frame(timestamp = 12) { _, _ -> 0 }).candidates.isEmpty())
        assertTrue(analyze(analyzer, frame(timestamp = 5) { _, _ -> 255 }).candidates.isEmpty())
    }

    @Test
    fun `workspace is reused until dimensions change`() {
        val analyzer = analyzer()
        analyze(analyzer, frame(timestamp = 0) { _, _ -> 0 })
        val firstGeneration = analyzer.workspaceGeneration
        analyze(analyzer, frame(timestamp = 1) { _, _ -> 0 })
        assertEquals(firstGeneration, analyzer.workspaceGeneration)
        analyze(analyzer, frame(width = 12, height = 4, timestamp = 2) { _, _ -> 0 })
        assertEquals(firstGeneration + 1, analyzer.workspaceGeneration)
    }

    @Test
    fun `source geometry change with same arena dimensions establishes fresh baseline`() {
        val analyzer = analyzer(minRegionCells = 1)
        val firstGeometry = CaptureGeometry(8, 4, 8, 4)
        val secondGeometry = CaptureGeometry(16, 8, 8, 4)
        val arena = PixelRect(0, 0, 8, 4)
        analyze(analyzer, frame(firstGeometry, arena, 0) { _, _ -> 0 })
        val generation = analyzer.workspaceGeneration

        val afterMappingChange = analyze(
            analyzer,
            frame(secondGeometry, arena, 1) { x, _ -> if (x < 4) 255 else 0 },
        )

        assertTrue(afterMappingChange.candidates.isEmpty())
        assertEquals(generation, analyzer.workspaceGeneration)
    }

    private fun analyzer(minRegionCells: Int = 2, maxCandidates: Int = 8) =
        TemporalEventAnalyzer(
            TemporalDetectionConfig(
                spatialReductionFactor = 1,
                pixelDifferenceThreshold = 20,
                gridCellSize = 2,
                activeCellChangedRatio = 0.75f,
                activeCellMeanDifference = 15f,
                globalChangeSuppressionRatio = 0.95f,
                minRegionCells = minRegionCells,
                maxCandidatesPerFrame = maxCandidates,
            ),
        )

    private fun frame(
        width: Int = 8,
        height: Int = 4,
        timestamp: Long,
        pixel: (Int, Int) -> Int,
    ): OwnedFrameBuffer {
        val geometry = CaptureGeometry(width, height, width, height)
        return frame(geometry, PixelRect(0, 0, width, height), timestamp, pixel)
    }

    private fun frame(
        geometry: CaptureGeometry,
        arena: PixelRect,
        timestamp: Long,
        pixel: (Int, Int) -> Int,
    ): OwnedFrameBuffer {
        val pool = FrameBufferPool(arena.width, arena.height, capacity = 1, bufferFactory = ByteBuffer::allocate)
        val frame = requireNotNull(pool.tryAcquire(AnalysisFrameDescriptor(timestamp, geometry, arena)))
        val buffer = frame.writablePixels()
        for (y in 0 until arena.height) {
            for (x in 0 until arena.width) {
                val value = pixel(x, y).toByte()
                buffer.put(value).put(value).put(value).put(255.toByte())
            }
        }
        frame.markQueued(timestamp)
        frame.markProcessing()
        return frame
    }

    private fun analyze(
        analyzer: TemporalEventAnalyzer,
        frame: OwnedFrameBuffer,
    ): AnalysisResult = try {
        analyzer.analyze(frame)
    } finally {
        frame.release()
    }
}
