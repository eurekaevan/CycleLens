package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemporalChangeTrackerTest {
    private val config = TemporalDetectionConfig(
        trackingIouThreshold = 0.2f,
        trackExpirationNs = 100,
        trackCooldownNs = 200,
    )

    @Test
    fun `overlap updates same track while distant change starts another`() {
        val tracker = TemporalChangeTracker(config)
        val start = tracker.update(listOf(candidate(0f, 0f, 0.4f, 0.4f, 0)), 0)
        val update = tracker.update(listOf(candidate(0.1f, 0.1f, 0.4f, 0.4f, 50)), 50)
        val distant = tracker.update(listOf(candidate(0.6f, 0.6f, 0.9f, 0.9f, 60)), 60)

        assertEquals(TemporalChangePhase.START, start.events.single().phase)
        assertEquals(start.events.single().trackId, update.events.single().trackId)
        assertEquals(TemporalChangePhase.UPDATE, update.events.single().phase)
        assertEquals(TemporalChangePhase.START, distant.events.single().phase)
        assertEquals(2, distant.activeTrackCount)
    }

    @Test
    fun `expiration emits end once and cooldown suppresses a second start`() {
        val tracker = TemporalChangeTracker(config)
        val first = tracker.update(listOf(candidate(timestamp = 0)), 0).events.single()
        val ended = tracker.update(emptyList(), 100)
        val noDuplicateEnd = tracker.update(emptyList(), 150)
        val resumed = tracker.update(listOf(candidate(timestamp = 199)), 199).events.single()

        assertEquals(TemporalChangePhase.END, ended.events.single().phase)
        assertTrue(noDuplicateEnd.events.isEmpty())
        assertEquals(first.trackId, resumed.trackId)
        assertEquals(TemporalChangePhase.UPDATE, resumed.phase)
    }

    @Test
    fun `candidate after cooldown starts a new track and reset restarts ids`() {
        val tracker = TemporalChangeTracker(config)
        val first = tracker.update(listOf(candidate(timestamp = 0)), 0).events.single()
        tracker.update(emptyList(), 100)
        val afterCooldown = tracker.update(listOf(candidate(timestamp = 200)), 200).events.single()
        assertTrue(afterCooldown.trackId != first.trackId)
        assertEquals(TemporalChangePhase.START, afterCooldown.phase)

        tracker.reset()
        assertEquals(1L, tracker.update(listOf(candidate(timestamp = 300)), 300).events.single().trackId)
    }

    @Test
    fun `long frame gap ends stale track before starting replacement`() {
        val tracker = TemporalChangeTracker(config)
        val first = tracker.update(listOf(candidate(timestamp = 0)), 0).events.single()

        val afterGap = tracker.update(listOf(candidate(timestamp = 200)), 200)

        assertEquals(listOf(TemporalChangePhase.END, TemporalChangePhase.START), afterGap.events.map { it.phase })
        assertEquals(first.trackId, afterGap.events.first().trackId)
        assertTrue(first.trackId != afterGap.events.last().trackId)
    }

    @Test
    fun `iou handles overlap and separation`() {
        assertEquals(
            1f / 7f,
            TemporalChangeTracker.normalizedIou(
                NormalizedRect(0f, 0f, 0.5f, 0.5f),
                NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f),
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            TemporalChangeTracker.normalizedIou(
                NormalizedRect(0f, 0f, 0.2f, 0.2f),
                NormalizedRect(0.8f, 0.8f, 1f, 1f),
            ),
        )
    }

    private fun candidate(
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 0.5f,
        bottom: Float = 0.5f,
        timestamp: Long,
    ) = EventCandidate(
        NormalizedRect(left, top, right, bottom),
        strength = 1f,
        changedAreaRatio = 1f,
        timestampNs = timestamp,
    )
}
