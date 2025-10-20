package com.walkcraft.app.domain.format

import com.walkcraft.app.domain.model.RampBlock
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.domain.model.SteadyBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTest {
    @Test
    fun intro_steady_mph() {
        val block = SteadyBlock("Warmup", 120, 0.62)
        val spoken = Spoken.blockIntro(block, SpeedUnit.MPH)
        assertEquals("Warmup, 2 minutes, at 0.6 miles per hour.", spoken)
    }

    @Test
    fun intro_ramp_kph() {
        val block = RampBlock("Ramp", 75, 0.5, 1.0)
        val spoken = Spoken.blockIntro(block, SpeedUnit.KPH)
        assertEquals("Ramp, 1 minute 15 seconds, at target speed.", spoken)
    }

    @Test
    fun intro_single_second() {
        val block = SteadyBlock("Sprint", 1, 5.46)
        val spoken = Spoken.blockIntro(block, SpeedUnit.MPH)
        assertEquals("Sprint, 1 second, at 5.5 miles per hour.", spoken)
    }
}
