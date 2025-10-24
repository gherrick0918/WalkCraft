package com.walkcraft.app.health

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private const val FILE = "walkcraft_session"

val Context.sessionDataStore by preferencesDataStore(name = FILE)

private val KEY_ACTIVE = booleanPreferencesKey("active")
private val KEY_START_MS = longPreferencesKey("start_ms")
private val KEY_BASELINE_STEPS = longPreferencesKey("baseline_steps")

data class StoredSession(val active: Boolean, val startMs: Long, val baselineSteps: Long)

suspend fun readStoredSession(ctx: Context): StoredSession {
    val prefs = ctx.sessionDataStore.data.first()
    return StoredSession(
        active = prefs[KEY_ACTIVE] ?: false,
        startMs = prefs[KEY_START_MS] ?: 0L,
        baselineSteps = prefs[KEY_BASELINE_STEPS] ?: 0L
    )
}

suspend fun writeStoredSession(ctx: Context, value: StoredSession) {
    ctx.sessionDataStore.edit {
        it[KEY_ACTIVE] = value.active
        it[KEY_START_MS] = value.startMs
        it[KEY_BASELINE_STEPS] = value.baselineSteps
    }
}

suspend fun clearStoredSession(ctx: Context) {
    ctx.sessionDataStore.edit { it.clear() }
}
