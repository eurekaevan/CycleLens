package com.eureka.cyclelens.capture

data class FrameMetadata(
    val width: Int,
    val height: Int,
    val timestampNs: Long,
    val planeCount: Int,
    val rowStride: Int?,
    val pixelStride: Int?,
)

fun interface FrameIngress {
    fun onFrame(frame: FrameMetadata)
}
