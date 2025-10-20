package com.walkcraft.app.data.history

import com.walkcraft.app.domain.model.CompletedSegment
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCsvTest {
    @Test
    fun includesDistanceAndHeader() {
        val session = Session(
            workoutId = "w1",
            startedAt = 100L,
            endedAt = 200L,
            unit = SpeedUnit.MPH,
            segments = listOf(
                CompletedSegment(0, 3.0, 600),
                CompletedSegment(1, 2.0, 600)
            )
        )
        val csv = HistoryCsv.build(listOf(session))
        val rows = csv.trim().split('\n')
        assertTrue(rows.first().contains("distance"))
        val dist = 0.833333
        val expectedRow = "100,200,MPH,1200,${"%.3f".format(dist)},0:3.0:600|1:2.0:600"
        assertEquals(expectedRow, rows[1])
    }
}
