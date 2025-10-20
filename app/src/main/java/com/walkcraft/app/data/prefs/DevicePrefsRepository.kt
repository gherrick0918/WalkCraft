package com.walkcraft.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "device_settings")

data class DeviceSettings(
    val caps: DeviceCapabilities,
    val policy: SpeedPolicy
)

class DevicePrefsRepository private constructor(private val context: Context) {

    private object Keys {
        val UNIT = stringPreferencesKey("unit")
        val MODE = stringPreferencesKey("mode")
        val MIN = doublePreferencesKey("min")
        val MAX = doublePreferencesKey("max")
        val INC = doublePreferencesKey("increment")
        val ALLOWED = stringPreferencesKey("allowed_csv")
        val STRATEGY = stringPreferencesKey("strategy")
    }

    private val defaultSettings = DeviceSettings(
        caps = DeviceCapabilities(
            unit = SpeedUnit.MPH,
            mode = DeviceCapabilities.Mode.DISCRETE,
            allowed = listOf(2.0, 2.5, 3.0, 3.5)
        ),
        policy = SpeedPolicy(strategy = SpeedPolicy.Strategy.NEAREST)
    )

    val settingsFlow: Flow<DeviceSettings> = context.dataStore.data.map { p ->
        read(p)
    }

    suspend fun save(settings: DeviceSettings) {
        context.dataStore.edit { p ->
            p[Keys.UNIT] = settings.caps.unit.name
            p[Keys.MODE] = settings.caps.mode.name
            when (settings.caps.mode) {
                DeviceCapabilities.Mode.DISCRETE -> {
                    p.remove(Keys.MIN); p.remove(Keys.MAX); p.remove(Keys.INC)
                    p[Keys.ALLOWED] = settings.caps.allowed?.joinToString(",") ?: ""
                }
                DeviceCapabilities.Mode.INCREMENT -> {
                    p.remove(Keys.ALLOWED)
                    settings.caps.min?.let { p[Keys.MIN] = it }
                    settings.caps.max?.let { p[Keys.MAX] = it }
                    settings.caps.increment?.let { p[Keys.INC] = it }
                }
            }
            p[Keys.STRATEGY] = settings.policy.strategy.name
        }
    }

    private fun read(p: Preferences): DeviceSettings {
        val unit = runCatching { SpeedUnit.valueOf(p[Keys.UNIT] ?: defaultSettings.caps.unit.name) }
            .getOrDefault(SpeedUnit.MPH)
        val mode = runCatching { DeviceCapabilities.Mode.valueOf(p[Keys.MODE] ?: defaultSettings.caps.mode.name) }
            .getOrDefault(DeviceCapabilities.Mode.DISCRETE)

        val caps = when (mode) {
            DeviceCapabilities.Mode.DISCRETE -> {
                val csv = p[Keys.ALLOWED]
                val allowed = csv?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.sorted()
                    ?: defaultSettings.caps.allowed
                DeviceCapabilities(unit = unit, mode = mode, allowed = allowed)
            }
            DeviceCapabilities.Mode.INCREMENT -> {
                val min = p[Keys.MIN]
                val max = p[Keys.MAX]
                val inc = p[Keys.INC]
                if (min == null || max == null || inc == null) {
                    defaultSettings.caps.copy(unit = unit)
                } else {
                    DeviceCapabilities(unit = unit, mode = mode, min = min, max = max, increment = inc)
                }
            }
        }

        val policy = runCatching {
            SpeedPolicy(strategy = SpeedPolicy.Strategy.valueOf(p[Keys.STRATEGY] ?: defaultSettings.policy.strategy.name))
        }.getOrDefault(defaultSettings.policy)

        return DeviceSettings(caps, policy)
    }

    companion object {
        fun from(context: Context) = DevicePrefsRepository(context.applicationContext)
    }
}
