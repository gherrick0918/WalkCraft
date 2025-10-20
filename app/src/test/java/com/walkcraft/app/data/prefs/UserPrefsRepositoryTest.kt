package com.walkcraft.app.data.prefs

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPrefsRepositoryTest {
    @Test fun defaultsAndSet() = runBlocking {
        val repo = UserPrefsRepository.from(ApplicationProvider.getApplicationContext())
        assertEquals(false, repo.audioMutedFlow.first())
        assertEquals(false, repo.prerollEnabledFlow.first())
        repo.setAudioMuted(true)
        repo.setPrerollEnabled(true)
        assertEquals(true, repo.audioMutedFlow.first())
        assertEquals(true, repo.prerollEnabledFlow.first())
    }
}
