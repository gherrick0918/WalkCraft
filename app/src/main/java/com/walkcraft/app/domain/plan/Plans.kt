package com.walkcraft.app.domain.plan

import com.walkcraft.app.domain.mapper.DiscreteSpeedMapper
import com.walkcraft.app.domain.format.SpeedFmt
import com.walkcraft.app.domain.model.Block
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SteadyBlock
import com.walkcraft.app.domain.model.Workout
import com.walkcraft.app.domain.model.RampBlock

object Plans {

    /**
     * Builds a simple plan:
     *  - 2 min Warmup at mappedEasy
     *  - mid section Steady at mappedAverage(easy, hard) if time remains
     *  - 2 min Cooldown at mappedEasy
     */
    fun quickStart(
        easy: Double,
        hard: Double,
        minutes: Int,
        caps: DeviceCapabilities,
        policy: SpeedPolicy = SpeedPolicy()
    ): Workout {
        val totalSec = minutes.coerceAtLeast(1) * 60
        val warm = 120
        val cool = 120
        val mid = (totalSec - warm - cool).coerceAtLeast(0)

        val mappedEasy = DiscreteSpeedMapper.map(easy, caps, policy)
        val mappedHard = DiscreteSpeedMapper.map(hard, caps, policy)
        val avg = (mappedEasy + mappedHard) / 2.0
        val mappedAvg = DiscreteSpeedMapper.map(avg, caps, policy)

        val blocks = buildList<Block> {
            add(SteadyBlock("Warmup", warm, mappedEasy))
            if (mid > 0) add(SteadyBlock("Steady", mid, mappedAvg))
            add(SteadyBlock("Cooldown", cool, mappedEasy))
        }
        return Workout(
            id = java.util.UUID.randomUUID().toString(),
            name = "Quick Start ${minutes}min",
            blocks = blocks
        )
    }

    fun previewForQuickStart(
        easy: Double,
        hard: Double,
        minutes: Int,
        caps: DeviceCapabilities,
        policy: SpeedPolicy = SpeedPolicy(),
    ): String {
        val workout = quickStart(easy, hard, minutes, caps, policy)
        val includeSteady = minutes >= 5 && workout.blocks.any { it.label == "Steady" && it.durationSec > 0 }
        val unitLabel = SpeedFmt.unitLabel(caps.unit)

        fun duration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
        fun speed(block: Block): String {
            val value = when (block) {
                is SteadyBlock -> block.targetSpeed
                is RampBlock -> block.fromSpeed
            }
            val pretty = SpeedFmt.pretty(value, caps.unit, caps)
            return "$pretty $unitLabel"
        }

        return buildString {
            workout.blocks.forEach { block ->
                if (!includeSteady && block.label == "Steady") return@forEach
                if (isNotEmpty()) append(" • ")
                append(block.label)
                append(' ')
                append(duration(block.durationSec))
                append(" @ ")
                append(speed(block))
            }
        }
    }
}
