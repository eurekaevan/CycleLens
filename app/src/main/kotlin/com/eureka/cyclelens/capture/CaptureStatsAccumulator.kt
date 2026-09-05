package com.eureka.cyclelens.capture

internal fun interface NanoClock {
    fun now(): Long
}

internal class CaptureStatsAccumulator(
    initialGeometry: CaptureGeometry,
    private val profile: CaptureProfile,
    private val surfaceFrameRateHintRequested: Boolean,
    private val surfaceFrameRateHintApplied: Boolean,
    private val clock: NanoClock = NanoClock(System::nanoTime),
) : FrameIngress {
    private var geometry = initialGeometry
    private var width = initialGeometry.outputWidth
    private var height = initialGeometry.outputHeight
    private var receivedFrames = 0L
    private var acceptedFrames = 0L
    private var framesAtLastSnapshot = 0L
    private var acceptedFramesAtLastSnapshot = 0L
    private var sessionStartedNs = clock.now()
    private var lastSnapshotNs = sessionStartedNs
    private var lastFrameTimestampNs: Long? = null
    private var planeCount: Int? = null
    private var rowStride: Int? = null
    private var pixelStride: Int? = null
    private var capturedContentVisible = true

    @Synchronized
    override fun onFrame(frame: FrameMetadata) {
        onFrame(frame, accepted = true)
    }

    @Synchronized
    fun onFrame(frame: FrameMetadata, accepted: Boolean) {
        width = frame.width
        height = frame.height
        receivedFrames += 1
        if (accepted) {
            acceptedFrames += 1
        }
        lastFrameTimestampNs = frame.timestampNs
        planeCount = frame.planeCount
        rowStride = frame.rowStride
        pixelStride = frame.pixelStride
    }

    @Synchronized
    fun resize(nextGeometry: CaptureGeometry) {
        geometry = nextGeometry
        width = nextGeometry.outputWidth
        height = nextGeometry.outputHeight
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
        val acceptedFramesInWindow = acceptedFrames - acceptedFramesAtLastSnapshot
        val incomingFps = framesInWindow.ratePerSecond(windowDurationNs)
        val acceptedFps = acceptedFramesInWindow.ratePerSecond(windowDurationNs)
        val averageIncomingFps = receivedFrames.ratePerSecond(sessionDurationNs)
        val averageAcceptedFps = acceptedFrames.ratePerSecond(sessionDurationNs)

        framesAtLastSnapshot = receivedFrames
        acceptedFramesAtLastSnapshot = acceptedFrames
        lastSnapshotNs = nowNs

        return CaptureStats(
            profile = profile,
            sourceWidth = geometry.sourceWidth,
            sourceHeight = geometry.sourceHeight,
            width = width,
            height = height,
            receivedFrames = receivedFrames,
            acceptedFrames = acceptedFrames,
            droppedFrames = receivedFrames - acceptedFrames,
            incomingFps = incomingFps,
            acceptedFps = acceptedFps,
            averageIncomingFps = averageIncomingFps,
            averageAcceptedFps = averageAcceptedFps,
            lastFrameTimestampNs = lastFrameTimestampNs,
            planeCount = planeCount,
            pixelStride = pixelStride,
            rowStride = rowStride,
            capturedContentVisible = capturedContentVisible,
            surfaceFrameRateHintRequested = surfaceFrameRateHintRequested,
            surfaceFrameRateHintApplied = surfaceFrameRateHintApplied,
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
