package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NormalizedRectTest {
    @Test
    fun `valid normalized rect maps outward to pixels`() {
        val geometry = CaptureProfile.BALANCED.geometryFor(1440, 3120)
        assertEquals(
            PixelRect(72, 312, 648, 1404),
            NormalizedRect(0.1f, 0.2f, 0.9f, 0.9f).toPixelRect(geometry),
        )
    }

    @Test
    fun `full frame maps to all output pixels`() {
        val geometry = CaptureProfile.ECO.geometryFor(1440, 3120)
        assertEquals(PixelRect(0, 0, 540, 1170), NormalizedRect.FULL_FRAME.toPixelRect(geometry))
    }

    @Test
    fun `rotation geometry maps normalized coordinates in the new orientation`() {
        val geometry = CaptureProfile.BALANCED.geometryFor(3120, 1440)
        assertEquals(
            PixelRect(156, 72, 1404, 648),
            NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f).toPixelRect(geometry),
        )
    }

    @Test
    fun `calibrated arena maps to balanced pixels`() {
        val geometry = CaptureProfile.BALANCED.geometryFor(1440, 3120)
        assertEquals(
            PixelRect(0, 148, 720, 1334),
            ClashRoyaleCaptureLayout.arenaRegion.toPixelRect(geometry),
        )
    }

    @Test
    fun `invalid bounds and empty rectangles are rejected`() {
        assertFailsWith<IllegalArgumentException> { NormalizedRect(-0.1f, 0f, 1f, 1f) }
        assertFailsWith<IllegalArgumentException> { NormalizedRect(0f, 0f, 1.1f, 1f) }
        assertFailsWith<IllegalArgumentException> { NormalizedRect(0.5f, 0f, 0.5f, 1f) }
        assertFailsWith<IllegalArgumentException> { NormalizedRect(0f, 0.8f, 1f, 0.2f) }
    }
}
