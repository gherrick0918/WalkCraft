package com.walkcraft.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefs by preferencesDataStore(name = "user_prefs")

class UserPrefsRepository private constructor(private val ctx: Context) {
    object Keys {
        val AUDIO_MUTED = booleanPreferencesKey("audio_muted")
        val PREROLL_ENABLED = booleanPreferencesKey("preroll_enabled")
    }

    val audioMutedFlow: Flow<Boolean> =
        ctx.userPrefs.data.map { it[Keys.AUDIO_MUTED] ?: false }

    val prerollEnabledFlow: Flow<Boolean> =
        ctx.userPrefs.data.map { it[Keys.PREROLL_ENABLED] ?: false }

    suspend fun setAudioMuted(muted: Boolean) {
        ctx.userPrefs.edit { it[Keys.AUDIO_MUTED] = muted }
    }

    suspend fun setPrerollEnabled(enabled: Boolean) {
        ctx.userPrefs.edit { it[Keys.PREROLL_ENABLED] = enabled }
    }

    companion object {
        fun from(ctx: Context) = UserPrefsRepository(ctx.applicationContext)
    }
}
