package com.eureka.cyclelens.capture

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class RgbaRowCopyTest {
    @Test
    fun `copies rows without padding`() {
        val bytes = ByteArray(16) { it.toByte() }
        assertContentEquals(bytes, RgbaRowCopy.tightlyPacked(ByteBuffer.wrap(bytes), 2, 2, 4, 8))
    }

    @Test
    fun `skips Samsung style row padding`() {
        val rowBytes = 1440 * 4
        val rowStride = 5888
        val source = ByteArray(rowStride + rowBytes) { 0x7f.toByte() }
        source.fill(0x55.toByte(), rowBytes, rowStride)
        val packed = RgbaRowCopy.tightlyPacked(ByteBuffer.wrap(source), 1440, 2, 4, rowStride)
        assertContentEquals(ByteArray(rowBytes * 2) { 0x7f.toByte() }, packed)
    }

    @Test
    fun `copy respects a nonzero buffer position`() {
        val source = ByteBuffer.wrap(byteArrayOf(99, 1, 2, 3, 4, 88))
        source.position(1)
        source.limit(5)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), RgbaRowCopy.tightlyPacked(source, 1, 1, 4, 4))
    }

    @Test
    fun `truncated buffer is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RgbaRowCopy.tightlyPacked(ByteBuffer.allocate(15), 2, 2, 4, 8)
        }
    }
}
