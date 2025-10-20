package com.walkcraft.app.domain.engine

import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.domain.model.SteadyBlock
import com.walkcraft.app.domain.model.Workout
import kotlin.test.assertEquals
import org.junit.Test

class WorkoutEngineTest {

    @Test
    fun completes_full_block_duration_no_off_by_one() {
        val caps = DeviceCapabilities(
            unit = SpeedUnit.MPH,
            mode = DeviceCapabilities.Mode.DISCRETE,
            allowed = listOf(0.6, 2.2, 3.8)
        )
        val engine = WorkoutEngine(caps, SpeedPolicy())
        val w = Workout(
            id = "w",
            name = "Test",
            blocks = listOf(SteadyBlock("Warmup", 120, 0.6))
        )

        engine.start(w)
        repeat(120) { engine.tick(1) }

        val fin = engine.current() as EngineState.Finished
        assertEquals(1, fin.session.segments.size)
        assertEquals(120, fin.session.segments.first().durationSec)
    }
}
