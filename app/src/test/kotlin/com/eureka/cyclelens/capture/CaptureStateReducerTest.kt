package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CaptureStateReducerTest {
    @Test
    fun `idle requests user consent`() {
        assertEquals(
            CaptureState.RequestingConsent,
            CaptureStateReducer.reduce(CaptureState.Idle, CaptureEvent.RequestConsent),
        )
    }

    @Test
    fun `rejected consent returns to idle`() {
        assertEquals(
            CaptureState.Idle,
            CaptureStateReducer.reduce(
                CaptureState.RequestingConsent,
                CaptureEvent.RejectConsent,
            ),
        )
    }

    @Test
    fun `accepted consent starts service transition`() {
        assertEquals(
            CaptureState.Starting,
            CaptureStateReducer.reduce(
                CaptureState.RequestingConsent,
                CaptureEvent.AcceptConsent,
            ),
        )
    }

    @Test
    fun `capture start publishes running stats`() {
        val next = CaptureStateReducer.reduce(
            CaptureState.Starting,
            CaptureEvent.Start(EMPTY_STATS),
        )

        assertIs<CaptureState.Running>(next)
        assertEquals(EMPTY_STATS, next.stats)
    }

    @Test
    fun `system stop returns to idle`() {
        assertEquals(
            CaptureState.Idle,
            CaptureStateReducer.reduce(
                CaptureState.Running(EMPTY_STATS),
                CaptureEvent.Stop,
            ),
        )
    }

    @Test
    fun `failure exposes an error state`() {
        assertEquals(
            CaptureState.Error("failed"),
            CaptureStateReducer.reduce(CaptureState.Starting, CaptureEvent.Fail("failed")),
        )
    }

    @Test
    fun `duplicate request is ignored while capture is active`() {
        val running = CaptureState.Running(EMPTY_STATS)

        assertEquals(
            running,
            CaptureStateReducer.reduce(running, CaptureEvent.RequestConsent),
        )
    }

    private companion object {
        val EMPTY_STATS = CaptureStats(
            profile = CaptureProfile.NATIVE,
            sourceWidth = 1440,
            sourceHeight = 3120,
            width = 1440,
            height = 3120,
            receivedFrames = 0,
            acceptedFrames = 0,
            droppedFrames = 0,
            incomingFps = 0f,
            acceptedFps = 0f,
            averageIncomingFps = 0f,
            averageAcceptedFps = 0f,
            lastFrameTimestampNs = null,
            planeCount = null,
            pixelStride = null,
            rowStride = null,
            capturedContentVisible = true,
            surfaceFrameRateHintRequested = true,
            surfaceFrameRateHintApplied = true,
        )
    }
}
