package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisFrameQueueTest {
    @Test
    fun `capacity one drops oldest and retains latest`() {
        val pool = FrameBufferPool(2, 2, 3)
        val queue = AnalysisFrameQueue(1)
        val first = acquire(pool, 1)
        val latest = acquire(pool, 2)

        assertFalse(queue.offer(first, 10).droppedOldest)
        assertTrue(queue.offer(latest, 20).droppedOldest)

        assertEquals(FrameBufferOwnership.RELEASED, first.ownership)
        assertEquals(1, queue.size)
        val dequeued = assertNotNull(queue.poll())
        assertEquals(2, dequeued.descriptor.timestampNs)
        assertEquals(FrameBufferOwnership.PROCESSING, dequeued.ownership)
        assertTrue(dequeued.release())
        assertNull(queue.poll())
        assertEquals(3, pool.availableCount)
    }

    @Test
    fun `clear releases every queued lease`() {
        val pool = FrameBufferPool(2, 2, 3)
        val queue = AnalysisFrameQueue(2)
        queue.offer(acquire(pool, 1), 1)
        queue.offer(acquire(pool, 2), 2)

        assertEquals(2, queue.clear())

        assertEquals(0, queue.size)
        assertEquals(3, pool.availableCount)
    }

    @Test
    fun `closed queue rejects and releases submitted writing frame`() {
        val pool = FrameBufferPool(2, 2, 1)
        val queue = AnalysisFrameQueue()
        queue.close()
        val frame = acquire(pool, 1)

        val result = queue.offer(frame, 1)

        assertFalse(result.accepted)
        assertEquals(FrameBufferOwnership.RELEASED, frame.ownership)
        assertEquals(1, pool.availableCount)
    }

    @Test
    fun `repeated overflow remains bounded and returns all buffers`() {
        val pool = FrameBufferPool(2, 2, 3)
        val queue = AnalysisFrameQueue(1)
        repeat(100) { index -> queue.offer(acquire(pool, index.toLong()), index.toLong()) }

        assertEquals(1, queue.size)
        queue.close()
        assertEquals(0, pool.inUseCount)
    }

    private fun acquire(pool: FrameBufferPool, timestamp: Long): OwnedFrameBuffer =
        assertNotNull(
            pool.tryAcquire(
                AnalysisFrameDescriptor(
                    timestamp,
                    CaptureGeometry(2, 2, 2, 2),
                    PixelRect(0, 0, 2, 2),
                ),
            ),
        )
}
