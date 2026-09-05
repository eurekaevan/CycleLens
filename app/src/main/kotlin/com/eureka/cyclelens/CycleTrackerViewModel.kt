package com.eureka.cyclelens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eureka.cyclelens.catalog.CardCatalog
import com.eureka.cyclelens.overlay.OverlayAppearance
import com.eureka.cyclelens.overlay.OverlayBackgroundOpacity
import com.eureka.cyclelens.overlay.OverlayConfiguration
import com.eureka.cyclelens.overlay.OverlayDetailMode
import com.eureka.cyclelens.overlay.OverlayQuickCards
import com.eureka.cyclelens.overlay.OverlaySizeMode
import com.eureka.cyclelens.session.MatchSession
import com.eureka.cyclelens.session.MatchSnapshot
import cyclelens.core.CardId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CycleTrackerViewModel internal constructor(
    private val matchSession: MatchSession,
    private val catalog: CardCatalog,
    private val overlayQuickCards: OverlayQuickCards,
    private val overlayConfiguration: OverlayConfiguration,
    observationScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        createUiState(
            matchSnapshot = matchSession.snapshot.value,
            searchQuery = "",
            isCardPickerVisible = false,
            isResetConfirmationVisible = false,
            quickCardIds = overlayQuickCards.cardIds.value,
            quickCardSearchQuery = "",
            isQuickCardEditorVisible = false,
            overlayAppearance = overlayConfiguration.appearance.value,
        ),
    )
    val uiState: StateFlow<TrackerUiState> = mutableUiState.asStateFlow()

    private val stateObservationJobs: List<Job> = (observationScope ?: viewModelScope).let { scope ->
        listOf(
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                matchSession.snapshot.collect { matchSnapshot ->
                    publish(matchSnapshot = matchSnapshot)
                }
            },
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                overlayQuickCards.cardIds.collect { quickCardIds ->
                    publish(quickCardIds = quickCardIds)
                }
            },
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                overlayConfiguration.appearance.collect { overlayAppearance ->
                    publish(overlayAppearance = overlayAppearance)
                }
            },
        )
    }

    fun openCardPicker() {
        if (mutableUiState.value.canAddCard) {
            publish(searchQuery = "", isCardPickerVisible = true)
        }
    }

    fun dismissCardPicker() {
        publish(searchQuery = "", isCardPickerVisible = false)
    }

    fun onSearchQueryChanged(query: String) {
        publish(searchQuery = query)
    }

    fun openQuickCardEditor() {
        publish(quickCardSearchQuery = "", isQuickCardEditorVisible = true)
    }

    fun dismissQuickCardEditor() {
        publish(quickCardSearchQuery = "", isQuickCardEditorVisible = false)
    }

    fun onQuickCardSearchQueryChanged(query: String) {
        publish(quickCardSearchQuery = query)
    }

    fun toggleQuickCard(cardId: CardId) {
        val selected = cardId in overlayQuickCards.cardIds.value
        overlayQuickCards.setSelected(cardId, selected = !selected)
    }

    fun setOverlaySizeMode(sizeMode: OverlaySizeMode) {
        overlayConfiguration.setSizeMode(sizeMode)
    }

    fun setOverlayDetailMode(detailMode: OverlayDetailMode) {
        overlayConfiguration.setDetailMode(detailMode)
    }

    fun setOverlayBackgroundOpacity(backgroundOpacity: OverlayBackgroundOpacity) {
        overlayConfiguration.setBackgroundOpacity(backgroundOpacity)
    }

    fun selectNewCard(cardId: CardId) {
        val state = mutableUiState.value
        if (
            !state.canAddCard ||
            state.opponentDeck.any { card -> card.cardId == cardId } ||
            !catalog.isCycleEligible(cardId)
        ) {
            return
        }

        if (matchSession.observe(cardId)) {
            publish(searchQuery = "", isCardPickerVisible = false)
        }
    }

    fun observeTrackedCard(cardId: CardId) {
        if (matchSession.snapshot.value.cards.none { card -> card.id == cardId }) {
            return
        }

        matchSession.observe(cardId)
    }

    fun undo() {
        matchSession.undo()
    }

    fun requestReset() {
        if (mutableUiState.value.canReset) {
            publish(isResetConfirmationVisible = true)
        }
    }

    fun dismissResetConfirmation() {
        publish(isResetConfirmationVisible = false)
    }

    fun confirmReset() {
        if (!mutableUiState.value.isResetConfirmationVisible) {
            return
        }

        matchSession.reset()
        publish(
            searchQuery = "",
            isCardPickerVisible = false,
            isResetConfirmationVisible = false,
        )
    }

    override fun onCleared() {
        stateObservationJobs.forEach(Job::cancel)
    }

    private fun publish(
        matchSnapshot: MatchSnapshot = matchSession.snapshot.value,
        searchQuery: String = mutableUiState.value.searchQuery,
        isCardPickerVisible: Boolean = mutableUiState.value.isCardPickerVisible,
        isResetConfirmationVisible: Boolean = mutableUiState.value.isResetConfirmationVisible,
        quickCardIds: List<CardId> = overlayQuickCards.cardIds.value,
        quickCardSearchQuery: String = mutableUiState.value.quickCardSearchQuery,
        isQuickCardEditorVisible: Boolean = mutableUiState.value.isQuickCardEditorVisible,
        overlayAppearance: OverlayAppearance = overlayConfiguration.appearance.value,
    ) {
        mutableUiState.value = createUiState(
            matchSnapshot = matchSnapshot,
            searchQuery = searchQuery,
            isCardPickerVisible = isCardPickerVisible,
            isResetConfirmationVisible = isResetConfirmationVisible,
            quickCardIds = quickCardIds,
            quickCardSearchQuery = quickCardSearchQuery,
            isQuickCardEditorVisible = isQuickCardEditorVisible,
            overlayAppearance = overlayAppearance,
        )
    }

    private fun createUiState(
        matchSnapshot: MatchSnapshot,
        searchQuery: String,
        isCardPickerVisible: Boolean,
        isResetConfirmationVisible: Boolean,
        quickCardIds: List<CardId>,
        quickCardSearchQuery: String,
        isQuickCardEditorVisible: Boolean,
        overlayAppearance: OverlayAppearance,
    ): TrackerUiState {
        val discoveredCardIdSet = matchSnapshot.cards.mapTo(mutableSetOf()) { card -> card.id }
        val normalizedQuery = searchQuery.trim()
        val normalizedQuickCardQuery = quickCardSearchQuery.trim()
        val canAddCard = matchSnapshot.cards.size < matchSnapshot.deckCapacity

        val opponentDeck = matchSnapshot.cards.map { trackedCard ->
            val definition = checkNotNull(catalog[trackedCard.id]) {
                "Observed card is missing from the catalog: ${trackedCard.id.value}"
            }

            TrackedCardUiState(
                cardId = trackedCard.id,
                displayName = definition.displayName,
                cardsPlayedSince = trackedCard.cardsPlayedSince,
                cardsUntilAvailable = trackedCard.cardsUntilAvailable,
                available = trackedCard.cardsUntilAvailable == 0,
            )
        }

        val pickerCards = catalog.allCycleCards()
            .asSequence()
            .filterNot { definition -> definition.id in discoveredCardIdSet }
            .filter { definition ->
                normalizedQuery.isEmpty() ||
                    definition.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    definition.id.value.contains(normalizedQuery, ignoreCase = true)
            }
            .map { definition ->
                CardPickerItemUiState(
                    cardId = definition.id,
                    displayName = definition.displayName,
                )
            }
            .toList()

        val quickCardIdSet = quickCardIds.toSet()
        val quickCardOptions = catalog.allCycleCards()
            .asSequence()
            .filter { definition ->
                normalizedQuickCardQuery.isEmpty() ||
                    definition.displayName.contains(normalizedQuickCardQuery, ignoreCase = true) ||
                    definition.shortName.contains(normalizedQuickCardQuery, ignoreCase = true) ||
                    definition.id.value.contains(normalizedQuickCardQuery, ignoreCase = true)
            }
            .map { definition ->
                val selected = definition.id in quickCardIdSet
                QuickCardOptionUiState(
                    cardId = definition.id,
                    displayName = definition.displayName,
                    selected = selected,
                    enabled = selected || quickCardIds.size < overlayQuickCards.limit,
                )
            }
            .toList()

        return TrackerUiState(
            observationCount = matchSnapshot.observationCount,
            deckCapacity = matchSnapshot.deckCapacity,
            opponentDeck = opponentDeck,
            pickerCards = pickerCards,
            searchQuery = searchQuery,
            canAddCard = canAddCard,
            canUndo = matchSnapshot.canUndo,
            canReset = matchSnapshot.observationCount > 0,
            isCardPickerVisible = isCardPickerVisible && canAddCard,
            isResetConfirmationVisible =
                isResetConfirmationVisible && matchSnapshot.observationCount > 0,
            quickCardCount = quickCardIds.size,
            quickCardLimit = overlayQuickCards.limit,
            quickCardOptions = quickCardOptions,
            quickCardSearchQuery = quickCardSearchQuery,
            isQuickCardEditorVisible = isQuickCardEditorVisible,
            overlaySizeMode = overlayAppearance.sizeMode,
            overlayDetailMode = overlayAppearance.detailMode,
            overlayBackgroundOpacity = overlayAppearance.backgroundOpacity,
        )
    }

    class Factory(
        private val matchSession: MatchSession,
        private val catalog: CardCatalog,
        private val overlayQuickCards: OverlayQuickCards,
        private val overlayConfiguration: OverlayConfiguration,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CycleTrackerViewModel::class.java)) {
                "Unsupported ViewModel class: ${modelClass.name}"
            }

            @Suppress("UNCHECKED_CAST")
            return CycleTrackerViewModel(
                matchSession = matchSession,
                catalog = catalog,
                overlayQuickCards = overlayQuickCards,
                overlayConfiguration = overlayConfiguration,
            ) as T
        }
    }
}
