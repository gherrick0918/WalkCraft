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
import java.util.UUID

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
    private var sessionId: String? = null

    fun current(): EngineState = state

    fun start(workout: Workout) {
        require(workout.blocks.isNotEmpty()) { "Workout has no blocks" }
        startedAt = System.currentTimeMillis()
        sessionId = UUID.randomUUID().toString()
        segments.clear()
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
            val session = sessionFrom(
                workout = w,
                extraSegment = null,
                endedAt = System.currentTimeMillis()
            )
            state = EngineState.Finished(session)
            return
        }

        val block = w.blocks[idx]
        state = EngineState.Running(w, idx, block.durationSec, speedFor(block))
    }

    fun finishNow(): Session {
        val current = state
        if (current is EngineState.Finished) {
            return current.session
        }

        val session = when (current) {
            is EngineState.Running -> {
                val block = current.workout.blocks[current.idx]
                val consumed = (block.durationSec - current.remaining).coerceIn(0, block.durationSec)
                val partial = if (consumed > 0) {
                    CompletedSegment(current.idx, current.speed, consumed)
                } else {
                    null
                }
                sessionFrom(current.workout, partial, System.currentTimeMillis())
            }
            is EngineState.Paused -> {
                val block = current.workout.blocks[current.idx]
                val consumed = (block.durationSec - current.remaining).coerceIn(0, block.durationSec)
                val partial = if (consumed > 0) {
                    CompletedSegment(current.idx, current.speed, consumed)
                } else {
                    null
                }
                sessionFrom(current.workout, partial, System.currentTimeMillis())
            }
            is EngineState.Idle -> {
                sessionFrom(
                    workout = current.workout ?: return Session(
                        id = ensureSessionId(),
                        workoutId = null,
                        startedAt = startedAt.takeIf { it != 0L } ?: System.currentTimeMillis(),
                        endedAt = System.currentTimeMillis(),
                        unit = caps.unit,
                        segments = emptyList(),
                        workoutName = null
                    ),
                    extraSegment = null,
                    endedAt = System.currentTimeMillis()
                )
            }
            is EngineState.Finished -> current.session
        }

        state = EngineState.Finished(session)
        return session
    }

    fun isStarted(): Boolean = state !is EngineState.Idle

    fun currentSessionId(): String? = sessionId

    private fun ensureSessionId(): String = sessionId ?: UUID.randomUUID().toString().also { sessionId = it }

    private fun sessionFrom(
        workout: Workout,
        extraSegment: CompletedSegment?,
        endedAt: Long
    ): Session {
        val completed = buildList {
            addAll(segments)
            if (extraSegment != null) add(extraSegment)
        }
        val start = startedAt.takeIf { it != 0L } ?: (endedAt - completed.sumOf { it.durationSec } * 1000L)
        val session = Session(
            id = ensureSessionId(),
            workoutId = workout.id,
            startedAt = start,
            endedAt = endedAt,
            unit = caps.unit,
            segments = completed,
            workoutName = workout.name
        )
        return session
    }

    private fun speedFor(b: Block): Double = when (b) {
        is SteadyBlock -> DiscreteSpeedMapper.map(b.targetSpeed, caps, policy)
        is RampBlock -> DiscreteSpeedMapper.map(b.fromSpeed, caps, policy) // MVP: treat ramp as steady(start)
    }
}
