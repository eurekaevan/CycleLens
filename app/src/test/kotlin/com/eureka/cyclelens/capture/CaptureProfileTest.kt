package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureProfileTest {
    @Test
    fun `profiles derive expected Samsung output dimensions`() {
        assertEquals(1440 to 3120, CaptureProfile.NATIVE.sizeFor(1440, 3120))
        assertEquals(720 to 1560, CaptureProfile.BALANCED.sizeFor(1440, 3120))
        assertEquals(540 to 1170, CaptureProfile.ECO.sizeFor(1440, 3120))
    }

    @Test
    fun `resize retains selected profile`() {
        assertEquals(1560 to 720, CaptureProfile.BALANCED.sizeFor(3120, 1440))
        assertEquals(1170 to 540, CaptureProfile.ECO.sizeFor(3120, 1440))
    }

    @Test
    fun `profile changes only while idle or after error`() {
        val configuration = CaptureConfiguration()
        assertFalse(configuration.setProfile(CaptureProfile.ECO, CaptureState.Starting))
        assertEquals(CaptureProfile.NATIVE, configuration.profile.value)
        assertTrue(configuration.setProfile(CaptureProfile.ECO, CaptureState.Idle))
        assertEquals(CaptureProfile.ECO, configuration.profile.value)
    }

    private fun CaptureProfile.sizeFor(width: Int, height: Int): Pair<Int, Int> =
        geometryFor(width, height).let { it.outputWidth to it.outputHeight }
}
