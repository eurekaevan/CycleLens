package com.eureka.cyclelens.overlay

import com.eureka.cyclelens.catalog.CardCatalog
import com.eureka.cyclelens.catalog.CardDefinition
import com.eureka.cyclelens.session.MatchSnapshot
import cyclelens.core.CardId
import java.util.Collections

enum class OverlayPanel {
    EXPANDED,
    COLLAPSED,
    PICKER,
}

data class OverlayPresentationState(
    val panel: OverlayPanel,
    val cards: List<OverlayCardUiState>,
    val pickerCards: List<OverlayPickerCardUiState>,
    val canAddCard: Boolean,
    val canUndo: Boolean,
    val sizeMode: OverlaySizeMode,
    val detailMode: OverlayDetailMode,
    val backgroundOpacity: OverlayBackgroundOpacity,
)

data class OverlayCardUiState(
    val cardId: CardId,
    val displayName: String,
    val shortName: String?,
    val cycleLabel: String,
    val available: Boolean,
)

data class OverlayPickerCardUiState(
    val cardId: CardId,
    val displayName: String,
    val shortName: String,
)

class OverlayPresentationMapper(
    private val catalog: CardCatalog,
) {
    fun map(
        matchSnapshot: MatchSnapshot,
        panel: OverlayPanel,
        quickCardIds: List<CardId>,
        appearance: OverlayAppearance,
    ): OverlayPresentationState {
        val discoveredCardIds = matchSnapshot.cards.mapTo(mutableSetOf()) { card -> card.id }
        val cards = matchSnapshot.cards.map { trackedCard ->
            val definition = checkNotNull(catalog[trackedCard.id]) {
                "Observed card is missing from the catalog: ${trackedCard.id.value}"
            }
            check(definition.cycleEligible) {
                "Non-cycle card was observed: ${trackedCard.id.value}"
            }
            val available = trackedCard.cardsUntilAvailable == 0

            OverlayCardUiState(
                cardId = trackedCard.id,
                displayName = definition.displayName,
                shortName = definition.shortName.takeIf {
                    appearance.detailMode == OverlayDetailMode.FULL
                },
                cycleLabel = if (available) {
                    AVAILABLE_LABEL
                } else {
                    trackedCard.cardsUntilAvailable.toString()
                },
                available = available,
            )
        }
        val pickerCards = quickCardIds
            .asSequence()
            .distinct()
            .map { cardId ->
                checkNotNull(catalog[cardId]) {
                    "Quick card is missing from the catalog: ${cardId.value}"
                }
            }
            .filter(CardDefinition::cycleEligible)
            .filterNot { definition -> definition.id in discoveredCardIds }
            .map { definition ->
                OverlayPickerCardUiState(
                    cardId = definition.id,
                    displayName = definition.displayName,
                    shortName = definition.shortName,
                )
            }
            .toList()

        return OverlayPresentationState(
            panel = panel,
            cards = Collections.unmodifiableList(cards),
            pickerCards = Collections.unmodifiableList(pickerCards),
            canAddCard = cards.size < matchSnapshot.deckCapacity && pickerCards.isNotEmpty(),
            canUndo = matchSnapshot.canUndo,
            sizeMode = appearance.sizeMode,
            detailMode = appearance.detailMode,
            backgroundOpacity = appearance.backgroundOpacity,
        )
    }

    private companion object {
        const val AVAILABLE_LABEL = "✓"
    }
}

class OverlayPresentationController(
    private val mapper: OverlayPresentationMapper,
) {
    var panel: OverlayPanel = OverlayPanel.EXPANDED
        private set

    fun stateFor(
        matchSnapshot: MatchSnapshot,
        quickCardIds: List<CardId>,
        appearance: OverlayAppearance,
    ): OverlayPresentationState {
        val state = mapper.map(matchSnapshot, panel, quickCardIds, appearance)
        if (panel == OverlayPanel.PICKER && !state.canAddCard) {
            panel = OverlayPanel.EXPANDED
            return mapper.map(matchSnapshot, panel, quickCardIds, appearance)
        }
        return state
    }

    fun expand() {
        panel = OverlayPanel.EXPANDED
    }

    fun collapse() {
        panel = OverlayPanel.COLLAPSED
    }

    fun openPicker(
        matchSnapshot: MatchSnapshot,
        quickCardIds: List<CardId>,
        appearance: OverlayAppearance,
    ): Boolean {
        if (!mapper.map(matchSnapshot, panel, quickCardIds, appearance).canAddCard) {
            return false
        }
        panel = OverlayPanel.PICKER
        return true
    }
}
