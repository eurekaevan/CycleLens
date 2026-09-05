package com.eureka.cyclelens

import com.eureka.cyclelens.catalog.CardCatalog
import com.eureka.cyclelens.catalog.CardDefinition
import com.eureka.cyclelens.catalog.CardForm
import com.eureka.cyclelens.catalog.VisualFormDefinition
import cyclelens.core.CardId

fun testCardCatalog(): CardCatalog {
    val cards = listOf(
        card("hog_rider", "Hog Rider", "Hog"),
        card("fireball", "Fireball", "Fire"),
        card("the_log", "The Log", "Log"),
        card("knight", "Knight", "Knight"),
        card("skeletons", "Skeletons", "Skels"),
        card("ice_spirit", "Ice Spirit", "Spirit"),
        card("cannon", "Cannon", "Cannon"),
        card("musketeer", "Musketeer", "Musket"),
        card("archers", "Archers", "Archers"),
        card("arrows", "Arrows", "Arrows"),
        card("zap", "Zap", "Zap"),
        card("tesla", "Tesla", "Tesla"),
        card("valkyrie", "Valkyrie", "Valk"),
        card("mini_pekka", "Mini P.E.K.K.A", "Mini P"),
        card("goblin_barrel", "Goblin Barrel", "Barrel"),
        card("princess", "Princess", "Princess"),
    )
    return CardCatalog(
        definitions = cards,
        visualFormDefinitions = cards.map { card ->
            VisualFormDefinition(
                id = "${card.id.value}_normal",
                canonicalCardId = card.id,
                form = CardForm.NORMAL,
                displayName = card.displayName,
            )
        },
    )
}

private fun card(
    id: String,
    displayName: String,
    shortName: String,
) = CardDefinition(
    id = CardId(id),
    supercellId = null,
    displayName = displayName,
    shortName = shortName,
    type = null,
    elixir = null,
    cycleEligible = true,
    iconAssetPath = null,
)
