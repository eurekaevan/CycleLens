package com.eureka.cyclelens.catalog

import cyclelens.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CardCatalogJsonParserTest {
    @Test
    fun `valid JSON parses canonical cards and visual forms`() {
        val catalog = CardCatalogJsonParser.parse(
            catalogJson(
                cardJson(supercellId = "26000000", type = "TROOP", elixir = "3"),
                visualFormJson(),
            ),
        )

        val card = catalog.card(CardId("knight"))
        requireNotNull(card)
        assertEquals(26000000L, card.supercellId)
        assertEquals("Knight", card.displayName)
        assertEquals(CardType.TROOP, card.type)
        assertEquals(3, card.elixir)
        assertEquals(true, card.cycleEligible)
        assertEquals("card-icons/26000000.png", card.iconAssetPath)
        assertEquals(CardForm.NORMAL, catalog.visualForm("knight_normal")?.form)
    }

    @Test
    fun `nullable canonical metadata is accepted`() {
        val card = CardCatalogJsonParser.parse(
            catalogJson(cardJson(), visualFormJson()),
        ).allCards().single()

        assertNull(card.supercellId)
        assertNull(card.type)
        assertNull(card.elixir)
    }

    @Test
    fun `duplicate canonical and supercell IDs are rejected`() {
        val duplicateCard = cardJson()
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson("$duplicateCard,$duplicateCard", visualFormJson()),
            )
        }

        val fireball = cardJson(
            id = "fireball",
            displayName = "Fireball",
            supercellId = "26000001",
        )
        val knight = cardJson(supercellId = "26000001")
        val forms = listOf(
            visualFormJson("fireball_normal", "fireball", "Fireball"),
            visualFormJson(),
        ).joinToString()
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(catalogJson("$fireball,$knight", forms))
        }
    }

    @Test
    fun `duplicate visual form IDs and normal forms are rejected`() {
        val normal = visualFormJson()
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(catalogJson(cardJson(), "$normal,$normal"))
        }

        val secondNormal = visualFormJson(id = "knight_default")
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(catalogJson(cardJson(), "$normal,$secondNormal"))
        }
    }

    @Test
    fun `visual form with unknown canonical reference is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(
                    cardJson(),
                    visualFormJson(canonicalCardId = "unknown"),
                ),
            )
        }
    }

    @Test
    fun `invalid snake case and blank names are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(cardJson(id = "Hero Knight"), visualFormJson()),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(cardJson(displayName = ""), visualFormJson()),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(cardJson(shortName = " "), visualFormJson()),
            )
        }
    }

    @Test
    fun `unknown card type and visual form enum are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(cardJson(type = "CHAMPION"), visualFormJson()),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(cardJson(), visualFormJson(form = "CHAMPION")),
            )
        }
    }

    @Test
    fun `invalid elixir is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(cardJson(elixir = "11"), visualFormJson()),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(cardJson(elixir = "4.5"), visualFormJson()),
            )
        }
    }

    @Test
    fun `missing cycle eligibility is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(cardJson(includeCycleEligible = false), visualFormJson()),
            )
        }
    }

    @Test
    fun `tower troop cannot be cycle eligible`() {
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(
                    cardJson(type = "TOWER_TROOP", cycleEligible = true),
                    visualFormJson(),
                ),
            )
        }
    }

    @Test
    fun `normal form must agree with canonical display name`() {
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse(
                catalogJson(cardJson(), visualFormJson(displayName = "Different Knight")),
            )
        }
    }

    @Test
    fun `malformed JSON is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse("{\"cards\":[")
        }
        assertFailsWith<IllegalArgumentException> {
            CardCatalogJsonParser.parse("[]")
        }
    }

    private fun catalogJson(cards: String, visualForms: String): String =
        """{"cards":[$cards],"visualForms":[$visualForms]}"""

    private fun cardJson(
        id: String = "knight",
        displayName: String = "Knight",
        shortName: String = "Knight",
        supercellId: String = "null",
        type: String? = null,
        elixir: String = "null",
        cycleEligible: Boolean = true,
        includeCycleEligible: Boolean = true,
        iconAssetPath: String? = if (supercellId == "null") null else "card-icons/$supercellId.png",
    ): String {
        val typeValue = type?.let { "\"$it\"" } ?: "null"
        val eligibilityField = if (includeCycleEligible) {
            "\"cycleEligible\":$cycleEligible,"
        } else {
            ""
        }
        val iconValue = iconAssetPath?.let { "\"$it\"" } ?: "null"
        return """
            {
              "id":"$id",
              "supercellId":$supercellId,
              "displayName":"$displayName",
              "shortName":"$shortName",
              "type":$typeValue,
              "elixir":$elixir,
              $eligibilityField
              "iconAssetPath":$iconValue
            }
        """.trimIndent()
    }

    private fun visualFormJson(
        id: String = "knight_normal",
        canonicalCardId: String = "knight",
        displayName: String = "Knight",
        form: String = "NORMAL",
    ): String = """
        {
          "id":"$id",
          "canonicalCardId":"$canonicalCardId",
          "form":"$form",
          "displayName":"$displayName"
        }
    """.trimIndent()
}
