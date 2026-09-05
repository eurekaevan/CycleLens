package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureSessionStateStoreTest {
    @Test
    fun `store exposes read only state and ignores a second consent request`() {
        val store = CaptureSessionStateStore()

        assertTrue(store.requestConsent())
        assertFalse(store.requestConsent())
        assertEquals(CaptureState.RequestingConsent, store.state.value)
    }

    @Test
    fun `published stats only update a running session`() {
        val store = CaptureSessionStateStore()
        val updated = STATS.copy(receivedFrames = 10)

        store.publish(updated)
        assertEquals(CaptureState.Idle, store.state.value)

        store.requestConsent()
        store.acceptConsent()
        store.start(STATS)
        store.publish(updated)

        assertEquals(CaptureState.Running(updated), store.state.value)
    }

    private companion object {
        val STATS = CaptureStats(
            width = 100,
            height = 200,
            receivedFrames = 0,
            currentFps = 0f,
            averageFps = 0f,
            lastFrameTimestampNs = null,
            planeCount = null,
            pixelStride = null,
            rowStride = null,
            capturedContentVisible = true,
        )
    }
}
