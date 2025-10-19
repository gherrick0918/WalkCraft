package com.walkcraft.app.domain.model

enum class SpeedUnit { MPH, KPH }

data class DeviceCapabilities(
    val unit: SpeedUnit,
    val mode: Mode, // INCREMENT or DISCRETE
    val min: Double? = null,
    val max: Double? = null,
    val increment: Double? = null,
    val allowed: List<Double>? = null
) {
    enum class Mode { INCREMENT, DISCRETE }
}

data class SpeedPolicy(
    val strategy: Strategy = Strategy.NEAREST, // or DOWN or UP
    val tolerance: Double = 0.0 // reserved for future
) {
    enum class Strategy { NEAREST, DOWN, UP }
}
