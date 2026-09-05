package com.eureka.cyclelens.catalog

import cyclelens.core.CardId

data class CardDefinition(
    val id: CardId,
    val supercellId: Long?,
    val displayName: String,
    val shortName: String,
    val type: CardType?,
    val elixir: Int?,
    val cycleEligible: Boolean,
    val iconAssetPath: String?,
)

enum class CardType {
    TROOP,
    SPELL,
    BUILDING,
    TOWER_TROOP,
}
