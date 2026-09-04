package com.eureka.cyclelens

import cyclelens.core.CardId

data class TrackerUiState(
    val input: String = "",
    val observations: Int = 0,
    val cards: List<CardUiState> = emptyList(),
    val canUndo: Boolean = false,
)

data class CardUiState(
    val cardId: CardId,
    val cardsPlayedSince: Int,
    val cardsUntilAvailable: Int,
    val available: Boolean,
)
