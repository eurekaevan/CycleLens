package com.eureka.cyclelens.capture

sealed interface CaptureState {
    data object Idle : CaptureState

    data object RequestingConsent : CaptureState

    data object Starting : CaptureState

    data class Running(
        val stats: CaptureStats,
    ) : CaptureState

    data class Error(
        val message: String,
    ) : CaptureState
}

data class CaptureStats(
    val profile: CaptureProfile,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val width: Int,
    val height: Int,
    val receivedFrames: Long,
    val acceptedFrames: Long,
    val droppedFrames: Long,
    val incomingFps: Float,
    val acceptedFps: Float,
    val averageIncomingFps: Float,
    val averageAcceptedFps: Float,
    val lastFrameTimestampNs: Long?,
    val planeCount: Int?,
    val pixelStride: Int?,
    val rowStride: Int?,
    val capturedContentVisible: Boolean,
    val surfaceFrameRateHintRequested: Boolean,
    val surfaceFrameRateHintApplied: Boolean,
)

internal sealed interface CaptureEvent {
    data object RequestConsent : CaptureEvent
    data object RejectConsent : CaptureEvent
    data object AcceptConsent : CaptureEvent
    data class Start(val stats: CaptureStats) : CaptureEvent
    data class PublishStats(val stats: CaptureStats) : CaptureEvent
    data object Stop : CaptureEvent
    data class Fail(val message: String) : CaptureEvent
}

internal object CaptureStateReducer {
    fun reduce(state: CaptureState, event: CaptureEvent): CaptureState = when (event) {
        CaptureEvent.RequestConsent -> when (state) {
            CaptureState.Idle,
            is CaptureState.Error,
            -> CaptureState.RequestingConsent

            else -> state
        }

        CaptureEvent.RejectConsent -> if (state == CaptureState.RequestingConsent) {
            CaptureState.Idle
        } else {
            state
        }

        CaptureEvent.AcceptConsent -> if (state == CaptureState.RequestingConsent) {
            CaptureState.Starting
        } else {
            state
        }

        is CaptureEvent.Start -> if (state == CaptureState.Starting) {
            CaptureState.Running(event.stats)
        } else {
            state
        }

        is CaptureEvent.PublishStats -> if (state is CaptureState.Running) {
            CaptureState.Running(event.stats)
        } else {
            state
        }

        CaptureEvent.Stop -> CaptureState.Idle
        is CaptureEvent.Fail -> CaptureState.Error(event.message)
    }
}
