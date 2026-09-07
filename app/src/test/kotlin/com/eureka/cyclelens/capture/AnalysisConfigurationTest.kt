package com.eureka.cyclelens.capture

import kotlin.test.Test
import kotlin.test.assertEquals

class AnalysisConfigurationTest {
    @Test
    fun `supported debug delays are explicit and mutable`() {
        assertEquals(listOf(0L, 20L, 50L, 100L), AnalysisDelay.entries.map { it.milliseconds })
        val configuration = AnalysisConfiguration()
        assertEquals(AnalysisDelay.NONE, configuration.delay.value)
        configuration.setDelay(AnalysisDelay.MS_100)
        assertEquals(AnalysisDelay.MS_100, configuration.delay.value)
    }
}
