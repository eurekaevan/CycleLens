package com.eureka.cyclelens.capture

internal fun interface NanoClock {
    fun now(): Long
}

internal class CaptureStatsAccumulator(
    initialSize: CaptureSize,
    private val clock: NanoClock = NanoClock(System::nanoTime),
) : FrameIngress {
    private var width = initialSize.width
    private var height = initialSize.height
    private var receivedFrames = 0L
    private var framesAtLastSnapshot = 0L
    private var sessionStartedNs = clock.now()
    private var lastSnapshotNs = sessionStartedNs
    private var lastFrameTimestampNs: Long? = null
    private var planeCount: Int? = null
    private var rowStride: Int? = null
    private var pixelStride: Int? = null
    private var capturedContentVisible = true

    @Synchronized
    override fun onFrame(frame: FrameMetadata) {
        width = frame.width
        height = frame.height
        receivedFrames += 1
        lastFrameTimestampNs = frame.timestampNs
        planeCount = frame.planeCount
        rowStride = frame.rowStride
        pixelStride = frame.pixelStride
    }

    @Synchronized
    fun resize(size: CaptureSize) {
        width = size.width
        height = size.height
    }

    @Synchronized
    fun setCapturedContentVisible(visible: Boolean) {
        capturedContentVisible = visible
    }

    @Synchronized
    fun snapshot(nowNs: Long = clock.now()): CaptureStats {
        val windowDurationNs = (nowNs - lastSnapshotNs).coerceAtLeast(0L)
        val sessionDurationNs = (nowNs - sessionStartedNs).coerceAtLeast(0L)
        val framesInWindow = receivedFrames - framesAtLastSnapshot
        val currentFps = framesInWindow.ratePerSecond(windowDurationNs)
        val averageFps = receivedFrames.ratePerSecond(sessionDurationNs)

        framesAtLastSnapshot = receivedFrames
        lastSnapshotNs = nowNs

        return CaptureStats(
            width = width,
            height = height,
            receivedFrames = receivedFrames,
            currentFps = currentFps,
            averageFps = averageFps,
            lastFrameTimestampNs = lastFrameTimestampNs,
            planeCount = planeCount,
            pixelStride = pixelStride,
            rowStride = rowStride,
            capturedContentVisible = capturedContentVisible,
        )
    }

    private fun Long.ratePerSecond(durationNs: Long): Float = if (durationNs <= 0L) {
        0f
    } else {
        (this.toDouble() * NANOS_PER_SECOND / durationNs.toDouble()).toFloat()
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
