package com.eureka.cyclelens.overlay

import com.eureka.cyclelens.testCardCatalog
import cyclelens.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayQuickCardsTest {
    private val catalog = testCardCatalog()

    @Test
    fun `default quick cards remain the reviewed practical set`() {
        val quickCards = OverlayQuickCards(catalog)

        assertEquals(
            listOf(
                CardId("hog_rider"),
                FIREBALL,
                LOG,
                KNIGHT,
                CardId("skeletons"),
                CardId("ice_spirit"),
                CardId("cannon"),
                CardId("musketeer"),
                CardId("archers"),
                CardId("arrows"),
                CardId("zap"),
                CardId("tesla"),
                CardId("valkyrie"),
                CardId("mini_pekka"),
                CardId("goblin_barrel"),
                CardId("princess"),
            ),
            quickCards.cardIds.value,
        )
    }

    @Test
    fun `configured quick cards keep their configured order`() {
        val quickCards = OverlayQuickCards(
            catalog = catalog,
            initialCardIds = listOf(LOG, FIREBALL, KNIGHT),
        )

        assertEquals(listOf(LOG, FIREBALL, KNIGHT), quickCards.cardIds.value)
    }

    @Test
    fun `duplicate configuration produces one candidate`() {
        val quickCards = OverlayQuickCards(
            catalog = catalog,
            initialCardIds = listOf(FIREBALL, FIREBALL, LOG),
        )

        assertEquals(listOf(FIREBALL, LOG), quickCards.cardIds.value)
    }

    @Test
    fun `selection enforces its configured limit`() {
        val quickCards = OverlayQuickCards(
            catalog = catalog,
            limit = 2,
            initialCardIds = listOf(FIREBALL, LOG),
        )

        assertFalse(quickCards.setSelected(KNIGHT, selected = true))
        assertTrue(quickCards.setSelected(LOG, selected = false))
        assertTrue(quickCards.setSelected(KNIGHT, selected = true))

        assertEquals(listOf(FIREBALL, KNIGHT), quickCards.cardIds.value)
    }

    @Test
    fun `unknown cards are rejected from quick configuration`() {
        assertFailsWith<IllegalArgumentException> {
            OverlayQuickCards(catalog, initialCardIds = listOf(CardId("unknown")))
        }

        val quickCards = OverlayQuickCards(catalog, initialCardIds = emptyList())
        assertFalse(quickCards.setSelected(CardId("unknown"), selected = true))
    }

    @Test
    fun `quick card snapshots cannot mutate internal selection`() {
        val quickCards = OverlayQuickCards(
            catalog = catalog,
            initialCardIds = listOf(FIREBALL),
        )

        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (quickCards.cardIds.value as MutableList<CardId>).add(LOG)
        }
        assertEquals(listOf(FIREBALL), quickCards.cardIds.value)
    }

    private companion object {
        val FIREBALL = CardId("fireball")
        val LOG = CardId("the_log")
        val KNIGHT = CardId("knight")
    }
}
