package com.eureka.cyclelens.catalog

import cyclelens.core.CardId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardCatalogAssetTest {
    @Test
    fun `packaged catalog parses as the canonical runtime schema`() {
        val catalog = File("src/main/assets/cards.json").reader().use { reader ->
            CardCatalogJsonParser.parse(reader)
        }

        assertEquals(126, catalog.allCards().size)
        assertEquals(122, catalog.allCycleCards().size)
        assertEquals(184, catalog.allVisualForms().size)
        assertEquals(42, catalog.allVisualForms().count { form -> form.form == CardForm.EVOLUTION })
        assertEquals(16, catalog.allVisualForms().count { form -> form.form == CardForm.HERO })
        assertEquals(4, catalog.allCards().count { card -> card.type == CardType.TOWER_TROOP })
        assertEquals(catalog.allCards().size, catalog.allCards().map { card -> card.id }.toSet().size)
        assertEquals(CardId("knight"), catalog.canonicalizeVisualForm("knight_evolution"))
        assertEquals(CardId("knight"), catalog.canonicalizeVisualForm("hero_knight"))
        assertEquals(
            CardId("archer_queen"),
            catalog.canonicalizeVisualForm("archer_queen_normal"),
        )
        assertFalse(catalog.isCycleEligible(CardId("tower_princess")))
        assertTrue(catalog.allCycleCards().none { card -> card.type == CardType.TOWER_TROOP })
    }
}
