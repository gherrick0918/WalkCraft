package com.walkcraft.app.domain.metric

import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedUnit
import kotlin.math.roundToInt

object Distance {
    /** Returns distance in the session's own unit (miles if MPH, kilometers if KPH). */
    fun of(session: Session): Double {
        // speed (unit/hour) * (sec / 3600)
        val total = session.segments.sumOf { it.actualSpeed * (it.durationSec / 3600.0) }
        return total
    }

    fun label(unit: SpeedUnit) = if (unit == SpeedUnit.MPH) "mi" else "km"

    /** Formats to at most one decimal (e.g., 1.0, 2.3). */
    fun pretty(value: Double): String {
        val rounded = ((value * 10.0).roundToInt()) / 10.0
        return "%.1f".format(rounded)
    }
}
