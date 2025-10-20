package com.walkcraft.app.domain.plan

import com.walkcraft.app.domain.mapper.DiscreteSpeedMapper
import com.walkcraft.app.domain.model.Block
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SteadyBlock
import com.walkcraft.app.domain.model.Workout

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
}
