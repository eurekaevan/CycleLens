package com.eureka.cyclelens

import androidx.lifecycle.ViewModel
import cyclelens.core.CardId
import cyclelens.core.CycleTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CycleTrackerViewModel(
    private val tracker: CycleTracker = CycleTracker(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(snapshot(input = ""))
    val uiState: StateFlow<TrackerUiState> = mutableUiState.asStateFlow()

    fun onInputChanged(input: String) {
        mutableUiState.value = mutableUiState.value.copy(input = input)
    }

    fun observeInput() {
        val normalizedInput = mutableUiState.value.input.trim()
        if (normalizedInput.isEmpty()) {
            return
        }

        tracker.observe(CardId(normalizedInput))
        publish(input = "")
    }

    fun undo() {
        if (tracker.undoLast() != null) {
            publish(input = mutableUiState.value.input)
        }
    }

    fun reset() {
        tracker.reset()
        publish(input = "")
    }

    private fun publish(input: String) {
        mutableUiState.value = snapshot(input)
    }

    private fun snapshot(input: String): TrackerUiState {
        val cards = tracker.discoveredCards().map { cardId ->
            val cardsPlayedSince = checkNotNull(tracker.cardsPlayedSince(cardId)) {
                "A discovered card must have an observation"
            }
            val cardsUntilAvailable = checkNotNull(tracker.cardsUntilAvailable(cardId)) {
                "A discovered card must have cycle information"
            }

            CardUiState(
                cardId = cardId,
                cardsPlayedSince = cardsPlayedSince,
                cardsUntilAvailable = cardsUntilAvailable,
                available = cardsUntilAvailable == 0,
            )
        }

        return TrackerUiState(
            input = input,
            observations = tracker.observationCount(),
            cards = cards,
            canUndo = tracker.observationCount() > 0,
        )
    }
}
