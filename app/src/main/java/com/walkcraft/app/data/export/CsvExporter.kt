package com.walkcraft.app.data.export

import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedUnit
import java.text.DecimalFormat

object CsvExporter {
    private val speedFmt = DecimalFormat("#.###")
    private val distFmt = DecimalFormat("#.##")

    fun sessionToCsv(session: Session): String {
        val sb = StringBuilder()
        sb.appendLine("Workout,Started At,Ended At,Total Seconds,Unit,Distance")
        val totalSec = ((session.endedAt - session.startedAt) / 1000L).coerceAtLeast(0)
        val distance = estimateDistance(session)
        sb.appendLine(
            "${safe(session.workoutDisplayName())},${session.startedAt},${session.endedAt},$totalSec,${session.unit},${distFmt.format(distance)}"
        )
        sb.appendLine()
        sb.appendLine("Block,Speed (${session.unit}),Duration (sec)")
        session.segments.forEach { seg ->
            sb.appendLine("${seg.blockIndex},${speedFmt.format(seg.actualSpeed)},${seg.durationSec}")
        }
        return sb.toString()
    }

    fun allSessionsToCsv(sessions: List<Session>): String {
        val sb = StringBuilder()
        sb.appendLine("Session Id,Workout,Started At,Ended At,Total Seconds,Unit,Distance")
        sessions.forEach { s ->
            val totalSec = ((s.endedAt - s.startedAt) / 1000L).coerceAtLeast(0)
            val distance = estimateDistance(s)
            sb.appendLine(
                "${s.idOrHash()},${safe(s.workoutDisplayName())},${s.startedAt},${s.endedAt},$totalSec,${s.unit},${distFmt.format(distance)}"
            )
        }
        return sb.toString()
    }

    private fun estimateDistance(session: Session): Double {
        val mphSeconds = session.segments.sumOf { it.actualSpeed * it.durationSec }
        val miles = mphSeconds / 3600.0
        return if (session.unit == SpeedUnit.MPH) {
            miles
        } else {
            miles * 1.60934
        }
    }

    private fun safe(value: String?): String = value?.replace(",", " ") ?: ""
}

fun Session.workoutDisplayName(): String = workoutName ?: notes ?: "WalkCraft Session"

fun Session.idOrHash(): String = id.ifEmpty { hashCode().toString() }
