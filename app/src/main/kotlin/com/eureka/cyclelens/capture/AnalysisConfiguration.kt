package com.eureka.cyclelens.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AnalysisDelay(val milliseconds: Long) {
    NONE(0),
    MS_20(20),
    MS_50(50),
    MS_100(100),
}

class AnalysisConfiguration(initialDelay: AnalysisDelay = AnalysisDelay.NONE) {
    private val mutableDelay = MutableStateFlow(initialDelay)
    val delay: StateFlow<AnalysisDelay> = mutableDelay

    fun setDelay(delay: AnalysisDelay) {
        mutableDelay.value = delay
    }
}
