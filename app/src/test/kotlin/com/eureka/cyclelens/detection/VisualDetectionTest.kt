package com.eureka.cyclelens.detection

import com.eureka.cyclelens.catalog.CardCatalog
import com.eureka.cyclelens.catalog.CardDefinition
import com.eureka.cyclelens.catalog.CardForm
import com.eureka.cyclelens.catalog.CardType
import com.eureka.cyclelens.catalog.VisualFormDefinition
import com.eureka.cyclelens.session.MatchSession
import cyclelens.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisualDetectionTest {
    @Test
    fun `visual detection can canonicalize without observing a match`() {
        val catalog = catalog()
        val session = MatchSession()
        val detection = VisualDetection(
            visualFormId = "hero_knight",
            confidence = 0.95f,
            timestampMillis = 1_000L,
        )

        val canonicalCardId = catalog.canonicalizeVisualForm(detection.visualFormId)

        assertEquals(CardId("knight"), canonicalCardId)
        assertEquals(0, session.snapshot.value.observationCount)
    }

    @Test
    fun `tower troop detection is rejected before match observation`() {
        val catalog = catalog()
        val session = MatchSession()
        val detection = VisualDetection(
            visualFormId = "tower_princess_normal",
            confidence = 0.99f,
            timestampMillis = 2_000L,
        )

        val cycleCardId = catalog.canonicalizeVisualForm(detection.visualFormId)
            ?.takeIf(catalog::isCycleEligible)
        cycleCardId?.let { cardId -> session.observe(cardId) }

        assertNull(cycleCardId)
        assertEquals(0, session.snapshot.value.observationCount)
    }

    private fun catalog(): CardCatalog {
        val knight = card("knight", "Knight", cycleEligible = true)
        val towerPrincess = card(
            "tower_princess",
            "Tower Princess",
            cycleEligible = false,
            type = CardType.TOWER_TROOP,
        )
        return CardCatalog(
            definitions = listOf(knight, towerPrincess),
            visualFormDefinitions = listOf(
                form("knight_normal", knight, CardForm.NORMAL, "Knight"),
                form("hero_knight", knight, CardForm.HERO, "Hero Knight"),
                form(
                    "tower_princess_normal",
                    towerPrincess,
                    CardForm.NORMAL,
                    "Tower Princess",
                ),
            ),
        )
    }

    private fun card(
        id: String,
        displayName: String,
        cycleEligible: Boolean,
        type: CardType? = null,
    ) = CardDefinition(
        id = CardId(id),
        supercellId = null,
        displayName = displayName,
        shortName = displayName,
        type = type,
        elixir = null,
        cycleEligible = cycleEligible,
        iconAssetPath = null,
    )

    private fun form(
        id: String,
        card: CardDefinition,
        form: CardForm,
        displayName: String,
    ) = VisualFormDefinition(id, card.id, form, displayName)
}
