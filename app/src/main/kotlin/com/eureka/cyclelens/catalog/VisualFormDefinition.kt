package com.eureka.cyclelens.catalog

import cyclelens.core.CardId

data class VisualFormDefinition(
    val id: String,
    val canonicalCardId: CardId,
    val form: CardForm,
    val displayName: String,
)

enum class CardForm {
    NORMAL,
    EVOLUTION,
    HERO,
}
