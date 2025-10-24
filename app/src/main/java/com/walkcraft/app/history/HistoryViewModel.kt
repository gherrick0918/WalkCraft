package com.walkcraft.app.history

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walkcraft.app.health.HealthConnectHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DaySteps(val date: LocalDate, val steps: Long)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val client = HealthConnectClient.getOrCreate(app)

    private val _items = MutableStateFlow<List<DaySteps>>(emptyList())
    val items: StateFlow<List<DaySteps>> = _items

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load(days: Int = 7) {
        viewModelScope.launch {
            _error.value = null
            _loading.value = true
            try {
                val has = HealthConnectHelper.hasAllPermissions(client)
                if (!has) {
                    _items.value = emptyList()
                    _error.value = "Steps permission not granted."
                } else {
                    val list = HealthConnectHelper.readStepsLastNDays(client, days)
                        .map { DaySteps(it.first, it.second) }
                    _items.value = list
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Failed to load history."
            } finally {
                _loading.value = false
            }
        }
    }
}
