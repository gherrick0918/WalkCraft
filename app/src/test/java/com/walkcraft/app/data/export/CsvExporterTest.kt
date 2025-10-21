package com.walkcraft.app.data.export

import com.walkcraft.app.domain.model.CompletedSegment
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {

    @Test
    fun sessionCsv_hasHeaderRowsAndFormattedValues() {
        val session = Session(
            id = "session-1",
            workoutId = "workout-1",
            startedAt = 1_000L,
            endedAt = 2_000L,
            unit = SpeedUnit.MPH,
            segments = listOf(
                CompletedSegment(blockIndex = 0, actualSpeed = 3.04, durationSec = 600, label = "Warmup"),
                CompletedSegment(blockIndex = 1, actualSpeed = 4.05, durationSec = 300, label = "Steady"),
                CompletedSegment(blockIndex = 2, actualSpeed = 3.5, durationSec = 300)
            ),
            notes = "Morning walk"
        )

        val csv = CsvExporter.sessionToCsv(session)
        val lines = csv.trim().split('\n')

        assertEquals("block_index,label,speed,duration_sec,distance", lines.first())
        assertEquals(session.segments.size + 1, lines.size)

        val firstRow = lines[1].split(',')
        assertEquals(listOf("0", "Warmup", "3.0", "600", "0.51"), firstRow)

        val lastRow = lines.last().split(',')
        assertEquals("2", lastRow[0])
        assertEquals("Block 2", lastRow[1])
        assertEquals("3.5", lastRow[2])
        assertEquals("300", lastRow[3])
        assertEquals("0.29", lastRow[4])
    }

    @Test
    fun sessionCsv_convertsKphToMilesForDistance() {
        val session = Session(
            id = "kph-session",
            workoutId = null,
            startedAt = 0L,
            endedAt = 1_000L,
            unit = SpeedUnit.KPH,
            segments = listOf(
                CompletedSegment(blockIndex = 0, actualSpeed = 6.2, durationSec = 1_800, label = "Tempo")
            )
        )

        val csv = CsvExporter.sessionToCsv(session)
        val lines = csv.trim().split('\n')
        val row = lines[1].split(',')

        assertEquals("Tempo", row[1])
        assertEquals("6.2", row[2])
        assertEquals("1800", row[3])
        // 6.2 kph -> 3.85 mph, half-hour duration = 1.93 miles
        assertEquals("1.93", row[4])
    }
}
