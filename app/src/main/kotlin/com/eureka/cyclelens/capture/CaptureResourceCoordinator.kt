package com.eureka.cyclelens.capture

data class CaptureSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "Capture width must be positive" }
        require(height > 0) { "Capture height must be positive" }
    }
}

internal sealed interface ResizeDecision {
    data object Ignore : ResizeDecision
    data object Unchanged : ResizeDecision
    data class Replace(
        val previous: CaptureSize,
        val next: CaptureSize,
    ) : ResizeDecision
}

internal class CaptureResourceCoordinator {
    private var activeSize: CaptureSize? = null

    fun begin(size: CaptureSize): Boolean {
        if (activeSize != null) {
            return false
        }
        activeSize = size
        return true
    }

    fun resize(size: CaptureSize): ResizeDecision {
        val previous = activeSize ?: return ResizeDecision.Ignore
        if (previous == size) {
            return ResizeDecision.Unchanged
        }
        activeSize = size
        return ResizeDecision.Replace(previous, size)
    }

    fun stop(): Boolean {
        if (activeSize == null) {
            return false
        }
        activeSize = null
        return true
    }
}
