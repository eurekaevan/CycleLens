package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CaptureResourceCoordinatorTest {
    @Test
    fun `duplicate start is ignored`() {
        val coordinator = CaptureResourceCoordinator()

        assertTrue(coordinator.begin(INITIAL_SIZE))
        assertFalse(coordinator.begin(INITIAL_SIZE))
    }

    @Test
    fun `stop is idempotent`() {
        val coordinator = CaptureResourceCoordinator()
        coordinator.begin(INITIAL_SIZE)

        assertTrue(coordinator.stop())
        assertFalse(coordinator.stop())
    }

    @Test
    fun `resize records surface replacement sizes`() {
        val coordinator = CaptureResourceCoordinator()
        coordinator.begin(INITIAL_SIZE)

        val decision = coordinator.resize(RESIZED)

        assertIs<ResizeDecision.Replace>(decision)
        assertEquals(INITIAL_SIZE, decision.previous)
        assertEquals(RESIZED, decision.next)
        assertEquals(ResizeDecision.Unchanged, coordinator.resize(RESIZED))
    }

    @Test
    fun `resize is ignored without an active session`() {
        val coordinator = CaptureResourceCoordinator()

        assertEquals(ResizeDecision.Ignore, coordinator.resize(RESIZED))
    }

    private companion object {
        val INITIAL_SIZE = CaptureSize(1440, 3120)
        val RESIZED = CaptureSize(1080, 2340)
    }
}
