package com.walkcraft.app.domain.format

object TimeFmt {
    /** mm:ss, clamped to non-negative. */
    fun mmSs(totalSec: Int): String {
        val t = totalSec.coerceAtLeast(0)
        val m = t / 60
        val s = t % 60
        return "%d:%02d".format(m, s)
    }

    /** h:mm:ss for anything >= hour; mm:ss otherwise. */
    fun hMmSs(totalSec: Int): String {
        val t = totalSec.coerceAtLeast(0)
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
