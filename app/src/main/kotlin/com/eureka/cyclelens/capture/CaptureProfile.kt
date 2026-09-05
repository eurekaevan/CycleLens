package com.eureka.cyclelens.capture

enum class CaptureProfile(
    val maxLongEdge: Int?,
    val targetAnalysisFps: Int,
) {
    NATIVE(maxLongEdge = null, targetAnalysisFps = 30),
    BALANCED(maxLongEdge = 1_560, targetAnalysisFps = 15),
    ECO(maxLongEdge = 1_170, targetAnalysisFps = 10),
    ;

    fun geometryFor(sourceWidth: Int, sourceHeight: Int): CaptureGeometry =
        CaptureGeometry.derive(sourceWidth, sourceHeight, maxLongEdge)
}

class CaptureConfiguration(
    initialProfile: CaptureProfile = CaptureProfile.NATIVE,
) {
    private val mutableProfile = kotlinx.coroutines.flow.MutableStateFlow(initialProfile)
    val profile: kotlinx.coroutines.flow.StateFlow<CaptureProfile> = mutableProfile

    fun setProfile(profile: CaptureProfile, captureState: CaptureState): Boolean {
        if (captureState != CaptureState.Idle && captureState !is CaptureState.Error) {
            return false
        }
        mutableProfile.value = profile
        return true
    }
}
