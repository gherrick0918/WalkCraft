package com.walkcraft.app.session

import android.content.Context
import androidx.datastore.preferences.core.LongPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

// File-scoped extension (use from both Service and ViewModel)
val Context.sessionLiveDataStore by preferencesDataStore(name = "walkcraft_session_live")

private val KEY_LOCAL_DELTA = LongPreferencesKey("local_delta_steps")

// write
suspend fun writeLocalDelta(ctx: Context, value: Long) {
    ctx.sessionLiveDataStore.edit { it[KEY_LOCAL_DELTA] = value.coerceAtLeast(0) }
}

// clear
suspend fun clearLocalDelta(ctx: Context) {
    ctx.sessionLiveDataStore.edit { it[KEY_LOCAL_DELTA] = 0L }
}

// observe
fun liveDeltaFlow(ctx: Context) =
    ctx.sessionLiveDataStore.data.map { prefs -> prefs[KEY_LOCAL_DELTA] ?: 0L }
