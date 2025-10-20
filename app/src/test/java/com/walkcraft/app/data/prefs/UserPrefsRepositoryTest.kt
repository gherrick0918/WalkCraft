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
        assertEquals(false, repo.prerollEnabledFlow.first())
        repo.setAudioMuted(true)
        repo.setPrerollEnabled(true)
        assertEquals(true, repo.audioMutedFlow.first())
        assertEquals(true, repo.prerollEnabledFlow.first())
    }
}
