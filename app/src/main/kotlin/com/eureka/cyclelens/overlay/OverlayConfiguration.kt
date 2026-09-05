package com.eureka.cyclelens.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlaySizeMode {
    COMPACT,
    COMFORTABLE,
}

enum class OverlayDetailMode {
    FULL,
    MINIMAL,
}

enum class OverlayBackgroundOpacity(val percent: Int) {
    SEVENTY(70),
    FIFTY(50),
    THIRTY_FIVE(35),
}

data class OverlayAppearance(
    val sizeMode: OverlaySizeMode = OverlaySizeMode.COMFORTABLE,
    val detailMode: OverlayDetailMode = OverlayDetailMode.MINIMAL,
    val backgroundOpacity: OverlayBackgroundOpacity = OverlayBackgroundOpacity.FIFTY,
)

class OverlayConfiguration(
    initialAppearance: OverlayAppearance = OverlayAppearance(),
) {
    private val mutableAppearance = MutableStateFlow(initialAppearance)
    val appearance: StateFlow<OverlayAppearance> = mutableAppearance.asStateFlow()

    fun setSizeMode(sizeMode: OverlaySizeMode) {
        mutableAppearance.value = mutableAppearance.value.copy(sizeMode = sizeMode)
    }

    fun setDetailMode(detailMode: OverlayDetailMode) {
        mutableAppearance.value = mutableAppearance.value.copy(detailMode = detailMode)
    }

    fun setBackgroundOpacity(backgroundOpacity: OverlayBackgroundOpacity) {
        mutableAppearance.value = mutableAppearance.value.copy(
            backgroundOpacity = backgroundOpacity,
        )
    }
}
