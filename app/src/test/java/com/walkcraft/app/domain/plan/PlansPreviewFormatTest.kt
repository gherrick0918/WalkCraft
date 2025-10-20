package com.walkcraft.app.domain.plan

import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class PlansPreviewFormatTest {

    private val caps = DeviceCapabilities(
        unit = SpeedUnit.MPH,
        mode = DeviceCapabilities.Mode.DISCRETE,
        allowed = listOf(2.0, 2.5, 3.0, 3.5)
    )
    private val policy = SpeedPolicy(strategy = SpeedPolicy.Strategy.NEAREST)

    @Test
    fun preview_includes_all_blocks_when_long_enough() {
        val preview = Plans.previewForQuickStart(
            easy = 2.1,
            hard = 3.4,
            minutes = 10,
            caps = caps,
            policy = policy
        )

        assertEquals(
            "Warmup 2:00 @ 2 mph • Steady 6:00 @ 2.5 mph • Cooldown 2:00 @ 2 mph",
            preview
        )
    }

    @Test
    fun preview_omits_steady_when_minutes_short() {
        val preview = Plans.previewForQuickStart(
            easy = 2.0,
            hard = 3.0,
            minutes = 4,
            caps = caps,
            policy = policy
        )

        assertEquals(
            "Warmup 2:00 @ 2 mph • Cooldown 2:00 @ 2 mph",
            preview
        )
    }
}
