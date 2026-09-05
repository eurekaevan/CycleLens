package com.eureka.cyclelens.capture

enum class FrameSamplingDecision {
    ACCEPT,
    DROP,
}

class FrameSamplingGate(
    targetAnalysisFps: Int,
) {
    init {
        require(targetAnalysisFps > 0) { "Target analysis FPS must be positive" }
    }

    val minimumIntervalNs: Long =
        (NANOS_PER_SECOND + targetAnalysisFps - 1L) / targetAnalysisFps
    private var lastSeenTimestampNs: Long? = null
    private var nextAcceptedTimestampNs: Long? = null

    fun decide(timestampNs: Long): FrameSamplingDecision {
        require(timestampNs >= 0L) { "Frame timestamp must not be negative" }
        val lastSeen = lastSeenTimestampNs
        if (lastSeen == null || timestampNs < lastSeen) {
            lastSeenTimestampNs = timestampNs
            nextAcceptedTimestampNs = timestampNs.saturatedPlus(minimumIntervalNs)
            return FrameSamplingDecision.ACCEPT
        }
        lastSeenTimestampNs = timestampNs
        val deadline = checkNotNull(nextAcceptedTimestampNs)
        return if (timestampNs >= deadline) {
            var nextDeadline = deadline
            do {
                nextDeadline = nextDeadline.saturatedPlus(minimumIntervalNs)
            } while (nextDeadline <= timestampNs && nextDeadline != Long.MAX_VALUE)
            nextAcceptedTimestampNs = nextDeadline
            FrameSamplingDecision.ACCEPT
        } else {
            FrameSamplingDecision.DROP
        }
    }

    fun reset() {
        lastSeenTimestampNs = null
        nextAcceptedTimestampNs = null
    }

    private fun Long.saturatedPlus(value: Long): Long =
        if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
