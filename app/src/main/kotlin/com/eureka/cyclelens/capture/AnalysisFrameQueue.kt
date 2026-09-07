package com.eureka.cyclelens.capture

data class QueueOfferResult(
    val accepted: Boolean,
    val droppedOldest: Boolean,
)

/** Capacity-bounded FIFO with DROP_OLDEST overflow, preserving the freshest useful frame. */
class AnalysisFrameQueue(
    val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "Queue capacity must be positive" }
    }

    private val frames = ArrayDeque<OwnedFrameBuffer>(capacity)
    private var closed = false

    @Synchronized
    fun offer(frame: OwnedFrameBuffer, nowNs: Long): QueueOfferResult {
        if (closed) {
            frame.release()
            return QueueOfferResult(accepted = false, droppedOldest = false)
        }
        frame.markQueued(nowNs)
        val dropped = if (frames.size == capacity) frames.removeFirst() else null
        dropped?.release()
        frames.addLast(frame)
        return QueueOfferResult(accepted = true, droppedOldest = dropped != null)
    }

    @Synchronized
    fun poll(): OwnedFrameBuffer? = frames.removeFirstOrNull()?.also { it.markProcessing() }

    @Synchronized
    fun clear(): Int {
        val count = frames.size
        frames.forEach { it.release() }
        frames.clear()
        return count
    }

    @Synchronized
    fun close(): Int {
        closed = true
        return clear()
    }

    @get:Synchronized
    val size: Int
        get() = frames.size

    @get:Synchronized
    val isClosed: Boolean
        get() = closed

    companion object {
        const val DEFAULT_CAPACITY = 1
    }
}
