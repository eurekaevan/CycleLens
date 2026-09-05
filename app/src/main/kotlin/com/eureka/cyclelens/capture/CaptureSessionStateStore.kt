package com.eureka.cyclelens.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CaptureSessionStateStore {
    private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = mutableState.asStateFlow()

    @Synchronized
    internal fun requestConsent(): Boolean = transition(CaptureEvent.RequestConsent)

    @Synchronized
    internal fun rejectConsent() {
        transition(CaptureEvent.RejectConsent)
    }

    @Synchronized
    internal fun acceptConsent(): Boolean = transition(CaptureEvent.AcceptConsent)

    @Synchronized
    internal fun start(stats: CaptureStats) {
        transition(CaptureEvent.Start(stats))
    }

    @Synchronized
    internal fun publish(stats: CaptureStats) {
        transition(CaptureEvent.PublishStats(stats))
    }

    @Synchronized
    internal fun stop() {
        transition(CaptureEvent.Stop)
    }

    @Synchronized
    internal fun fail(message: String) {
        transition(CaptureEvent.Fail(message))
    }

    private fun transition(event: CaptureEvent): Boolean {
        val current = mutableState.value
        val next = CaptureStateReducer.reduce(current, event)
        if (next == current) {
            return false
        }
        mutableState.value = next
        return true
    }
}
