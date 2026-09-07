package com.eureka.cyclelens.capture

import java.nio.ByteBuffer

enum class AnalysisPixelFormat {
    RGBA_8888,
}

enum class FrameBufferOwnership {
    WRITING,
    QUEUED,
    PROCESSING,
    RELEASED,
}

/**
 * A single exclusive lease of a pooled, tightly packed arena buffer.
 *
 * The Android Image and its Plane never reach this type. Callers must transfer the lease through
 * the declared ownership states and eventually release it to its originating pool.
 */
class OwnedFrameBuffer internal constructor(
    internal val pool: FrameBufferPool,
    internal val slot: FrameBufferPool.Slot,
    internal val leaseId: Long,
    val descriptor: AnalysisFrameDescriptor,
) {
    val width: Int = descriptor.arenaRect.width
    val height: Int = descriptor.arenaRect.height
    val pixelFormat: AnalysisPixelFormat = AnalysisPixelFormat.RGBA_8888
    val rowStride: Int = width * BYTES_PER_PIXEL
    val byteSize: Int = rowStride * height

    internal var enqueuedAtNs: Long = 0L

    val ownership: FrameBufferOwnership
        get() = pool.ownershipOf(this)

    internal fun writablePixels(): ByteBuffer = pool.writablePixels(this)

    fun readOnlyPixels(): ByteBuffer = pool.readOnlyPixels(this)

    internal fun markQueued(nowNs: Long) {
        pool.transition(this, FrameBufferOwnership.WRITING, FrameBufferOwnership.QUEUED)
        enqueuedAtNs = nowNs
    }

    internal fun markProcessing() {
        pool.transition(this, FrameBufferOwnership.QUEUED, FrameBufferOwnership.PROCESSING)
    }

    fun release(): Boolean = pool.release(this)

    companion object {
        const val BYTES_PER_PIXEL = 4
    }
}

class FrameBufferPool(
    val width: Int,
    val height: Int,
    val capacity: Int = DEFAULT_CAPACITY,
    private val bufferFactory: (Int) -> ByteBuffer = ByteBuffer::allocateDirect,
) {
    init {
        require(width > 0) { "Buffer width must be positive" }
        require(height > 0) { "Buffer height must be positive" }
        require(capacity > 0) { "Pool capacity must be positive" }
    }

    val bufferByteSize: Int = Math.multiplyExact(
        Math.multiplyExact(width, height),
        OwnedFrameBuffer.BYTES_PER_PIXEL,
    )
    val totalByteSize: Long = bufferByteSize.toLong() * capacity

    internal class Slot(
        val pixels: ByteBuffer,
        var ownership: FrameBufferOwnership = FrameBufferOwnership.RELEASED,
        var leaseId: Long = 0L,
    )

    private val slots = List(capacity) { Slot(bufferFactory(bufferByteSize)) }
    private var nextLeaseId = 1L
    private var disposed = false

    @Synchronized
    fun tryAcquire(descriptor: AnalysisFrameDescriptor): OwnedFrameBuffer? {
        if (disposed || descriptor.arenaRect.width != width || descriptor.arenaRect.height != height) {
            return null
        }
        val slot = slots.firstOrNull { it.ownership == FrameBufferOwnership.RELEASED }
            ?: return null
        val leaseId = nextLeaseId++
        slot.leaseId = leaseId
        slot.ownership = FrameBufferOwnership.WRITING
        slot.pixels.clear()
        return OwnedFrameBuffer(this, slot, leaseId, descriptor)
    }

    @Synchronized
    fun dispose() {
        disposed = true
    }

    @get:Synchronized
    val availableCount: Int
        get() = if (disposed) 0 else slots.count { it.ownership == FrameBufferOwnership.RELEASED }

    @get:Synchronized
    val inUseCount: Int
        get() = slots.count { it.ownership != FrameBufferOwnership.RELEASED }

    @get:Synchronized
    val isDisposed: Boolean
        get() = disposed

    @Synchronized
    internal fun ownershipOf(frame: OwnedFrameBuffer): FrameBufferOwnership {
        validateLease(frame)
        return frame.slot.ownership
    }

    @Synchronized
    internal fun writablePixels(frame: OwnedFrameBuffer): ByteBuffer {
        validateLease(frame)
        check(frame.slot.ownership == FrameBufferOwnership.WRITING) {
            "Pixels are mutable only while WRITING"
        }
        return frame.slot.pixels
    }

    @Synchronized
    internal fun readOnlyPixels(frame: OwnedFrameBuffer): ByteBuffer {
        validateLease(frame)
        check(frame.slot.ownership == FrameBufferOwnership.PROCESSING) {
            "Pixels are readable only while PROCESSING"
        }
        return frame.slot.pixels.asReadOnlyBuffer().apply {
            position(0)
            limit(frame.byteSize)
        }
    }

    @Synchronized
    internal fun transition(
        frame: OwnedFrameBuffer,
        expected: FrameBufferOwnership,
        next: FrameBufferOwnership,
    ) {
        validateLease(frame)
        check(frame.slot.ownership == expected) {
            "Expected $expected ownership, found ${frame.slot.ownership}"
        }
        frame.slot.ownership = next
    }

    @Synchronized
    internal fun release(frame: OwnedFrameBuffer): Boolean {
        if (frame.pool !== this || frame.slot.leaseId != frame.leaseId) {
            return false
        }
        if (frame.slot.ownership == FrameBufferOwnership.RELEASED) {
            return false
        }
        frame.slot.ownership = FrameBufferOwnership.RELEASED
        frame.slot.pixels.clear()
        return true
    }

    private fun validateLease(frame: OwnedFrameBuffer) {
        check(frame.pool === this && frame.slot.leaseId == frame.leaseId) {
            "Frame buffer lease is stale or belongs to another pool"
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 3
    }
}
