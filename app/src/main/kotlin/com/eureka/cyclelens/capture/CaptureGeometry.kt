package com.eureka.cyclelens.capture

import kotlin.math.abs
import kotlin.math.roundToInt

data class PixelPoint(
    val x: Float,
    val y: Float,
)

data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0) { "PixelRect left must not be negative" }
        require(top >= 0) { "PixelRect top must not be negative" }
        require(right > left) { "PixelRect right must be greater than left" }
        require(bottom > top) { "PixelRect bottom must be greater than top" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class CaptureGeometry(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
) {
    init {
        require(sourceWidth > 0) { "Source width must be positive" }
        require(sourceHeight > 0) { "Source height must be positive" }
        require(outputWidth > 0) { "Output width must be positive" }
        require(outputHeight > 0) { "Output height must be positive" }
        val idealOutputWidth = outputHeight.toDouble() * sourceWidth / sourceHeight
        require(
            abs(outputWidth - idealOutputWidth) <= MAX_ROUNDING_ERROR_PX,
        ) { "Source and output aspect ratios must match" }
    }

    val scaleX: Float = outputWidth.toFloat() / sourceWidth
    val scaleY: Float = outputHeight.toFloat() / sourceHeight

    fun sourceToOutput(x: Float, y: Float): PixelPoint = PixelPoint(x * scaleX, y * scaleY)

    fun outputToSource(x: Float, y: Float): PixelPoint = PixelPoint(x / scaleX, y / scaleY)

    fun normalizedToOutput(x: Float, y: Float): PixelPoint {
        require(x in 0f..1f) { "Normalized x must be between 0 and 1" }
        require(y in 0f..1f) { "Normalized y must be between 0 and 1" }
        return PixelPoint(x * outputWidth, y * outputHeight)
    }

    companion object {
        private const val MAX_ROUNDING_ERROR_PX = 2.0

        fun derive(sourceWidth: Int, sourceHeight: Int, maxLongEdge: Int?): CaptureGeometry {
            require(sourceWidth > 0) { "Source width must be positive" }
            require(sourceHeight > 0) { "Source height must be positive" }
            require(maxLongEdge == null || maxLongEdge > 0) { "Maximum long edge must be positive" }

            if (maxLongEdge == null || maxOf(sourceWidth, sourceHeight) <= maxLongEdge) {
                return CaptureGeometry(sourceWidth, sourceHeight, sourceWidth, sourceHeight)
            }

            val scale = maxLongEdge.toDouble() / maxOf(sourceWidth, sourceHeight)
            val rawWidth = sourceWidth * scale
            val rawHeight = sourceHeight * scale
            val outputWidth = rawWidth.nearestPositiveEven()
            val outputHeight = rawHeight.nearestPositiveEven()
            return CaptureGeometry(sourceWidth, sourceHeight, outputWidth, outputHeight)
        }

        private fun Double.nearestPositiveEven(): Int {
            val rounded = roundToInt().coerceAtLeast(1)
            return when {
                rounded == 1 -> 2
                rounded % 2 == 0 -> rounded
                else -> if (this >= rounded) rounded + 1 else rounded - 1
            }
        }
    }
}
