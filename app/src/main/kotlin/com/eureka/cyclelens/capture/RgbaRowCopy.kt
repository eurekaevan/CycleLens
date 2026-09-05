package com.eureka.cyclelens.capture

object RgbaRowCopy {
    fun tightlyPacked(
        source: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        pixelStride: Int,
        rowStride: Int,
    ): ByteArray {
        require(width > 0) { "Width must be positive" }
        require(height > 0) { "Height must be positive" }
        require(pixelStride == RGBA_BYTES_PER_PIXEL) { "RGBA pixel stride must be 4" }
        val rowBytes = Math.multiplyExact(width, pixelStride)
        require(rowStride >= rowBytes) { "Row stride must contain the visible row" }
        val requiredBytes = Math.addExact(Math.multiplyExact(height - 1, rowStride), rowBytes)
        val view = source.duplicate()
        require(view.remaining() >= requiredBytes) { "Pixel buffer is truncated" }

        val result = ByteArray(Math.multiplyExact(rowBytes, height))
        val initialPosition = view.position()
        repeat(height) { row ->
            view.position(initialPosition + row * rowStride)
            view.get(result, row * rowBytes, rowBytes)
        }
        return result
    }

    private const val RGBA_BYTES_PER_PIXEL = 4
}
