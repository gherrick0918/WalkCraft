package com.walkcraft.app.ui.screens.run

import com.walkcraft.app.domain.engine.EngineState
import com.walkcraft.app.domain.format.SpeedFmt
import com.walkcraft.app.domain.format.TimeFmt
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.service.WorkoutService

sealed interface RunUiState {
    data object Idle : RunUiState

    data class Running(
        val blockLabel: String,
        val speedText: String,
        val remaining: String,
        val nextLabel: String?,
        val health: RunHealth
    ) : RunUiState

    data class Paused(
        val blockLabel: String,
        val remaining: String,
        val health: RunHealth
    ) : RunUiState

    data class Finished(val summary: String) : RunUiState

    companion object {
        fun from(s: EngineState, telemetry: WorkoutService.HealthTelemetry): RunUiState {
            val health = telemetry.toRunHealth()
            return when (s) {
                is EngineState.Idle -> Idle
            is EngineState.Running -> {
                val curr = s.workout.blocks[s.idx]
                val next = s.workout.blocks.getOrNull(s.idx + 1)?.label
                val unit = SpeedUnit.MPH
                val pretty = SpeedFmt.pretty(s.speed, unit, null)
                RunUiState.Running(
                    blockLabel = curr.label,
                    speedText = "$pretty ${SpeedFmt.unitLabel(unit)}",
                    remaining = TimeFmt.mmSs(s.remaining),
                    nextLabel = next,
                    health = health
                )
            }
            is EngineState.Paused -> {
                val curr = s.workout.blocks[s.idx]
                Paused(curr.label, TimeFmt.mmSs(s.remaining), health)
            }
            is EngineState.Finished -> {
                val secs = s.session.segments.sumOf { it.durationSec }
                Finished(TimeFmt.hMmSs(secs))
            }
        }
        }
    }
}

sealed interface RunHealth {
    data object Inactive : RunHealth
    data object PermissionsNeeded : RunHealth
    data class Active(val heartRateBpm: Int?, val steps: Int?) : RunHealth
}

private fun WorkoutService.HealthTelemetry.toRunHealth(): RunHealth = when (this) {
    WorkoutService.HealthTelemetry.Inactive -> RunHealth.Inactive
    WorkoutService.HealthTelemetry.PermissionsNeeded -> RunHealth.PermissionsNeeded
    is WorkoutService.HealthTelemetry.Active -> RunHealth.Active(heartRateBpm, totalSteps)
}
