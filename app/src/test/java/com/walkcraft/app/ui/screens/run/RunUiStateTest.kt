package com.walkcraft.app.ui.screens.run

import com.walkcraft.app.domain.engine.EngineState
import com.walkcraft.app.domain.model.CompletedSegment
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.domain.model.SteadyBlock
import com.walkcraft.app.domain.model.Workout
import com.walkcraft.app.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class RunUiStateTest {

    @Test
    fun runningStateMapsToUi() {
        val workout = Workout(
            id = "w1",
            name = "Test",
            blocks = listOf(
                SteadyBlock("Warmup", 120, 3.0),
                SteadyBlock("Run", 60, 4.0)
            )
        )
        val state = EngineState.Running(workout, idx = 0, remaining = 75, speed = 3.0)

        val ui = RunUiState.from(state) as RunUiState.Running

        assertEquals("Warmup", ui.blockLabel)
        assertEquals("1:15", ui.remaining)
        assertEquals("3 mph", ui.speedText)
        assertEquals("Run", ui.nextLabel)
    }

    @Test
    fun pausedStateShowsRemainingTime() {
        val workout = Workout(
            id = "w2",
            name = "Test",
            blocks = listOf(
                SteadyBlock("Cool", 90, 2.5)
            )
        )
        val state = EngineState.Paused(workout, idx = 0, remaining = 30, speed = 2.5)

        val ui = RunUiState.from(state) as RunUiState.Paused

        assertEquals("Cool", ui.blockLabel)
        assertEquals("0:30", ui.remaining)
    }

    @Test
    fun finishedStateAggregatesDuration() {
        val session = Session(
            id = "session-1",
            workoutId = "w3",
            startedAt = 0L,
            endedAt = 0L,
            unit = SpeedUnit.MPH,
            segments = listOf(
                CompletedSegment(blockIndex = 0, actualSpeed = 3.0, durationSec = 120),
                CompletedSegment(blockIndex = 1, actualSpeed = 3.5, durationSec = 60)
            ),
            workoutName = "Test"
        )
        val state = EngineState.Finished(session)

        val ui = RunUiState.from(state) as RunUiState.Finished

        assertEquals("3:00", ui.summary)
    }
}
