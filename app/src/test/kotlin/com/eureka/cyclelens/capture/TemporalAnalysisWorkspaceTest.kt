package com.eureka.cyclelens.capture

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TemporalAnalysisWorkspaceTest {
    @Test
    fun `integer luma conversion handles known colors and ignores alpha`() {
        val workspace = TemporalAnalysisWorkspace(reductionFactor = 1, gridCellSize = 1)
        workspace.prepare(4, 1)
        val rgba = ByteBuffer.allocateDirect(16).apply {
            put(byteArrayOf(
                255.toByte(), 0, 0, 0,
                0, 255.toByte(), 0, 17,
                0, 0, 255.toByte(), 99,
                255.toByte(), 255.toByte(), 255.toByte(), 1,
            ))
            flip()
        }

        workspace.downsample(rgba, inputWidth = 4, inputHeight = 1, rowStride = 16)

        assertContentEquals(byteArrayOf(76, 149.toByte(), 28, 255.toByte()), workspace.currentLuma)
    }

    @Test
    fun `factor four geometry uses final partial sample and reuses buffers`() {
        val workspace = TemporalAnalysisWorkspace(reductionFactor = 4, gridCellSize = 8)
        assertTrue(workspace.prepare(720, 1_186))
        val previous = workspace.previousLuma
        val current = workspace.currentLuma
        val differences = workspace.differences

        assertEquals(180, workspace.analysisWidth)
        assertEquals(297, workspace.analysisHeight)
        assertFalse(workspace.prepare(720, 1_186))
        assertSame(previous, workspace.previousLuma)
        assertSame(current, workspace.currentLuma)
        assertSame(differences, workspace.differences)
        assertEquals(1, workspace.generation)

        val small = TemporalAnalysisWorkspace(reductionFactor = 4, gridCellSize = 8)
        small.prepare(5, 5)
        val source = ByteBuffer.allocate(5 * 5 * 4)
        repeat(25) { pixel ->
            val value = if (pixel == 24) 255.toByte() else 0
            source.put(value).put(value).put(value).put(0)
        }
        source.flip()
        small.downsample(source, 5, 5, 20)
        assertEquals(2, small.analysisWidth)
        assertEquals(2, small.analysisHeight)
        assertEquals(255, small.currentLuma.last().toInt() and 0xff)
    }

    @Test
    fun `difference uses inclusive threshold and unsigned values`() {
        val workspace = TemporalAnalysisWorkspace(reductionFactor = 1, gridCellSize = 1)
        workspace.prepare(3, 1)
        workspace.previousLuma[0] = 0
        workspace.previousLuma[1] = 100
        workspace.previousLuma[2] = 255.toByte()
        workspace.currentLuma[0] = 9
        workspace.currentLuma[1] = 110
        workspace.currentLuma[2] = 235.toByte()

        val result = workspace.difference(threshold = 10)

        assertEquals(2, result.changedPixels)
        assertContentEquals(byteArrayOf(9, 10, 20), workspace.differences)
    }

    @Test
    fun `grid aggregation honors ratios edge areas and deterministic positions`() {
        val config = TemporalDetectionConfig(
            spatialReductionFactor = 1,
            pixelDifferenceThreshold = 10,
            gridCellSize = 2,
            activeCellChangedRatio = 0.5f,
            activeCellMeanDifference = 5f,
        )
        val workspace = TemporalAnalysisWorkspace(1, 2)
        workspace.prepare(3, 2)
        workspace.differences.indices.forEach { workspace.differences[it] = 0 }
        workspace.differences[0] = 10
        workspace.differences[1] = 10
        workspace.differences[2] = 10

        workspace.aggregateGrid(config, suppressGlobalChange = false)

        assertContentEquals(intArrayOf(2, 1), workspace.changedCounts)
        assertContentEquals(intArrayOf(4, 2), workspace.cellAreas)
        assertContentEquals(booleanArrayOf(true, true), workspace.activeCells)
        workspace.aggregateGrid(config, suppressGlobalChange = true)
        assertTrue(workspace.activeCells.none { it })
    }
}
