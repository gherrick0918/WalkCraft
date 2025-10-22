package com.walkcraft.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "health_prefs")

object HealthPrefs {
    private val KEY_HC_GRANTED = booleanPreferencesKey("hc_permissions_granted")

    fun grantedFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HC_GRANTED] ?: false }

    suspend fun setGranted(context: Context, granted: Boolean) {
        context.dataStore.edit { it[KEY_HC_GRANTED] = granted }
    }
}
