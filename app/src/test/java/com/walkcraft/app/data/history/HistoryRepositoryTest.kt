package com.walkcraft.app.data.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.walkcraft.app.domain.model.CompletedSegment
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryRepositoryTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun insertIgnore_dropsDuplicateId() = runBlocking {
        val repo = HistoryRepository.from(app)
        repo.clear()
        val first = sampleSession("same-id")
        val duplicate = first.copy(endedAt = first.endedAt + 10_000)

        repo.insertIgnore(first)
        repo.insertIgnore(duplicate)

        val sessions = repo.allOnce()
        assertEquals(1, sessions.size)
        assertEquals(first, sessions.first())
    }

    private fun sampleSession(id: String): Session = Session(
        id = id,
        workoutId = "w",
        startedAt = 0L,
        endedAt = 1_000L,
        unit = SpeedUnit.MPH,
        segments = listOf(CompletedSegment(0, 3.0, 60))
    )
}
