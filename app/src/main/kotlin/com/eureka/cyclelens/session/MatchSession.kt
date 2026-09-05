package com.eureka.cyclelens.session

import cyclelens.core.CardId
import cyclelens.core.CycleTracker
import cyclelens.core.StandardCycleRules
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MatchSession(
    private val tracker: CycleTracker = CycleTracker(),
    val deckCapacity: Int = StandardCycleRules.deckSize,
) {
    private val mutableSnapshot: MutableStateFlow<MatchSnapshot>

    val snapshot: StateFlow<MatchSnapshot>
        get() = mutableSnapshot.asStateFlow()

    init {
        require(deckCapacity > 0) { "Deck capacity must be positive" }
        mutableSnapshot = MutableStateFlow(createSnapshot())
    }

    @Synchronized
    fun observe(cardId: CardId): Boolean {
        val discoveredCards = tracker.discoveredCards()
        if (cardId !in discoveredCards && discoveredCards.size >= deckCapacity) {
            return false
        }

        tracker.observe(cardId)
        publishSnapshot()
        return true
    }

    @Synchronized
    fun undo(): CardId? {
        val undoneCard = tracker.undoLast() ?: return null
        publishSnapshot()
        return undoneCard
    }

    @Synchronized
    fun reset() {
        tracker.reset()
        publishSnapshot()
    }

    private fun publishSnapshot() {
        mutableSnapshot.value = createSnapshot()
    }

    private fun createSnapshot(): MatchSnapshot {
        val cards = tracker.discoveredCardsInOrder().map { cardId ->
            TrackedCard(
                id = cardId,
                cardsPlayedSince = checkNotNull(tracker.cardsPlayedSince(cardId)) {
                    "A discovered card must have an observation"
                },
                cardsUntilAvailable = checkNotNull(tracker.cardsUntilAvailable(cardId)) {
                    "A discovered card must have cycle information"
                },
            )
        }

        return MatchSnapshot(
            observationCount = tracker.observationCount(),
            deckCapacity = deckCapacity,
            cards = Collections.unmodifiableList(cards),
            canUndo = tracker.observationCount() > 0,
        )
    }
}
