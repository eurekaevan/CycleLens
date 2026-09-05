package com.eureka.cyclelens.session

import cyclelens.core.CardId

data class MatchSnapshot(
    val observationCount: Int,
    val deckCapacity: Int,
    val cards: List<TrackedCard>,
    val canUndo: Boolean,
)

data class TrackedCard(
    val id: CardId,
    val cardsPlayedSince: Int,
    val cardsUntilAvailable: Int,
)
