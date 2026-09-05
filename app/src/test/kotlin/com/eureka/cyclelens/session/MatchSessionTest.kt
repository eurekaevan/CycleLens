package com.eureka.cyclelens.session

import cyclelens.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchSessionTest {
    @Test
    fun `observe publishes cycle state`() {
        val session = MatchSession()

        session.observe(FIREBALL)
        session.observe(KNIGHT)
        session.observe(LOG)

        val snapshot = session.snapshot.value
        assertEquals(3, snapshot.observationCount)
        assertEquals(listOf(FIREBALL, KNIGHT, LOG), snapshot.cards.map { it.id })
        assertEquals(2, snapshot.cards.first().cardsPlayedSince)
        assertEquals(2, snapshot.cards.first().cardsUntilAvailable)
        assertTrue(snapshot.canUndo)
    }

    @Test
    fun `repeated observations update a card without changing deck order`() {
        val session = MatchSession()
        session.observe(FIREBALL)
        session.observe(KNIGHT)

        assertTrue(session.observe(FIREBALL))

        val snapshot = session.snapshot.value
        assertEquals(listOf(FIREBALL, KNIGHT), snapshot.cards.map { it.id })
        assertEquals(3, snapshot.observationCount)
        assertEquals(0, snapshot.cards.first().cardsPlayedSince)
    }

    @Test
    fun `undo restores the previous snapshot`() {
        val session = MatchSession()
        session.observe(FIREBALL)
        session.observe(KNIGHT)

        assertEquals(KNIGHT, session.undo())

        val snapshot = session.snapshot.value
        assertEquals(listOf(FIREBALL), snapshot.cards.map { it.id })
        assertEquals(1, snapshot.observationCount)
        assertEquals(0, snapshot.cards.single().cardsPlayedSince)
    }

    @Test
    fun `undo on an empty session returns null without changing state`() {
        val session = MatchSession()

        assertNull(session.undo())

        assertEquals(emptySnapshot(), session.snapshot.value)
    }

    @Test
    fun `reset clears the shared match`() {
        val session = MatchSession()
        session.observe(FIREBALL)
        session.observe(KNIGHT)

        session.reset()

        assertEquals(emptySnapshot(), session.snapshot.value)
    }

    @Test
    fun `session rejects a ninth distinct card but still accepts known cards`() {
        val session = MatchSession()
        FIRST_EIGHT.forEach { assertTrue(session.observe(it)) }

        assertFalse(session.observe(NINTH))
        assertTrue(session.observe(FIREBALL))

        val snapshot = session.snapshot.value
        assertEquals(FIRST_EIGHT, snapshot.cards.map { it.id })
        assertEquals(9, snapshot.observationCount)
    }

    private fun emptySnapshot(): MatchSnapshot = MatchSnapshot(
        observationCount = 0,
        deckCapacity = 8,
        cards = emptyList(),
        canUndo = false,
    )

    private companion object {
        val FIREBALL = CardId("fireball")
        val KNIGHT = CardId("knight")
        val LOG = CardId("the_log")
        val NINTH = CardId("archers")
        val FIRST_EIGHT = listOf(
            FIREBALL,
            KNIGHT,
            LOG,
            CardId("skeletons"),
            CardId("ice_spirit"),
            CardId("cannon"),
            CardId("musketeer"),
            CardId("hog_rider"),
        )
    }
}
