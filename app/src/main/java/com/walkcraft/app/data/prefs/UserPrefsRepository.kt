package com.walkcraft.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefs by preferencesDataStore(name = "user_prefs")

data class QuickStartConfig(
    val easy: Double = 2.0,
    val hard: Double = 3.0,
    val minutes: Int = 20,
    val preRoll: Boolean = false,
)

class UserPrefsRepository private constructor(private val ctx: Context) {
    object Keys {
        val AUDIO_MUTED = booleanPreferencesKey("audio_muted")
        val PREROLL_ENABLED = booleanPreferencesKey("preroll_enabled")
        val QUICK_EASY = doublePreferencesKey("quick_easy")
        val QUICK_HARD = doublePreferencesKey("quick_hard")
        val QUICK_MINUTES = intPreferencesKey("quick_minutes")
    }

    val audioMutedFlow: Flow<Boolean> =
        ctx.userPrefs.data.map { it[Keys.AUDIO_MUTED] ?: false }

    val quickStartConfigFlow: Flow<QuickStartConfig> = ctx.userPrefs.data.map { prefs ->
        QuickStartConfig(
            easy = prefs[Keys.QUICK_EASY] ?: 2.0,
            hard = prefs[Keys.QUICK_HARD] ?: 3.0,
            minutes = (prefs[Keys.QUICK_MINUTES] ?: 20).coerceAtLeast(1),
            preRoll = prefs[Keys.PREROLL_ENABLED] ?: false,
        )
    }

    val prerollEnabledFlow: Flow<Boolean> =
        quickStartConfigFlow.map { it.preRoll }

    suspend fun setAudioMuted(muted: Boolean) {
        ctx.userPrefs.edit { it[Keys.AUDIO_MUTED] = muted }
    }

    suspend fun setPrerollEnabled(enabled: Boolean) {
        updateQuickStartConfig { it.copy(preRoll = enabled) }
    }

    suspend fun updateQuickStartConfig(transform: (QuickStartConfig) -> QuickStartConfig) {
        ctx.userPrefs.edit { prefs ->
            val current = QuickStartConfig(
                easy = prefs[Keys.QUICK_EASY] ?: 2.0,
                hard = prefs[Keys.QUICK_HARD] ?: 3.0,
                minutes = (prefs[Keys.QUICK_MINUTES] ?: 20).coerceAtLeast(1),
                preRoll = prefs[Keys.PREROLL_ENABLED] ?: false,
            )
            val updated = transform(current)
            prefs[Keys.QUICK_EASY] = updated.easy
            prefs[Keys.QUICK_HARD] = updated.hard
            prefs[Keys.QUICK_MINUTES] = updated.minutes.coerceAtLeast(1)
            prefs[Keys.PREROLL_ENABLED] = updated.preRoll
        }
    }

    suspend fun setQuickStartConfig(config: QuickStartConfig) {
        updateQuickStartConfig { config }
    }

    companion object {
        fun from(ctx: Context) = UserPrefsRepository(ctx.applicationContext)
    }
}
