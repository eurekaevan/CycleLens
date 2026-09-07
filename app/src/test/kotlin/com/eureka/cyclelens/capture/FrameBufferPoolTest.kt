package com.eureka.cyclelens.capture

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameBufferPoolTest {
    @Test
    fun `pool has fixed tightly packed capacity and exhausts without fallback allocation`() {
        var allocations = 0
        val pool = FrameBufferPool(3, 2, capacity = 2) { size ->
            allocations++
            ByteBuffer.allocate(size)
        }

        val first = assertNotNull(pool.tryAcquire(descriptor(3, 2, 1)))
        val second = assertNotNull(pool.tryAcquire(descriptor(3, 2, 2)))

        assertEquals(2, allocations)
        assertEquals(24, first.byteSize)
        assertEquals(12, first.rowStride)
        assertEquals(0, pool.availableCount)
        assertNull(pool.tryAcquire(descriptor(3, 2, 3)))
        assertEquals(2, allocations)
        assertTrue(first.release())
        assertTrue(second.release())
        assertEquals(2, pool.availableCount)
    }

    @Test
    fun `released slot is reused with clean bounds and stale lease cannot release it`() {
        val pool = FrameBufferPool(2, 2, capacity = 1) { ByteBuffer.allocate(it) }
        val old = assertNotNull(pool.tryAcquire(descriptor(2, 2, 1)))
        old.writablePixels().put(ByteArray(old.byteSize))
        assertTrue(old.release())

        val current = assertNotNull(pool.tryAcquire(descriptor(2, 2, 2)))
        assertEquals(0, current.writablePixels().position())
        assertEquals(current.byteSize, current.writablePixels().limit())
        assertFalse(old.release())
        assertEquals(1, pool.inUseCount)
        assertTrue(current.release())
        assertFalse(current.release())
    }

    @Test
    fun `ownership transitions are explicit and only processing exposes read only pixels`() {
        val pool = FrameBufferPool(2, 2, capacity = 1) { ByteBuffer.allocate(it) }
        val frame = assertNotNull(pool.tryAcquire(descriptor(2, 2, 1)))
        assertEquals(FrameBufferOwnership.WRITING, frame.ownership)
        assertFailsWith<IllegalStateException> { frame.readOnlyPixels() }

        frame.markQueued(5)
        assertEquals(FrameBufferOwnership.QUEUED, frame.ownership)
        frame.markProcessing()
        assertEquals(FrameBufferOwnership.PROCESSING, frame.ownership)
        assertTrue(frame.readOnlyPixels().isReadOnly)
        assertTrue(frame.release())
        assertEquals(FrameBufferOwnership.RELEASED, frame.ownership)
    }

    @Test
    fun `dispose rejects acquisition but permits late release`() {
        val pool = FrameBufferPool(2, 2, capacity = 1)
        val frame = assertNotNull(pool.tryAcquire(descriptor(2, 2, 1)))

        pool.dispose()

        assertTrue(pool.isDisposed)
        assertEquals(0, pool.availableCount)
        assertNull(pool.tryAcquire(descriptor(2, 2, 2)))
        assertTrue(frame.release())
        assertEquals(0, pool.inUseCount)
    }

    @Test
    fun `pool rejects invalid size capacity overflow and wrong geometry`() {
        assertFailsWith<IllegalArgumentException> { FrameBufferPool(0, 1) }
        assertFailsWith<IllegalArgumentException> { FrameBufferPool(1, 0) }
        assertFailsWith<IllegalArgumentException> { FrameBufferPool(1, 1, 0) }
        assertFailsWith<ArithmeticException> { FrameBufferPool(Int.MAX_VALUE, 2) }

        val pool = FrameBufferPool(2, 2, 1)
        assertNull(pool.tryAcquire(descriptor(3, 2, 1)))
    }

    private fun descriptor(width: Int, height: Int, timestamp: Long) = AnalysisFrameDescriptor(
        timestampNs = timestamp,
        geometry = CaptureGeometry(width, height, width, height),
        arenaRect = PixelRect(0, 0, width, height),
    )
}
