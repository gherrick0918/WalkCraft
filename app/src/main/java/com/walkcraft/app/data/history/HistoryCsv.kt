package com.walkcraft.app.data.history

import com.walkcraft.app.domain.metric.Distance
import com.walkcraft.app.domain.model.Session

object HistoryCsv {
    fun build(sessions: List<Session>): String {
        val sb = StringBuilder("start,end,unit,totalSec,distance,segments\n")
        sessions.forEach { s ->
            val totalSec = s.segments.sumOf { it.durationSec }
            val dist = Distance.of(s)
            val segStr = s.segments.joinToString("|") { "${it.blockIndex}:${it.actualSpeed}:${it.durationSec}" }
            sb.append("${s.startedAt},${s.endedAt},${s.unit},${totalSec},${"%.3f".format(dist)},${segStr}\n")
        }
        return sb.toString()
    }
}
