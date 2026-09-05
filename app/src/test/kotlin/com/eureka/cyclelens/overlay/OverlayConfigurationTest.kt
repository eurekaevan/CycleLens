package com.eureka.cyclelens.overlay

import com.eureka.cyclelens.session.MatchSession
import cyclelens.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayConfigurationTest {
    @Test
    fun `default appearance uses minimal detail and fifty percent background opacity`() {
        val appearance = OverlayAppearance()

        assertEquals(OverlayDetailMode.MINIMAL, appearance.detailMode)
        assertEquals(OverlayBackgroundOpacity.FIFTY, appearance.backgroundOpacity)
    }

    @Test
    fun `background opacity presets map to seventy fifty and thirty five percent`() {
        assertEquals(
            listOf(70, 50, 35),
            OverlayBackgroundOpacity.entries.map { opacity -> opacity.percent },
        )
    }

    @Test
    fun `appearance changes do not alter the match session`() {
        val session = MatchSession()
        session.observe(CardId("fireball"))
        val snapshotBeforeAppearanceChange = session.snapshot.value
        val configuration = OverlayConfiguration()

        configuration.setSizeMode(OverlaySizeMode.COMPACT)
        configuration.setDetailMode(OverlayDetailMode.FULL)
        configuration.setBackgroundOpacity(OverlayBackgroundOpacity.THIRTY_FIVE)

        assertEquals(snapshotBeforeAppearanceChange, session.snapshot.value)
    }
}
