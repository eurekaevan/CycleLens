package com.eureka.cyclelens

import cyclelens.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CycleTrackerViewModelTest {
    @Test
    fun `observe trims input and publishes cycle state`() {
        val viewModel = CycleTrackerViewModel()
        viewModel.onInputChanged("  fireball  ")

        viewModel.observeInput()

        assertEquals(
            TrackerUiState(
                input = "",
                observations = 1,
                cards = listOf(
                    CardUiState(
                        cardId = CardId("fireball"),
                        cardsPlayedSince = 0,
                        cardsUntilAvailable = 4,
                        available = false,
                    ),
                ),
                canUndo = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `undo restores the previous UI state`() {
        val viewModel = CycleTrackerViewModel()
        viewModel.onInputChanged("fireball")
        viewModel.observeInput()
        viewModel.onInputChanged("knight")
        viewModel.observeInput()

        viewModel.undo()

        val state = viewModel.uiState.value
        assertEquals(1, state.observations)
        assertEquals(listOf(CardId("fireball")), state.cards.map { it.cardId })
        assertEquals(0, state.cards.single().cardsPlayedSince)
        assertEquals(4, state.cards.single().cardsUntilAvailable)
        assertTrue(state.canUndo)
    }

    @Test
    fun `reset clears observations input and cards`() {
        val viewModel = CycleTrackerViewModel()
        viewModel.onInputChanged("fireball")
        viewModel.observeInput()
        viewModel.onInputChanged("draft")

        viewModel.reset()

        assertEquals(TrackerUiState(), viewModel.uiState.value)
    }

    @Test
    fun `blank input does not create an observation`() {
        val viewModel = CycleTrackerViewModel()
        viewModel.onInputChanged("  \t  ")

        viewModel.observeInput()

        val state = viewModel.uiState.value
        assertEquals(0, state.observations)
        assertTrue(state.cards.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun `completed cycle is published as available`() {
        val viewModel = CycleTrackerViewModel()
        listOf("fireball", "knight", "the_log", "skeletons", "ice_spirit").forEach { cardId ->
            viewModel.onInputChanged(cardId)
            viewModel.observeInput()
        }

        val fireball = viewModel.uiState.value.cards.first { it.cardId == CardId("fireball") }

        assertEquals(0, fireball.cardsUntilAvailable)
        assertTrue(fireball.available)
    }
}
