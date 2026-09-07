package com.eureka.cyclelens.capture

import java.nio.ByteBuffer

data class RgbaSourceLayout(
    val width: Int,
    val height: Int,
    val pixelStride: Int,
    val rowStride: Int,
)

enum class ArenaCopyFailure {
    INVALID_SOURCE_DIMENSIONS,
    UNSUPPORTED_PIXEL_STRIDE,
    INVALID_ROW_STRIDE,
    ARENA_OUT_OF_BOUNDS,
    SOURCE_TRUNCATED,
    DESTINATION_READ_ONLY,
    DESTINATION_TOO_SMALL,
    COPY_EXCEPTION,
}

sealed interface ArenaCopyResult {
    data class Success(val bytesCopied: Int) : ArenaCopyResult
    data class Failure(val reason: ArenaCopyFailure) : ArenaCopyResult
}

object RgbaArenaCopier {
    fun copy(
        source: ByteBuffer,
        sourceLayout: RgbaSourceLayout,
        arenaRect: PixelRect,
        destination: ByteBuffer,
    ): ArenaCopyResult {
        if (sourceLayout.width <= 0 || sourceLayout.height <= 0) {
            return ArenaCopyResult.Failure(ArenaCopyFailure.INVALID_SOURCE_DIMENSIONS)
        }
        if (sourceLayout.pixelStride != OwnedFrameBuffer.BYTES_PER_PIXEL) {
            return ArenaCopyResult.Failure(ArenaCopyFailure.UNSUPPORTED_PIXEL_STRIDE)
        }
        val minimumRowBytes = sourceLayout.width.toLong() * sourceLayout.pixelStride
        if (sourceLayout.rowStride < minimumRowBytes) {
            return ArenaCopyResult.Failure(ArenaCopyFailure.INVALID_ROW_STRIDE)
        }
        if (
            arenaRect.right > sourceLayout.width ||
            arenaRect.bottom > sourceLayout.height
        ) {
            return ArenaCopyResult.Failure(ArenaCopyFailure.ARENA_OUT_OF_BOUNDS)
        }
        val rowBytes = arenaRect.width.toLong() * sourceLayout.pixelStride
        val requiredDestinationBytes = rowBytes * arenaRect.height
        if (destination.isReadOnly) {
            return ArenaCopyResult.Failure(ArenaCopyFailure.DESTINATION_READ_ONLY)
        }
        if (requiredDestinationBytes > destination.capacity()) {
            return ArenaCopyResult.Failure(ArenaCopyFailure.DESTINATION_TOO_SMALL)
        }

        val baseOffset = source.position().toLong()
        val lastRowStart = baseOffset +
            arenaRect.top.toLong().plus(arenaRect.height - 1L) * sourceLayout.rowStride +
            arenaRect.left.toLong() * sourceLayout.pixelStride
        val requiredSourceLimit = lastRowStart + rowBytes
        if (requiredSourceLimit > source.limit().toLong()) {
            return ArenaCopyResult.Failure(ArenaCopyFailure.SOURCE_TRUNCATED)
        }

        val sourceView = source.duplicate()
        destination.clear()
        repeat(arenaRect.height) { row ->
            val rowStart = (
                baseOffset +
                    (arenaRect.top + row).toLong() * sourceLayout.rowStride +
                    arenaRect.left.toLong() * sourceLayout.pixelStride
                ).toInt()
            sourceView.limit(source.limit())
            sourceView.position(rowStart)
            sourceView.limit(rowStart + rowBytes.toInt())
            destination.put(sourceView)
        }
        destination.flip()
        return ArenaCopyResult.Success(requiredDestinationBytes.toInt())
    }
}
