package com.eureka.cyclelens.capture

import kotlin.math.ceil
import kotlin.math.floor

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left >= 0f) { "NormalizedRect left must not be negative" }
        require(top >= 0f) { "NormalizedRect top must not be negative" }
        require(right <= 1f) { "NormalizedRect right must not exceed 1" }
        require(bottom <= 1f) { "NormalizedRect bottom must not exceed 1" }
        require(left < right) { "NormalizedRect left must be less than right" }
        require(top < bottom) { "NormalizedRect top must be less than bottom" }
    }

    fun toPixelRect(geometry: CaptureGeometry): PixelRect = PixelRect(
        left = floor(left * geometry.outputWidth).toInt().coerceIn(0, geometry.outputWidth - 1),
        top = floor(top * geometry.outputHeight).toInt().coerceIn(0, geometry.outputHeight - 1),
        right = ceil(right * geometry.outputWidth).toInt().coerceIn(1, geometry.outputWidth),
        bottom = ceil(bottom * geometry.outputHeight).toInt().coerceIn(1, geometry.outputHeight),
    )

    companion object {
        val FULL_FRAME = NormalizedRect(0f, 0f, 1f, 1f)
    }
}

enum class AnalysisRegion {
    FULL_FRAME,
    ARENA,
    CUSTOM_DEBUG,
}

data class AnalysisFrameDescriptor(
    val timestampNs: Long,
    val geometry: CaptureGeometry,
    val arenaRect: PixelRect,
)

object ClashRoyaleCaptureLayout {
    // Measured from a 720 x 1560 single-app battle capture on Samsung SM-S9260.
    // Full width is retained; top player/timer UI and bottom hand bar are excluded.
    val arenaRegion = NormalizedRect(
        left = 0f,
        top = 0.095f,
        right = 1f,
        bottom = 0.855f,
    )
}
