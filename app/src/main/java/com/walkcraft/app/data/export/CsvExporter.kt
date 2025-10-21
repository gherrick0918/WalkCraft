package com.walkcraft.app.data.export

import com.walkcraft.app.domain.model.CompletedSegment
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedUnit
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CsvExporter {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val speedFormat = DecimalFormat("0.0", symbols).apply { isGroupingUsed = false }
    private val distanceFormat = DecimalFormat("0.00", symbols).apply { isGroupingUsed = false }

    fun sessionToCsv(session: Session): String {
        val builder = StringBuilder()
        builder.appendLine("block_index,label,speed,duration_sec,distance")
        session.segments.forEach { segment ->
            builder.appendLine(
                listOf(
                    segment.blockIndex.toString(),
                    formatLabel(segment),
                    speedFormat.format(segment.actualSpeed),
                    segment.durationSec.toString(),
                    distanceFormat.format(distanceFor(segment, session.unit)),
                ).joinToString(separator = ",")
            )
        }
        return builder.toString()
    }

    private fun formatLabel(segment: CompletedSegment): String {
        val raw = segment.label?.takeIf { it.isNotBlank() } ?: "Block ${segment.blockIndex}"
        return sanitize(raw)
    }

    private fun sanitize(value: String): String =
        if (value.contains(',') || value.contains('"')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun distanceFor(segment: CompletedSegment, unit: SpeedUnit): Double {
        val speedMph = if (unit == SpeedUnit.MPH) {
            segment.actualSpeed
        } else {
            segment.actualSpeed * 0.621371
        }
        return speedMph * (segment.durationSec / 3600.0)
    }
}

fun Session.workoutDisplayName(): String = workoutName ?: notes ?: "WalkCraft Session"

fun Session.shortId(): String =
    id.takeIf { it.isNotBlank() }?.take(8) ?: hashCode().toString().take(8)
