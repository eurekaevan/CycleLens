package com.eureka.cyclelens.capture

import java.nio.ByteBuffer
import kotlin.math.abs

internal data class DifferenceSummary(
    val changedPixels: Int,
    val totalPixels: Int,
)

/** Reusable luma, difference, and coarse-grid storage owned by one analysis worker. */
internal class TemporalAnalysisWorkspace(
    private val reductionFactor: Int,
    private val gridCellSize: Int,
) {
    var analysisWidth: Int = 0
        private set
    var analysisHeight: Int = 0
        private set
    var gridWidth: Int = 0
        private set
    var gridHeight: Int = 0
        private set
    var generation: Int = 0
        private set

    var previousLuma = ByteArray(0)
        private set
    var currentLuma = ByteArray(0)
        private set
    var differences = ByteArray(0)
        private set
    var changedCounts = IntArray(0)
        private set
    var differenceSums = IntArray(0)
        private set
    var cellAreas = IntArray(0)
        private set
    var activeCells = BooleanArray(0)
        private set
    var visitedCells = BooleanArray(0)
        private set
    var traversalQueue = IntArray(0)
        private set

    fun prepare(inputWidth: Int, inputHeight: Int): Boolean {
        val nextWidth = ceilDiv(inputWidth, reductionFactor)
        val nextHeight = ceilDiv(inputHeight, reductionFactor)
        if (nextWidth == analysisWidth && nextHeight == analysisHeight) return false

        analysisWidth = nextWidth
        analysisHeight = nextHeight
        gridWidth = ceilDiv(nextWidth, gridCellSize)
        gridHeight = ceilDiv(nextHeight, gridCellSize)
        val pixels = Math.multiplyExact(nextWidth, nextHeight)
        val cells = Math.multiplyExact(gridWidth, gridHeight)
        previousLuma = ByteArray(pixels)
        currentLuma = ByteArray(pixels)
        differences = ByteArray(pixels)
        changedCounts = IntArray(cells)
        differenceSums = IntArray(cells)
        cellAreas = IntArray(cells)
        activeCells = BooleanArray(cells)
        visitedCells = BooleanArray(cells)
        traversalQueue = IntArray(cells)
        generation++
        return true
    }

    fun downsample(source: ByteBuffer, inputWidth: Int, inputHeight: Int, rowStride: Int) {
        require(rowStride >= inputWidth * OwnedFrameBuffer.BYTES_PER_PIXEL)
        require(source.limit() >= rowStride * inputHeight)
        var destinationIndex = 0
        val centerOffset = reductionFactor / 2
        for (analysisY in 0 until analysisHeight) {
            val sourceY = (analysisY * reductionFactor + centerOffset).coerceAtMost(inputHeight - 1)
            for (analysisX in 0 until analysisWidth) {
                val sourceX = (analysisX * reductionFactor + centerOffset).coerceAtMost(inputWidth - 1)
                val sourceIndex = sourceY * rowStride + sourceX * OwnedFrameBuffer.BYTES_PER_PIXEL
                val red = source.get(sourceIndex).toInt() and 0xff
                val green = source.get(sourceIndex + 1).toInt() and 0xff
                val blue = source.get(sourceIndex + 2).toInt() and 0xff
                currentLuma[destinationIndex++] =
                    ((LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue) shr 8).toByte()
            }
        }
    }

    fun difference(threshold: Int): DifferenceSummary {
        var changed = 0
        for (index in currentLuma.indices) {
            val difference = abs(
                (currentLuma[index].toInt() and 0xff) -
                    (previousLuma[index].toInt() and 0xff),
            )
            differences[index] = difference.toByte()
            if (difference >= threshold) changed++
        }
        return DifferenceSummary(changed, differences.size)
    }

    fun aggregateGrid(config: TemporalDetectionConfig, suppressGlobalChange: Boolean) {
        changedCounts.fill(0)
        differenceSums.fill(0)
        cellAreas.fill(0)
        activeCells.fill(false)
        for (y in 0 until analysisHeight) {
            val cellRow = (y / gridCellSize) * gridWidth
            val pixelRow = y * analysisWidth
            for (x in 0 until analysisWidth) {
                val cell = cellRow + x / gridCellSize
                val difference = differences[pixelRow + x].toInt() and 0xff
                cellAreas[cell]++
                differenceSums[cell] += difference
                if (difference >= config.pixelDifferenceThreshold) changedCounts[cell]++
            }
        }
        if (suppressGlobalChange) return
        for (cell in activeCells.indices) {
            val area = cellAreas[cell]
            val changedRatio = changedCounts[cell].toFloat() / area
            val meanDifference = differenceSums[cell].toFloat() / area
            activeCells[cell] = changedRatio >= config.activeCellChangedRatio &&
                meanDifference >= config.activeCellMeanDifference
        }
    }

    fun promoteCurrentToPrevious() {
        val reusable = previousLuma
        previousLuma = currentLuma
        currentLuma = reusable
    }

    fun clearActivity() {
        differences.fill(0)
        changedCounts.fill(0)
        differenceSums.fill(0)
        cellAreas.fill(0)
        activeCells.fill(false)
        visitedCells.fill(false)
    }

    companion object {
        private const val LUMA_RED = 77
        private const val LUMA_GREEN = 150
        private const val LUMA_BLUE = 29

        private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor
    }
}
