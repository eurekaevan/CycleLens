package com.eureka.cyclelens.overlay

import com.eureka.cyclelens.session.MatchSession
import com.eureka.cyclelens.testCardCatalog
import cyclelens.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayPresentationTest {
    private val catalog = testCardCatalog()
    private val quickCardIds = catalog.allCycleCards().map { card -> card.id }
    private val mapper = OverlayPresentationMapper(catalog)

    @Test
    fun `mapper preserves stable first discovery slots while cycle state changes`() {
        val session = MatchSession()
        FIRST_FOUR.forEach { session.observe(it) }
        val initialSlots = state(session).cards.map { it.cardId }

        session.observe(LOG)
        session.observe(FIREBALL)

        assertEquals(initialSlots, state(session).cards.map { it.cardId })
    }

    @Test
    fun `cycle distances use compact one through four labels`() {
        val session = MatchSession()
        FIRST_FOUR.forEach { session.observe(it) }

        assertEquals(listOf("1", "2", "3", "4"), state(session).cards.map { it.cycleLabel })
    }

    @Test
    fun `available cards use a check mark`() {
        val session = MatchSession()
        listOf(FIREBALL, KNIGHT, LOG, SKELETONS, ICE_SPIRIT).forEach {
            session.observe(it)
        }

        val fireball = state(session).cards.first()

        assertEquals("✓", fireball.cycleLabel)
        assertTrue(fireball.available)
    }

    @Test
    fun `picker contains only configured undiscovered quick cards`() {
        val session = MatchSession()
        session.observe(FIREBALL)

        val state = mapper.map(
            matchSnapshot = session.snapshot.value,
            panel = OverlayPanel.PICKER,
            quickCardIds = listOf(FIREBALL, LOG, KNIGHT),
            appearance = OverlayAppearance(),
        )

        assertEquals(listOf(LOG, KNIGHT), state.pickerCards.map { it.cardId })
        assertTrue(state.canAddCard)
    }

    @Test
    fun `controller switches between expanded collapsed and picker panels`() {
        val session = MatchSession()
        val controller = OverlayPresentationController(mapper)

        assertEquals(OverlayPanel.EXPANDED, controller.state(session).panel)
        controller.collapse()
        assertEquals(OverlayPanel.COLLAPSED, controller.state(session).panel)
        controller.expand()
        assertTrue(
            controller.openPicker(
                session.snapshot.value,
                quickCardIds,
                OverlayAppearance(),
            ),
        )
        assertEquals(OverlayPanel.PICKER, controller.state(session).panel)
    }

    @Test
    fun `full deck disables add and closes an open picker`() {
        val session = MatchSession()
        val controller = OverlayPresentationController(mapper)
        assertTrue(
            controller.openPicker(
                session.snapshot.value,
                quickCardIds,
                OverlayAppearance(),
            ),
        )
        FIRST_EIGHT.forEach { session.observe(it) }

        val state = controller.state(session)

        assertFalse(state.canAddCard)
        assertEquals(OverlayPanel.EXPANDED, state.panel)
    }

    @Test
    fun `presentation carries compact and comfortable size modes`() {
        val session = MatchSession()

        val compact = mapper.map(
            session.snapshot.value,
            OverlayPanel.EXPANDED,
            quickCardIds,
            OverlayAppearance(sizeMode = OverlaySizeMode.COMPACT),
        )
        val comfortable = mapper.map(
            session.snapshot.value,
            OverlayPanel.EXPANDED,
            quickCardIds,
            OverlayAppearance(sizeMode = OverlaySizeMode.COMFORTABLE),
        )

        assertEquals(OverlaySizeMode.COMPACT, compact.sizeMode)
        assertEquals(OverlaySizeMode.COMFORTABLE, comfortable.sizeMode)
    }

    @Test
    fun `presentation carries configured background opacity`() {
        val session = MatchSession()

        val state = mapper.map(
            session.snapshot.value,
            OverlayPanel.COLLAPSED,
            quickCardIds,
            OverlayAppearance(backgroundOpacity = OverlayBackgroundOpacity.SEVENTY),
        )

        assertEquals(OverlayBackgroundOpacity.SEVENTY, state.backgroundOpacity)
    }

    @Test
    fun `full detail includes the catalog short name`() {
        val session = MatchSession()
        session.observe(FIREBALL)

        val card = mapper.map(
            session.snapshot.value,
            OverlayPanel.EXPANDED,
            quickCardIds,
            OverlayAppearance(detailMode = OverlayDetailMode.FULL),
        ).cards.single()

        assertEquals("Fire", card.shortName)
        assertEquals("4", card.cycleLabel)
    }

    @Test
    fun `minimal detail omits the short name but keeps cycle state`() {
        val session = MatchSession()
        session.observe(FIREBALL)

        val card = mapper.map(
            session.snapshot.value,
            OverlayPanel.EXPANDED,
            quickCardIds,
            OverlayAppearance(detailMode = OverlayDetailMode.MINIMAL),
        ).cards.single()

        assertEquals(null, card.shortName)
        assertEquals("4", card.cycleLabel)
    }

    @Test
    fun `changing detail mode does not change slot ordering`() {
        val session = MatchSession()
        FIRST_FOUR.forEach { cardId -> session.observe(cardId) }

        val minimal = mapper.map(
            session.snapshot.value,
            OverlayPanel.EXPANDED,
            quickCardIds,
            OverlayAppearance(detailMode = OverlayDetailMode.MINIMAL),
        )
        val full = mapper.map(
            session.snapshot.value,
            OverlayPanel.EXPANDED,
            quickCardIds,
            OverlayAppearance(detailMode = OverlayDetailMode.FULL),
        )

        assertEquals(minimal.cards.map { it.cardId }, full.cards.map { it.cardId })
    }

    private fun state(session: MatchSession): OverlayPresentationState = mapper.map(
        matchSnapshot = session.snapshot.value,
        panel = OverlayPanel.EXPANDED,
        quickCardIds = quickCardIds,
        appearance = OverlayAppearance(),
    )

    private fun OverlayPresentationController.state(
        session: MatchSession,
    ): OverlayPresentationState = stateFor(
        matchSnapshot = session.snapshot.value,
        quickCardIds = quickCardIds,
        appearance = OverlayAppearance(),
    )

    private companion object {
        val FIREBALL = CardId("fireball")
        val KNIGHT = CardId("knight")
        val LOG = CardId("the_log")
        val SKELETONS = CardId("skeletons")
        val ICE_SPIRIT = CardId("ice_spirit")
        val FIRST_FOUR = listOf(FIREBALL, KNIGHT, LOG, SKELETONS)
        val FIRST_EIGHT = FIRST_FOUR + listOf(
            ICE_SPIRIT,
            CardId("cannon"),
            CardId("musketeer"),
            CardId("hog_rider"),
        )
    }
}
