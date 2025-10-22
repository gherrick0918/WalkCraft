package com.walkcraft.app.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class UserPrefsRepositoryTest {
    @Test fun defaultsAndSet() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPrefsRepository.from(ctx)
        assertEquals(false, repo.audioMutedFlow.first())
        val config = repo.quickStartConfigFlow.first()
        assertEquals(false, config.preRoll)
        assertEquals(false, repo.prerollEnabledFlow.first())
        assertEquals(2.0, config.easy, 0.0)
        assertEquals(3.0, config.hard, 0.0)
        assertEquals(20, config.minutes)
        repo.setAudioMuted(true)
        repo.setPrerollEnabled(true)
        repo.updateQuickStartConfig { it.copy(easy = 2.5, hard = 3.5, minutes = 15) }
        assertEquals(true, repo.audioMutedFlow.first())
        assertEquals(true, repo.prerollEnabledFlow.first())
        val updated = repo.quickStartConfigFlow.first()
        assertEquals(true, updated.preRoll)
        assertEquals(2.5, updated.easy, 0.0)
        assertEquals(3.5, updated.hard, 0.0)
        assertEquals(15, updated.minutes)
    }
}
