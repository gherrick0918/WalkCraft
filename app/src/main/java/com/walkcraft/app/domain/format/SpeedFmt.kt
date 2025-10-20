package com.walkcraft.app.domain.format

import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedUnit
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

object SpeedFmt {
    /**
     * Pretty print a speed using either device increment decimals (if provided),
     * otherwise 1 decimal (e.g., 2.2). Trims trailing zeros.
     */
    @Suppress("UNUSED_PARAMETER")
    fun pretty(value: Double, unit: SpeedUnit, caps: DeviceCapabilities?): String {
        val decimals = caps?.increment?.let { decsFromStep(it) } ?: 1
        val pow = 10.0.pow(decimals.toDouble())
        val rounded = (value * pow).roundToInt() / pow
        val fmt = String.format(Locale.US, "%.${decimals}f", rounded)
        return if (decimals == 0) fmt else fmt.trimEnd('0').trimEnd('.')
    }

    private fun decsFromStep(step: Double): Int {
        val stripped = BigDecimal.valueOf(step).stripTrailingZeros()
        return stripped.scale().coerceAtLeast(0).coerceAtMost(2)
    }
}
