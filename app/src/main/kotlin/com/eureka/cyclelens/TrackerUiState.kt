package com.eureka.cyclelens

import com.eureka.cyclelens.overlay.OverlayBackgroundOpacity
import com.eureka.cyclelens.overlay.OverlayDetailMode
import com.eureka.cyclelens.overlay.OverlaySizeMode
import cyclelens.core.CardId

data class TrackerUiState(
    val observationCount: Int,
    val deckCapacity: Int,
    val opponentDeck: List<TrackedCardUiState>,
    val pickerCards: List<CardPickerItemUiState>,
    val searchQuery: String,
    val canAddCard: Boolean,
    val canUndo: Boolean,
    val canReset: Boolean,
    val isCardPickerVisible: Boolean,
    val isResetConfirmationVisible: Boolean,
    val quickCardCount: Int,
    val quickCardLimit: Int,
    val quickCardOptions: List<QuickCardOptionUiState>,
    val quickCardSearchQuery: String,
    val isQuickCardEditorVisible: Boolean,
    val overlaySizeMode: OverlaySizeMode,
    val overlayDetailMode: OverlayDetailMode,
    val overlayBackgroundOpacity: OverlayBackgroundOpacity,
)

data class TrackedCardUiState(
    val cardId: CardId,
    val displayName: String,
    val cardsPlayedSince: Int,
    val cardsUntilAvailable: Int,
    val available: Boolean,
)

data class CardPickerItemUiState(
    val cardId: CardId,
    val displayName: String,
)

data class QuickCardOptionUiState(
    val cardId: CardId,
    val displayName: String,
    val selected: Boolean,
    val enabled: Boolean,
)
