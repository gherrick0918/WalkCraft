package com.walkcraft.app.data.export

import com.walkcraft.app.domain.model.CompletedSegment
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {
    @Test
    fun sessionCsvContainsSummaryAndSegments() {
        val segments = listOf(
            CompletedSegment(0, 3.0, 600),
            CompletedSegment(1, 3.5, 300),
            CompletedSegment(2, 4.0, 300)
        )
        val session = Session(
            id = "session-1",
            workoutId = "workout-1",
            startedAt = 1_000_000L,
            endedAt = 1_000_000L + 1_200_000L,
            unit = SpeedUnit.MPH,
            segments = segments,
            notes = "Morning walk",
            workoutName = "Morning Stroll"
        )

        val csv = CsvExporter.sessionToCsv(session)
        val lines = csv.trim().split('\n')
        assertEquals("Workout,Started At,Ended At,Total Seconds,Unit,Distance", lines[0])
        val detail = lines[1].split(',')
        assertEquals("Morning Stroll", detail[0])
        assertEquals("1200", detail[3])
        assertEquals("MPH", detail[4])
        val expectedMiles = segments.sumOf { it.actualSpeed * it.durationSec } / 3600.0
        assertEquals(expectedMiles, detail[5].toDouble(), 0.01)

        assertEquals("", lines[2])
        assertEquals("Block,Speed (MPH),Duration (sec)", lines[3])
        val segmentLines = lines.drop(4)
        assertEquals(segments.size, segmentLines.size)
        assertEquals("0,3,600", segmentLines[0])
        assertEquals("1,3.5,300", segmentLines[1])
        assertEquals("2,4,300", segmentLines[2])
    }

    @Test
    fun allSessionsCsvIncludesSummaryRows() {
        val segments = listOf(
            CompletedSegment(0, 3.0, 600),
            CompletedSegment(1, 3.5, 300)
        )
        val session = Session(
            id = "session-2",
            workoutId = null,
            startedAt = 2_000_000L,
            endedAt = 2_000_000L + 900_000L,
            unit = SpeedUnit.MPH,
            segments = segments,
            notes = null,
            workoutName = "Evening Walk"
        )

        val csv = CsvExporter.allSessionsToCsv(listOf(session))
        val lines = csv.trim().split('\n')
        assertEquals("Session Id,Workout,Started At,Ended At,Total Seconds,Unit,Distance", lines[0])
        val data = lines[1].split(',')
        assertEquals("session-2", data[0])
        assertEquals("Evening Walk", data[1])
        assertEquals("900", data[4])
        val expectedMiles = segments.sumOf { it.actualSpeed * it.durationSec } / 3600.0
        assertEquals(expectedMiles, data[6].toDouble(), 0.01)
    }
}
