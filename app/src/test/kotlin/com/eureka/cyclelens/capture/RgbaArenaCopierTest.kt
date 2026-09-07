package com.eureka.cyclelens.capture

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RgbaArenaCopierTest {
    @Test
    fun `copies exact cropped pixels across Samsung-style padded rows`() {
        val source = paddedPixels(width = 4, height = 3, rowStride = 20)
        val destination = ByteBuffer.allocateDirect(2 * 2 * 4)

        val result = RgbaArenaCopier.copy(
            source,
            RgbaSourceLayout(4, 3, pixelStride = 4, rowStride = 20),
            PixelRect(left = 1, top = 1, right = 3, bottom = 3),
            destination,
        )

        assertEquals(ArenaCopyResult.Success(16), result)
        assertContentEquals(
            byteArrayOf(11, 11, 11, 11, 12, 12, 12, 12, 21, 21, 21, 21, 22, 22, 22, 22),
            destination.bytes(),
        )
    }

    @Test
    fun `honors nonzero source position and restrictive limit`() {
        val backing = ByteBuffer.allocate(5 + 16 + 7)
        repeat(5) { backing.put(99) }
        repeat(16) { backing.put(it.toByte()) }
        backing.position(5)
        backing.limit(21)
        val destination = ByteBuffer.allocate(16)

        val result = RgbaArenaCopier.copy(
            backing,
            RgbaSourceLayout(2, 2, 4, 8),
            PixelRect(0, 0, 2, 2),
            destination,
        )

        assertIs<ArenaCopyResult.Success>(result)
        assertContentEquals(ByteArray(16) { it.toByte() }, destination.bytes())
    }

    @Test
    fun `copies full frame without padding`() {
        val source = ByteBuffer.wrap(ByteArray(16) { (it + 1).toByte() })
        val destination = ByteBuffer.allocate(16)
        assertEquals(
            ArenaCopyResult.Success(16),
            RgbaArenaCopier.copy(
                source,
                RgbaSourceLayout(2, 2, 4, 8),
                PixelRect(0, 0, 2, 2),
                destination,
            ),
        )
        assertContentEquals(ByteArray(16) { (it + 1).toByte() }, destination.bytes())
    }

    @Test
    fun `rejects unsupported or malformed source layout`() {
        val destination = ByteBuffer.allocate(16)
        val source = ByteBuffer.allocate(16)

        assertFailure(ArenaCopyFailure.INVALID_SOURCE_DIMENSIONS, source, RgbaSourceLayout(0, 2, 4, 8), PixelRect(0, 0, 1, 1), destination)
        assertFailure(ArenaCopyFailure.UNSUPPORTED_PIXEL_STRIDE, source, RgbaSourceLayout(2, 2, 3, 8), PixelRect(0, 0, 1, 1), destination)
        assertFailure(ArenaCopyFailure.INVALID_ROW_STRIDE, source, RgbaSourceLayout(2, 2, 4, 7), PixelRect(0, 0, 1, 1), destination)
        assertFailure(ArenaCopyFailure.ARENA_OUT_OF_BOUNDS, source, RgbaSourceLayout(1, 1, 4, 4), PixelRect(0, 0, 2, 1), destination)
    }

    @Test
    fun `rejects truncated source and undersized or readonly destination`() {
        val rect = PixelRect(0, 0, 2, 2)
        val layout = RgbaSourceLayout(2, 2, 4, 8)
        assertFailure(ArenaCopyFailure.SOURCE_TRUNCATED, ByteBuffer.allocate(15), layout, rect, ByteBuffer.allocate(16))
        assertFailure(ArenaCopyFailure.DESTINATION_TOO_SMALL, ByteBuffer.allocate(16), layout, rect, ByteBuffer.allocate(15))
        assertFailure(ArenaCopyFailure.DESTINATION_READ_ONLY, ByteBuffer.allocate(16), layout, rect, ByteBuffer.allocate(16).asReadOnlyBuffer())
    }

    private fun assertFailure(
        expected: ArenaCopyFailure,
        source: ByteBuffer,
        layout: RgbaSourceLayout,
        rect: PixelRect,
        destination: ByteBuffer,
    ) {
        assertEquals(ArenaCopyResult.Failure(expected), RgbaArenaCopier.copy(source, layout, rect, destination))
    }

    private fun paddedPixels(width: Int, height: Int, rowStride: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(rowStride * height)
        repeat(height) { y ->
            repeat(width) { x -> repeat(4) { buffer.put((y * 10 + x).toByte()) } }
            repeat(rowStride - width * 4) { buffer.put(127) }
        }
        buffer.flip()
        return buffer
    }

    private fun ByteBuffer.bytes(): ByteArray = ByteArray(remaining()).also { duplicate().get(it) }
}
