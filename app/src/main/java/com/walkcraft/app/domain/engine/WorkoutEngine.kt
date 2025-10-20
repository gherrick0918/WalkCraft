package com.walkcraft.app.domain.engine

import android.os.SystemClock
import com.walkcraft.app.domain.mapper.DiscreteSpeedMapper
import com.walkcraft.app.domain.model.Block
import com.walkcraft.app.domain.model.CompletedSegment
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.RampBlock
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SteadyBlock
import com.walkcraft.app.domain.model.Workout

sealed interface EngineState {
    data class Idle(val workout: Workout?) : EngineState
    data class Running(
        val workout: Workout,
        val idx: Int,
        val remaining: Int,
        val speed: Double
    ) : EngineState
    data class Paused(
        val workout: Workout,
        val idx: Int,
        val remaining: Int,
        val speed: Double
    ) : EngineState
    data class Finished(val session: Session) : EngineState
}

class WorkoutEngine(
    private val caps: DeviceCapabilities,
    private val policy: SpeedPolicy,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var state: EngineState = EngineState.Idle(null)
    private val segments = mutableListOf<CompletedSegment>()
    private var startedAt: Long = 0L

    fun current(): EngineState = state

    fun start(workout: Workout) {
        require(workout.blocks.isNotEmpty()) { "Workout has no blocks" }
        startedAt = System.currentTimeMillis()
        state = EngineState.Running(
            workout = workout,
            idx = 0,
            remaining = workout.blocks.first().durationSec,
            speed = speedFor(workout.blocks.first())
        )
    }

    fun tick(second: Int = 1) {
        val s = state
        if (s is EngineState.Running) {
            val rem = s.remaining - second
            if (rem <= 0) {
                // Ensure the state reflects the true boundary before recording the segment
                state = s.copy(remaining = 0)
                nextBlock(s.workout, s.idx + 1)
            } else {
                state = s.copy(remaining = rem)
            }
        }
    }

    fun pause() {
        state = (state as? EngineState.Running)
            ?.let { EngineState.Paused(it.workout, it.idx, it.remaining, it.speed) }
            ?: state
    }

    fun resume() {
        state = (state as? EngineState.Paused)
            ?.let { EngineState.Running(it.workout, it.idx, it.remaining, it.speed) }
            ?: state
    }

    fun skip() {
        (state as? EngineState.Running)?.let { nextBlock(it.workout, it.idx + 1) }
    }

    private fun nextBlock(w: Workout, idx: Int) {
        val prev = (state as? EngineState.Running)
        if (prev != null) {
            val original = prev.workout.blocks[prev.idx].durationSec
            val remaining = prev.remaining.coerceAtLeast(0)
            val consumed = (original - remaining).coerceIn(0, original)
            segments += CompletedSegment(
                blockIndex = prev.idx,
                actualSpeed = prev.speed,
                durationSec = consumed
            )
        }

        if (idx >= w.blocks.size) {
            state = EngineState.Finished(
                Session(
                    workoutId = w.id,
                    startedAt = startedAt,
                    endedAt = System.currentTimeMillis(),
                    unit = caps.unit,
                    segments = segments.toList(),
                    workoutName = w.name
                )
            )
            return
        }

        val block = w.blocks[idx]
        state = EngineState.Running(w, idx, block.durationSec, speedFor(block))
    }

    private fun speedFor(b: Block): Double = when (b) {
        is SteadyBlock -> DiscreteSpeedMapper.map(b.targetSpeed, caps, policy)
        is RampBlock -> DiscreteSpeedMapper.map(b.fromSpeed, caps, policy) // MVP: treat ramp as steady(start)
    }
}
