package com.walkcraft.app.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Publishes the current session's local step delta (since session start).
 * Foreground service sets this; ViewModel observes it to update UI instantly.
 */
object StepBus {
    private val _delta = MutableStateFlow(0L)
    val delta: StateFlow<Long> = _delta.asStateFlow()

    fun setDelta(stepsSinceStart: Long) {
        if (stepsSinceStart >= 0 && stepsSinceStart != _delta.value) {
            _delta.value = stepsSinceStart
        }
    }

    fun reset() {
        _delta.value = 0L
    }
}
