package com.walkcraft.app.health

data class StepSessionState(
    val active: Boolean = false,
    val startEpochMs: Long = 0L,
    val baselineSteps: Long = 0L,
    val latestSteps: Long = 0L,
    val elapsedMs: Long = 0L,
) {
    val sessionSteps: Long get() = (latestSteps - baselineSteps).coerceAtLeast(0)
}
