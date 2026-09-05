package com.eureka.cyclelens.catalog

import com.eureka.cyclelens.testCardCatalog
import cyclelens.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardCatalogTest {
    @Test
    fun `catalog contains sixteen unique cycle card IDs`() {
        val cards = testCardCatalog().allCycleCards()

        assertEquals(16, cards.size)
        assertEquals(16, cards.map { it.id }.toSet().size)
    }

    @Test
    fun `catalog rejects duplicate canonical card IDs`() {
        val card = definition("fireball")

        assertFailsWith<IllegalArgumentException> {
            CardCatalog(
                definitions = listOf(card, card.copy(displayName = "Duplicate")),
                visualFormDefinitions = listOf(normalForm(card)),
            )
        }
    }

    @Test
    fun `catalog rejects duplicate non-null supercell IDs`() {
        val fireball = definition("fireball").copy(supercellId = 26000001)
        val knight = definition("knight", "Knight").copy(supercellId = 26000001)

        assertFailsWith<IllegalArgumentException> {
            CardCatalog(
                listOf(fireball, knight),
                listOf(normalForm(fireball), normalForm(knight)),
            )
        }
    }

    @Test
    fun `catalog rejects invalid canonical and visual form IDs`() {
        val invalidCard = definition("Fire Ball")
        assertFailsWith<IllegalArgumentException> {
            CardCatalog(listOf(invalidCard), listOf(normalForm(invalidCard)))
        }

        val card = definition("fireball")
        assertFailsWith<IllegalArgumentException> {
            CardCatalog(listOf(card), listOf(normalForm(card).copy(id = "Fireball Normal")))
        }
    }

    @Test
    fun `catalog rejects duplicate visual form IDs`() {
        val card = definition("knight", "Knight")
        val normal = normalForm(card)

        assertFailsWith<IllegalArgumentException> {
            CardCatalog(listOf(card), listOf(normal, normal.copy(form = CardForm.HERO)))
        }
    }

    @Test
    fun `catalog rejects visual forms with an unknown canonical reference`() {
        val card = definition("knight", "Knight")

        assertFailsWith<IllegalArgumentException> {
            CardCatalog(
                listOf(card),
                listOf(normalForm(card).copy(canonicalCardId = CardId("unknown"))),
            )
        }
    }

    @Test
    fun `catalog requires exactly one normal form per canonical card`() {
        val card = definition("knight", "Knight")
        val normal = normalForm(card)

        assertFailsWith<IllegalArgumentException> {
            CardCatalog(listOf(card), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            CardCatalog(
                listOf(card),
                listOf(normal, normal.copy(id = "knight_default")),
            )
        }
    }

    @Test
    fun `normal form display name must match canonical card`() {
        val card = definition("knight", "Knight")

        assertFailsWith<IllegalArgumentException> {
            CardCatalog(
                listOf(card),
                listOf(normalForm(card).copy(displayName = "Different Knight")),
            )
        }
    }

    @Test
    fun `catalog rejects blank names and invalid elixir`() {
        assertInvalidDefinition(definition("fireball").copy(displayName = " "))
        assertInvalidDefinition(definition("fireball").copy(shortName = ""))
        assertInvalidDefinition(definition("fireball").copy(elixir = 0))
        assertInvalidDefinition(definition("fireball").copy(elixir = 11))

        val card = definition("fireball")
        assertFailsWith<IllegalArgumentException> {
            CardCatalog(listOf(card), listOf(normalForm(card).copy(displayName = " ")))
        }
    }

    @Test
    fun `catalog accepts only Supercell keyed icon asset paths`() {
        val card = definition("fireball").copy(
            supercellId = 28000000,
            iconAssetPath = "card-icons/28000000.png",
        )

        assertEquals(card, CardCatalog(listOf(card), listOf(normalForm(card))).allCards().single())
        assertInvalidDefinition(card.copy(iconAssetPath = "icons/28000000.png"))
        assertInvalidDefinition(card.copy(iconAssetPath = "card-icons/26000000.png"))
    }

    @Test
    fun `normal evolution and hero forms canonicalize in one step`() {
        val knight = definition("knight", "Knight")
        val catalog = CardCatalog(
            definitions = listOf(knight),
            visualFormDefinitions = listOf(
                normalForm(knight),
                form("knight_evolution", knight, CardForm.EVOLUTION, "Knight Evolution"),
                form("hero_knight", knight, CardForm.HERO, "Hero Knight"),
            ),
        )

        assertEquals(knight.id, catalog.canonicalizeVisualForm("knight_normal"))
        assertEquals(knight.id, catalog.canonicalizeVisualForm("knight_evolution"))
        assertEquals(knight.id, catalog.canonicalizeVisualForm("hero_knight"))
        assertNull(catalog.canonicalizeVisualForm("unknown_form"))
    }

    @Test
    fun `champion remains its own canonical deck card`() {
        val archerQueen = definition("archer_queen", "Archer Queen")
        val catalog = CardCatalog(listOf(archerQueen), listOf(normalForm(archerQueen)))

        assertEquals(
            CardId("archer_queen"),
            catalog.canonicalizeVisualForm("archer_queen_normal"),
        )
        assertTrue(catalog.isCycleEligible(CardId("archer_queen")))
    }

    @Test
    fun `tower troop is excluded from cycle cards by structured eligibility`() {
        val towerPrincess = definition("tower_princess", "Tower Princess").copy(
            type = CardType.TOWER_TROOP,
            cycleEligible = false,
        )
        val catalog = CardCatalog(listOf(towerPrincess), listOf(normalForm(towerPrincess)))

        assertEquals(towerPrincess, catalog.card(towerPrincess.id))
        assertEquals(towerPrincess.id, catalog.canonicalizeVisualForm("tower_princess_normal"))
        assertFalse(catalog.isCycleEligible(towerPrincess.id))
        assertTrue(catalog.allCycleCards().isEmpty())
    }

    @Test
    fun `catalog rejects a cycle eligible tower troop`() {
        val invalidTowerPrincess = definition("tower_princess", "Tower Princess").copy(
            type = CardType.TOWER_TROOP,
            cycleEligible = true,
        )

        assertFailsWith<IllegalArgumentException> {
            CardCatalog(
                listOf(invalidTowerPrincess),
                listOf(normalForm(invalidTowerPrincess)),
            )
        }
    }

    @Test
    fun `catalog snapshots cannot mutate internal state`() {
        val sourceCards = mutableListOf(definition("fireball"))
        val sourceForms = mutableListOf(normalForm(sourceCards.single()))
        val catalog = CardCatalog(sourceCards, sourceForms)

        sourceCards.clear()
        sourceForms.clear()

        assertEquals(listOf(CardId("fireball")), catalog.allCards().map { it.id })
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (catalog.allCards() as MutableList<CardDefinition>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (catalog.allCycleCards() as MutableList<CardDefinition>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (catalog.allVisualForms() as MutableList<VisualFormDefinition>).clear()
        }
    }

    private fun assertInvalidDefinition(definition: CardDefinition) {
        assertFailsWith<IllegalArgumentException> {
            CardCatalog(listOf(definition), listOf(normalForm(definition)))
        }
    }

    private fun definition(
        id: String,
        displayName: String = "Fireball",
    ) = CardDefinition(
        id = CardId(id),
        supercellId = null,
        displayName = displayName,
        shortName = displayName,
        type = CardType.SPELL,
        elixir = 4,
        cycleEligible = true,
        iconAssetPath = null,
    )

    private fun normalForm(card: CardDefinition) = form(
        id = "${card.id.value}_normal",
        card = card,
        form = CardForm.NORMAL,
        displayName = card.displayName,
    )

    private fun form(
        id: String,
        card: CardDefinition,
        form: CardForm,
        displayName: String,
    ) = VisualFormDefinition(
        id = id,
        canonicalCardId = card.id,
        form = form,
        displayName = displayName,
    )
}
