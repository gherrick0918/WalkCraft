package com.walkcraft.app.domain.mapper

import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import kotlin.math.abs

object DiscreteSpeedMapper {

    fun map(
        target: Double,
        caps: DeviceCapabilities,
        policy: SpeedPolicy = SpeedPolicy()
    ): Double {
        val pool: List<Double> = when (caps.mode) {
            DeviceCapabilities.Mode.INCREMENT -> {
                val min = caps.min ?: error("min required")
                val max = caps.max ?: error("max required")
                val inc = caps.increment ?: error("increment required")

                // Build [min, max] inclusive with step inc, normalized to 0.001 precision
                val values = mutableListOf<Double>()
                var v = min
                while (v <= max + 1e-9) {
                    values += ((v * 1000.0).toInt() / 1000.0)
                    v += inc
                }
                values
            }
            DeviceCapabilities.Mode.DISCRETE ->
                (caps.allowed ?: emptyList()).sorted()
        }

        require(pool.isNotEmpty()) { "No allowable speeds configured" }

        return when (policy.strategy) {
            SpeedPolicy.Strategy.NEAREST ->
                pool.minByOrNull { abs(it - target) }!!
            SpeedPolicy.Strategy.DOWN ->
                pool.filter { it <= target }.maxOrNull() ?: pool.first()
            SpeedPolicy.Strategy.UP ->
                pool.filter { it >= target }.minOrNull() ?: pool.last()
        }
    }
}
