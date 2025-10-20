package com.walkcraft.app.domain.format

import com.walkcraft.app.domain.model.Block
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.domain.model.SteadyBlock
import kotlin.math.roundToInt

object Spoken {
    fun blockIntro(block: Block, unit: SpeedUnit): String {
        val minutes = block.durationSec / 60
        val seconds = block.durationSec % 60
        val parts = mutableListOf<String>()
        if (minutes > 0) {
            parts += if (minutes == 1) "1 minute" else "$minutes minutes"
        }
        if (seconds > 0) {
            parts += if (seconds == 1) "1 second" else "$seconds seconds"
        }
        val dur = if (parts.isEmpty()) "0 seconds" else parts.joinToString(" ")
        val speedPart = when (block) {
            is SteadyBlock -> {
                val v = (block.targetSpeed * 10.0).roundToInt() / 10.0
                val u = if (unit == SpeedUnit.MPH) "miles per hour" else "kilometers per hour"
                "at $v $u"
            }
            else -> "at target speed"
        }
        return "${block.label}, $dur, $speedPart."
    }
}
