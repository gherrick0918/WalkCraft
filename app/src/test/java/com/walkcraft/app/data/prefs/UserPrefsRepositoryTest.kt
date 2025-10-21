package com.walkcraft.app.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class UserPrefsRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearDataStore()
    }

    @After
    fun tearDown() {
        clearDataStore()
    }

    @Test
    fun healthConnectFlagDefaultsToFalse() = runTest {
        val repo = UserPrefsRepository.from(context)
        assertFalse(repo.healthConnectEnabledFlow.first())
    }

    @Test
    fun healthConnectFlagCanBeToggled() = runTest {
        val repo = UserPrefsRepository.from(context)
        repo.setHealthConnectEnabled(true)
        assertTrue(repo.healthConnectEnabledFlow.first())

        repo.setHealthConnectEnabled(false)
        assertFalse(repo.healthConnectEnabledFlow.first())
    }

    private fun clearDataStore() {
        val file = File(context.filesDir, "datastore/user_prefs.preferences_pb")
        if (file.exists()) {
            file.delete()
        }
    }
}
