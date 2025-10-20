package com.walkcraft.app.domain.plan

import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.domain.model.SteadyBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class PlansTest {

    @Test
    fun quickStart_maps_speeds_and_durations() {
        val caps = DeviceCapabilities(
            unit = SpeedUnit.MPH,
            mode = DeviceCapabilities.Mode.DISCRETE,
            allowed = listOf(2.0, 2.5, 3.0, 3.5)
        )
        val workout = Plans.quickStart(
            easy = 2.1,
            hard = 3.4,
            minutes = 10,
            caps = caps,
            policy = SpeedPolicy(SpeedPolicy.Strategy.NEAREST)
        )

        assertEquals(3, workout.blocks.size)
        val warm = workout.blocks[0] as SteadyBlock
        val steady = workout.blocks[1] as SteadyBlock
        val cool = workout.blocks[2] as SteadyBlock

        assertEquals(120, warm.durationSec)
        assertEquals(360, steady.durationSec)
        assertEquals(120, cool.durationSec)

        assertEquals(2.0, warm.targetSpeed, 0.0)
        assertEquals(2.5, steady.targetSpeed, 0.0)
        assertEquals(2.0, cool.targetSpeed, 0.0)
    }
}
