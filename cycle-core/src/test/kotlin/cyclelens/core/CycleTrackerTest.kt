package cyclelens.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleTrackerTest {
    @Test
    fun `observed cards are discovered`() {
        val tracker = CycleTracker()

        tracker.observe(HOG)
        tracker.observe(LOG)

        assertEquals(setOf(HOG, LOG), tracker.discoveredCards())
    }

    @Test
    fun `observing a card again does not duplicate its discovery`() {
        val tracker = CycleTracker()

        tracker.observe(HOG)
        tracker.observe(LOG)
        tracker.observe(HOG)

        assertEquals(2, tracker.discoveredCards().size)
        assertEquals(setOf(HOG, LOG), tracker.discoveredCards())
    }

    @Test
    fun `cards played since counts observations after each card`() {
        val tracker = CycleTracker()

        tracker.observe(FIREBALL)
        tracker.observe(KNIGHT)
        tracker.observe(LOG)

        assertEquals(2, tracker.cardsPlayedSince(FIREBALL))
        assertEquals(1, tracker.cardsPlayedSince(KNIGHT))
        assertEquals(0, tracker.cardsPlayedSince(LOG))
    }

    @Test
    fun `a card becomes available after four subsequent observations`() {
        val tracker = CycleTracker()

        tracker.observe(FIREBALL)
        tracker.observe(KNIGHT)
        tracker.observe(LOG)
        tracker.observe(SKELETONS)
        tracker.observe(ICE_SPIRIT)

        assertEquals(0, tracker.cardsUntilAvailable(FIREBALL))
    }

    @Test
    fun `available distance never drops below zero`() {
        val tracker = CycleTracker()

        tracker.observe(FIREBALL)
        tracker.observe(KNIGHT)
        tracker.observe(LOG)
        tracker.observe(SKELETONS)
        tracker.observe(ICE_SPIRIT)
        tracker.observe(HOG)

        assertEquals(0, tracker.cardsUntilAvailable(FIREBALL))
    }

    @Test
    fun `a card reports the remaining observations before its cycle completes`() {
        val tracker = CycleTracker()

        tracker.observe(FIREBALL)
        tracker.observe(KNIGHT)
        tracker.observe(LOG)

        assertEquals(2, tracker.cardsUntilAvailable(FIREBALL))
    }

    @Test
    fun `observing a card again restarts its cycle count`() {
        val tracker = CycleTracker()

        tracker.observe(FIREBALL)
        tracker.observe(KNIGHT)
        tracker.observe(LOG)
        tracker.observe(SKELETONS)
        tracker.observe(ICE_SPIRIT)
        tracker.observe(FIREBALL)
        tracker.observe(KNIGHT)

        assertEquals(1, tracker.cardsPlayedSince(FIREBALL))
        assertEquals(3, tracker.cardsUntilAvailable(FIREBALL))
    }

    @Test
    fun `an unobserved card has no cycle information`() {
        val tracker = CycleTracker()

        assertNull(tracker.cardsPlayedSince(UNKNOWN))
        assertNull(tracker.cardsUntilAvailable(UNKNOWN))
    }

    @Test
    fun `reset removes discoveries and cycle information`() {
        val tracker = CycleTracker()
        tracker.observe(FIREBALL)
        tracker.observe(KNIGHT)

        tracker.reset()

        assertTrue(tracker.discoveredCards().isEmpty())
        assertNull(tracker.cardsPlayedSince(FIREBALL))
        assertNull(tracker.cardsUntilAvailable(FIREBALL))
        assertNull(tracker.cardsPlayedSince(KNIGHT))
        assertNull(tracker.cardsUntilAvailable(KNIGHT))
    }

    @Test
    fun `custom rules determine how many cards complete a cycle`() {
        val threeCardCycle = object : CycleRules {
            override val handSize: Int = 3
            override val deckSize: Int = 6
            override val cardsRequiredToCycle: Int = 3
        }
        val tracker = CycleTracker(threeCardCycle)

        tracker.observe(FIREBALL)
        tracker.observe(KNIGHT)
        tracker.observe(LOG)

        assertEquals(1, tracker.cardsUntilAvailable(FIREBALL))

        tracker.observe(SKELETONS)

        assertEquals(0, tracker.cardsUntilAvailable(FIREBALL))
    }

    @Test
    fun `discovered cards are returned as a snapshot`() {
        val tracker = CycleTracker()
        tracker.observe(HOG)

        val discoveredBeforeNextObservation = tracker.discoveredCards()
        tracker.observe(LOG)

        assertEquals(setOf(HOG), discoveredBeforeNextObservation)
        assertEquals(setOf(HOG, LOG), tracker.discoveredCards())
    }

    private companion object {
        val HOG = CardId("Hog")
        val LOG = CardId("Log")
        val FIREBALL = CardId("Fireball")
        val KNIGHT = CardId("Knight")
        val SKELETONS = CardId("Skeletons")
        val ICE_SPIRIT = CardId("IceSpirit")
        val UNKNOWN = CardId("Unknown")
    }
}
