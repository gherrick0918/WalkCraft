package com.walkcraft.app.ui.screens.run

import com.walkcraft.app.domain.engine.EngineState
import com.walkcraft.app.domain.format.SpeedFmt
import com.walkcraft.app.domain.format.TimeFmt
import com.walkcraft.app.domain.model.SpeedUnit

sealed interface RunUiState {
    data object Idle : RunUiState

    data class Running(
        val blockLabel: String,
        val speedText: String,
        val remaining: String,
        val nextLabel: String?
    ) : RunUiState

    data class Paused(
        val blockLabel: String,
        val remaining: String
    ) : RunUiState

    data class Finished(val summary: String) : RunUiState

    companion object {
        fun from(s: EngineState): RunUiState = when (s) {
            is EngineState.Idle -> Idle
            is EngineState.Running -> {
                val curr = s.workout.blocks[s.idx]
                val next = s.workout.blocks.getOrNull(s.idx + 1)?.label
                val unit = SpeedUnit.MPH
                val pretty = SpeedFmt.pretty(s.speed, unit, null)
                RunUiState.Running(
                    blockLabel = curr.label,
                    speedText = "$pretty ${unitLabel(unit)}",
                    remaining = TimeFmt.mmSs(s.remaining),
                    nextLabel = next
                )
            }
            is EngineState.Paused -> {
                val curr = s.workout.blocks[s.idx]
                Paused(curr.label, TimeFmt.mmSs(s.remaining))
            }
            is EngineState.Finished -> {
                val secs = s.session.segments.sumOf { it.durationSec }
                Finished(TimeFmt.hMmSs(secs))
            }
        }

        private fun unitLabel(unit: SpeedUnit): String = when (unit) {
            SpeedUnit.MPH -> "mph"
            SpeedUnit.KPH -> "kph"
        }
    }
}
