package com.walkcraft.app.health

data class StepSessionState(
    val active: Boolean = false,
    val startEpochMs: Long = 0L,
    val baselineSteps: Long = 0L,
    val latestSteps: Long = 0L,
    val lastTickMs: Long = 0L
) {
    val sessionSteps: Long get() = (latestSteps - baselineSteps).coerceAtLeast(0)
    val elapsedMs: Long get() = if (active) (System.currentTimeMillis() - startEpochMs) else 0L
}
