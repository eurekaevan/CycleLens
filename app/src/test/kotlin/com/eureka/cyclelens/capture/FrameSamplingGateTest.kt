package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameSamplingGateTest {
    @Test
    fun `one hundred twenty incoming frames are sampled near fifteen fps`() {
        assertEquals(15, acceptedInOneSecond(targetFps = 15))
    }

    @Test
    fun `one hundred twenty incoming frames are sampled near ten fps`() {
        assertEquals(10, acceptedInOneSecond(targetFps = 10))
    }

    @Test
    fun `irregular timestamps do not create a backlog`() {
        val gate = FrameSamplingGate(10)
        val decisions = listOf(0L, 20_000_000L, 150_000_000L, 151_000_000L, 399_000_000L)
            .map(gate::decide)
        assertEquals(
            listOf(
                FrameSamplingDecision.ACCEPT,
                FrameSamplingDecision.DROP,
                FrameSamplingDecision.ACCEPT,
                FrameSamplingDecision.DROP,
                FrameSamplingDecision.ACCEPT,
            ),
            decisions,
        )
    }

    @Test
    fun `exact interval boundary is accepted`() {
        val gate = FrameSamplingGate(10)
        assertEquals(FrameSamplingDecision.ACCEPT, gate.decide(1_000L))
        assertEquals(FrameSamplingDecision.ACCEPT, gate.decide(100_001_000L))
    }

    @Test
    fun `clock regression resets schedule defensively`() {
        val gate = FrameSamplingGate(10)
        gate.decide(1_000_000_000L)
        gate.decide(1_050_000_000L)
        assertEquals(FrameSamplingDecision.ACCEPT, gate.decide(10L))
        assertEquals(FrameSamplingDecision.DROP, gate.decide(20L))
    }

    @Test
    fun `reset starts a new session`() {
        val gate = FrameSamplingGate(15)
        gate.decide(1_000L)
        assertEquals(FrameSamplingDecision.DROP, gate.decide(2_000L))
        gate.reset()
        assertEquals(FrameSamplingDecision.ACCEPT, gate.decide(2_000L))
    }

    private fun acceptedInOneSecond(targetFps: Int): Int {
        val gate = FrameSamplingGate(targetFps)
        return (0 until 120).count { frame ->
            val timestamp = frame * 1_000_000_000L / 120L
            gate.decide(timestamp) == FrameSamplingDecision.ACCEPT
        }
    }
}
