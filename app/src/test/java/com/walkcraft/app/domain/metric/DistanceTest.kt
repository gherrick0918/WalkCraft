package com.walkcraft.app.domain.metric

import com.walkcraft.app.domain.model.CompletedSegment
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {
    @Test
    fun mphMiles() {
        val s = Session(
            workoutId = "w",
            startedAt = 0L,
            endedAt = 0L,
            unit = SpeedUnit.MPH,
            segments = listOf(
                CompletedSegment(0, 3.0, 600),   // 3 mph * (10/60) h = 0.5 mi
                CompletedSegment(1, 2.0, 600)    // 2 mph * (10/60) h = 0.333... mi
            )
        )
        val d = Distance.of(s)
        assertEquals(0.8333, d, 1e-3)
    }

    @Test
    fun kphKm() {
        val s = Session(
            workoutId = "w",
            startedAt = 0L,
            endedAt = 0L,
            unit = SpeedUnit.KPH,
            segments = listOf(
                CompletedSegment(0, 5.0, 1800)   // 5 kph * 0.5h = 2.5 km
            )
        )
        val d = Distance.of(s)
        assertEquals(2.5, d, 1e-6)
    }
}
