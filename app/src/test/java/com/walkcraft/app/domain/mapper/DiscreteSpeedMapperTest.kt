package com.walkcraft.app.domain.mapper

import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscreteSpeedMapperTest {

    @Test
    fun discrete_nearest_down_up() {
        val caps = DeviceCapabilities(
            unit = SpeedUnit.MPH,
            mode = DeviceCapabilities.Mode.DISCRETE,
            allowed = listOf(1.0, 2.0, 3.0, 3.5)
        )

        assertEquals(2.0, DiscreteSpeedMapper.map(2.4, caps, SpeedPolicy(SpeedPolicy.Strategy.NEAREST)), 0.0)
        assertEquals(3.0, DiscreteSpeedMapper.map(2.6, caps, SpeedPolicy(SpeedPolicy.Strategy.NEAREST)), 0.0)
        assertEquals(2.0, DiscreteSpeedMapper.map(2.4, caps, SpeedPolicy(SpeedPolicy.Strategy.DOWN)), 0.0)
        assertEquals(3.0, DiscreteSpeedMapper.map(2.6, caps, SpeedPolicy(SpeedPolicy.Strategy.UP)), 0.0)
    }

    @Test
    fun increment_mode_generates_pool() {
        val caps = DeviceCapabilities(
            unit = SpeedUnit.MPH,
            mode = DeviceCapabilities.Mode.INCREMENT,
            min = 1.0, max = 3.0, increment = 0.5
        )

        // 1.0, 1.5, 2.0, 2.5, 3.0
        assertEquals(2.0, DiscreteSpeedMapper.map(2.1, caps, SpeedPolicy(SpeedPolicy.Strategy.NEAREST)), 0.0)
        assertEquals(2.5, DiscreteSpeedMapper.map(2.6, caps, SpeedPolicy(SpeedPolicy.Strategy.NEAREST)), 0.0)
        assertEquals(2.5, DiscreteSpeedMapper.map(2.6, caps, SpeedPolicy(SpeedPolicy.Strategy.DOWN)), 0.0)
        assertEquals(3.0, DiscreteSpeedMapper.map(2.6, caps, SpeedPolicy(SpeedPolicy.Strategy.UP)), 0.0)
    }
}
