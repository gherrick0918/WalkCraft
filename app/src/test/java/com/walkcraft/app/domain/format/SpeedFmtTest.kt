package com.walkcraft.app.domain.format

import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedFmtTest {
    @Test
    fun oneDecimalDefault() {
        val s = SpeedFmt.pretty(2.199, SpeedUnit.MPH, null)
        assertEquals("2.2", s)
    }

    @Test
    fun incrementTwoDecimals() {
        val caps = DeviceCapabilities(
            unit = SpeedUnit.MPH,
            mode = DeviceCapabilities.Mode.INCREMENT,
            min = 0.6,
            max = 3.8,
            increment = 0.05
        )
        val s = SpeedFmt.pretty(2.125, SpeedUnit.MPH, caps)
        assertEquals("2.13", s)
    }
}
