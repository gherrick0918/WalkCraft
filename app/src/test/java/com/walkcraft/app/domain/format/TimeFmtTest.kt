package com.walkcraft.app.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFmtTest {
    @Test
    fun mmss() {
        assertEquals("0:05", TimeFmt.mmSs(5))
        assertEquals("1:00", TimeFmt.mmSs(60))
        assertEquals("1:05", TimeFmt.mmSs(65))
    }

    @Test
    fun hmms() {
        assertEquals("59:59", TimeFmt.hMmSs(3599))
        assertEquals("1:00:00", TimeFmt.hMmSs(3600))
        assertEquals("1:02:03", TimeFmt.hMmSs(3723))
    }
}
