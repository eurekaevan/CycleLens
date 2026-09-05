package com.eureka.cyclelens

import com.eureka.cyclelens.catalog.CardCatalog
import com.eureka.cyclelens.catalog.CardDefinition
import com.eureka.cyclelens.catalog.CardForm
import com.eureka.cyclelens.catalog.CardType
import com.eureka.cyclelens.catalog.VisualFormDefinition
import com.eureka.cyclelens.session.MatchSession
import com.eureka.cyclelens.overlay.OverlayBackgroundOpacity
import com.eureka.cyclelens.overlay.OverlayConfiguration
import com.eureka.cyclelens.overlay.OverlayDetailMode
import com.eureka.cyclelens.overlay.OverlayQuickCards
import com.eureka.cyclelens.overlay.OverlaySizeMode
import cyclelens.core.CardId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CycleTrackerViewModelTest {
    @Test
    fun `selecting a new card adds it to the opponent deck`() {
        val viewModel = createViewModel()

        selectNewCard(viewModel, FIREBALL)

        val state = viewModel.uiState.value
        assertEquals(1, state.observationCount)
        assertEquals(listOf(FIREBALL), state.opponentDeck.map { it.cardId })
        assertEquals("Fireball", state.opponentDeck.single().displayName)
        assertFalse(state.isCardPickerVisible)
    }

    @Test
    fun `clicking a tracked card records another play without growing the deck`() {
        val viewModel = createViewModel()
        selectNewCard(viewModel, FIREBALL)

        viewModel.observeTrackedCard(FIREBALL)

        val state = viewModel.uiState.value
        assertEquals(2, state.observationCount)
        assertEquals(1, state.opponentDeck.size)
        assertEquals(FIREBALL, state.opponentDeck.single().cardId)
        assertEquals(0, state.opponentDeck.single().cardsPlayedSince)
    }

    @Test
    fun `adding the eighth distinct card disables adding cards`() {
        val viewModel = createViewModel()

        FIRST_EIGHT.forEach { selectNewCard(viewModel, it) }

        val state = viewModel.uiState.value
        assertEquals(8, state.opponentDeck.size)
        assertFalse(state.canAddCard)

        viewModel.openCardPicker()
        assertFalse(viewModel.uiState.value.isCardPickerVisible)
    }

    @Test
    fun `a ninth distinct card is rejected`() {
        val viewModel = createViewModel()
        FIRST_EIGHT.forEach { selectNewCard(viewModel, it) }

        selectNewCard(viewModel, NINTH_CARD)

        val state = viewModel.uiState.value
        assertEquals(8, state.observationCount)
        assertEquals(FIRST_EIGHT, state.opponentDeck.map { it.cardId })
        assertTrue(state.opponentDeck.none { it.cardId == NINTH_CARD })
    }

    @Test
    fun `undoing a card's only observation removes it from the deck`() {
        val viewModel = createViewModel()
        selectNewCard(viewModel, FIREBALL)

        viewModel.undo()

        val state = viewModel.uiState.value
        assertEquals(0, state.observationCount)
        assertTrue(state.opponentDeck.isEmpty())
        assertTrue(state.pickerCards.any { it.cardId == FIREBALL })
    }

    @Test
    fun `undoing a repeated observation keeps the card in the deck`() {
        val viewModel = createViewModel()
        selectNewCard(viewModel, FIREBALL)
        selectNewCard(viewModel, KNIGHT)
        viewModel.observeTrackedCard(FIREBALL)

        viewModel.undo()

        val state = viewModel.uiState.value
        assertEquals(2, state.observationCount)
        assertEquals(listOf(FIREBALL, KNIGHT), state.opponentDeck.map { it.cardId })
        assertEquals(1, state.opponentDeck.first().cardsPlayedSince)
    }

    @Test
    fun `undoing the eighth card's only observation enables adding again`() {
        val viewModel = createViewModel()
        FIRST_EIGHT.forEach { selectNewCard(viewModel, it) }

        viewModel.undo()

        val state = viewModel.uiState.value
        assertEquals(7, state.opponentDeck.size)
        assertTrue(state.canAddCard)
        assertTrue(state.pickerCards.any { it.cardId == FIRST_EIGHT.last() })
    }

    @Test
    fun `confirming reset clears the current match`() {
        val viewModel = createViewModel()
        selectNewCard(viewModel, FIREBALL)
        selectNewCard(viewModel, KNIGHT)

        viewModel.requestReset()
        assertTrue(viewModel.uiState.value.isResetConfirmationVisible)
        viewModel.confirmReset()

        val state = viewModel.uiState.value
        assertEquals(0, state.observationCount)
        assertTrue(state.opponentDeck.isEmpty())
        assertEquals(16, state.pickerCards.size)
        assertTrue(state.canAddCard)
        assertFalse(state.canUndo)
        assertFalse(state.canReset)
        assertFalse(state.isResetConfirmationVisible)
    }

    @Test
    fun `search filters by display name and card ID`() {
        val viewModel = createViewModel()
        viewModel.openCardPicker()

        viewModel.onSearchQueryChanged("fire")
        assertEquals(listOf(FIREBALL), viewModel.uiState.value.pickerCards.map { it.cardId })

        viewModel.onSearchQueryChanged("the_")
        assertEquals(listOf(THE_LOG), viewModel.uiState.value.pickerCards.map { it.cardId })
    }

    @Test
    fun `search is case insensitive`() {
        val viewModel = createViewModel()
        viewModel.openCardPicker()

        viewModel.onSearchQueryChanged("HoG")

        assertEquals(listOf(HOG_RIDER), viewModel.uiState.value.pickerCards.map { it.cardId })
    }

    @Test
    fun `empty search shows every selectable card`() {
        val viewModel = createViewModel()
        viewModel.openCardPicker()
        viewModel.onSearchQueryChanged("fire")

        viewModel.onSearchQueryChanged("")

        assertEquals(16, viewModel.uiState.value.pickerCards.size)
    }

    @Test
    fun `a discovered card is excluded from new card choices`() {
        val viewModel = createViewModel()
        selectNewCard(viewModel, FIREBALL)

        viewModel.openCardPicker()

        assertTrue(viewModel.uiState.value.pickerCards.none { it.cardId == FIREBALL })
        assertEquals(15, viewModel.uiState.value.pickerCards.size)
    }

    @Test
    fun `non-cycle catalog entries cannot enter activity or quick pickers`() {
        val baseCatalog = testCardCatalog()
        val towerPrincess = CardDefinition(
            id = TOWER_PRINCESS,
            supercellId = null,
            displayName = "Tower Princess",
            shortName = "Tower",
            type = CardType.TOWER_TROOP,
            elixir = null,
            cycleEligible = false,
            iconAssetPath = null,
        )
        val catalog = CardCatalog(
            definitions = baseCatalog.allCards() + towerPrincess,
            visualFormDefinitions = baseCatalog.allVisualForms() + VisualFormDefinition(
                id = "tower_princess_normal",
                canonicalCardId = TOWER_PRINCESS,
                form = CardForm.NORMAL,
                displayName = "Tower Princess",
            ),
        )
        val viewModel = createViewModel(catalog = catalog)

        viewModel.openCardPicker()
        viewModel.selectNewCard(TOWER_PRINCESS)

        val state = viewModel.uiState.value
        assertTrue(state.pickerCards.none { card -> card.cardId == TOWER_PRINCESS })
        assertTrue(state.quickCardOptions.none { card -> card.cardId == TOWER_PRINCESS })
        assertTrue(state.opponentDeck.isEmpty())
        assertEquals(0, state.observationCount)
    }

    @Test
    fun `a completed cycle is published as available`() {
        val viewModel = createViewModel()
        listOf(FIREBALL, KNIGHT, THE_LOG, SKELETONS, ICE_SPIRIT).forEach {
            selectNewCard(viewModel, it)
        }

        val fireball = viewModel.uiState.value.opponentDeck.first { it.cardId == FIREBALL }

        assertEquals(0, fireball.cardsUntilAvailable)
        assertTrue(fireball.available)
    }

    @Test
    fun `external session changes are reflected in UI state`() {
        val matchSession = MatchSession()
        val viewModel = createViewModel(matchSession)

        matchSession.observe(FIREBALL)
        matchSession.observe(KNIGHT)

        assertEquals(
            listOf(FIREBALL, KNIGHT),
            viewModel.uiState.value.opponentDeck.map { it.cardId },
        )
        assertEquals(2, viewModel.uiState.value.observationCount)
    }

    @Test
    fun `quick card editor updates its independent candidate selection`() {
        val viewModel = createViewModel()
        viewModel.openQuickCardEditor()

        viewModel.toggleQuickCard(FIREBALL)
        viewModel.onQuickCardSearchQueryChanged("fire")

        val state = viewModel.uiState.value
        assertTrue(state.isQuickCardEditorVisible)
        assertEquals(15, state.quickCardCount)
        assertEquals(listOf(FIREBALL), state.quickCardOptions.map { it.cardId })
        assertFalse(state.quickCardOptions.single().selected)
        assertTrue(state.opponentDeck.isEmpty())
    }

    @Test
    fun `overlay appearance changes are published to Activity state`() {
        val viewModel = createViewModel()

        viewModel.setOverlaySizeMode(OverlaySizeMode.COMPACT)
        viewModel.setOverlayDetailMode(OverlayDetailMode.FULL)
        viewModel.setOverlayBackgroundOpacity(OverlayBackgroundOpacity.SEVENTY)

        assertEquals(OverlaySizeMode.COMPACT, viewModel.uiState.value.overlaySizeMode)
        assertEquals(OverlayDetailMode.FULL, viewModel.uiState.value.overlayDetailMode)
        assertEquals(
            OverlayBackgroundOpacity.SEVENTY,
            viewModel.uiState.value.overlayBackgroundOpacity,
        )
    }

    private fun createViewModel(
        matchSession: MatchSession = MatchSession(),
        catalog: CardCatalog = testCardCatalog(),
    ): CycleTrackerViewModel {
        return CycleTrackerViewModel(
            matchSession = matchSession,
            catalog = catalog,
            overlayQuickCards = OverlayQuickCards(catalog),
            overlayConfiguration = OverlayConfiguration(),
            observationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    private fun selectNewCard(viewModel: CycleTrackerViewModel, cardId: CardId) {
        viewModel.openCardPicker()
        viewModel.selectNewCard(cardId)
    }

    private companion object {
        val HOG_RIDER = CardId("hog_rider")
        val FIREBALL = CardId("fireball")
        val THE_LOG = CardId("the_log")
        val KNIGHT = CardId("knight")
        val SKELETONS = CardId("skeletons")
        val ICE_SPIRIT = CardId("ice_spirit")
        val CANNON = CardId("cannon")
        val MUSKETEER = CardId("musketeer")
        val NINTH_CARD = CardId("archers")
        val TOWER_PRINCESS = CardId("tower_princess")

        val FIRST_EIGHT = listOf(
            HOG_RIDER,
            FIREBALL,
            THE_LOG,
            KNIGHT,
            SKELETONS,
            ICE_SPIRIT,
            CANNON,
            MUSKETEER,
        )
    }
}
