package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CaptureGeometryTest {
    @Test
    fun `balanced portrait is derived from long edge`() {
        val geometry = CaptureProfile.BALANCED.geometryFor(1440, 3120)
        assertEquals(720, geometry.outputWidth)
        assertEquals(1560, geometry.outputHeight)
    }

    @Test
    fun `balanced landscape preserves orientation`() {
        val geometry = CaptureProfile.BALANCED.geometryFor(3120, 1440)
        assertEquals(1560, geometry.outputWidth)
        assertEquals(720, geometry.outputHeight)
    }

    @Test
    fun `eco portrait derives fifty four hundredths scale`() {
        val geometry = CaptureProfile.ECO.geometryFor(1440, 3120)
        assertEquals(540, geometry.outputWidth)
        assertEquals(1170, geometry.outputHeight)
        assertEquals(0.375f, geometry.scaleX)
        assertEquals(0.375f, geometry.scaleY)
    }

    @Test
    fun `arbitrary dimensions remain even and preserve aspect ratio`() {
        val geometry = CaptureProfile.BALANCED.geometryFor(1234, 2711)
        assertEquals(0, geometry.outputWidth % 2)
        assertEquals(0, geometry.outputHeight % 2)
        assertTrue(kotlin.math.abs(geometry.scaleX - geometry.scaleY) < 0.001f)
    }

    @Test
    fun `source output round trip`() {
        val geometry = CaptureProfile.BALANCED.geometryFor(1440, 3120)
        val output = geometry.sourceToOutput(431.25f, 2111.5f)
        val source = geometry.outputToSource(output.x, output.y)
        assertEquals(431.25f, source.x, 0.001f)
        assertEquals(2111.5f, source.y, 0.001f)
    }

    @Test
    fun `invalid dimensions are rejected`() {
        assertFailsWith<IllegalArgumentException> { CaptureGeometry.derive(0, 100, 50) }
        assertFailsWith<IllegalArgumentException> { CaptureGeometry.derive(100, -1, 50) }
        assertFailsWith<IllegalArgumentException> { CaptureGeometry.derive(100, 100, 0) }
        assertFailsWith<IllegalArgumentException> { CaptureGeometry(100, 100, 100, 50) }
    }

    @Test
    fun `native does not upscale small source`() {
        val geometry = CaptureProfile.NATIVE.geometryFor(800, 600)
        assertEquals(800, geometry.outputWidth)
        assertEquals(600, geometry.outputHeight)
    }
}
