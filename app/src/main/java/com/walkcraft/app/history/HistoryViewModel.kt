package com.walkcraft.app.history

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walkcraft.app.data.AppDb
import com.walkcraft.app.data.SessionEntity
import com.walkcraft.app.health.HealthConnectHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DaySteps(val date: LocalDate, val steps: Long)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val client = HealthConnectClient.getOrCreate(app)

    private val _items = MutableStateFlow<List<DaySteps>>(emptyList())
    val items: StateFlow<List<DaySteps>> = _items

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _sessions = MutableStateFlow<List<SessionRow>>(emptyList())
    val sessions: StateFlow<List<SessionRow>> = _sessions

    data class SessionRow(
        val startText: String,
        val durationText: String,
        val steps: Long,
        val savedToHc: Boolean
    )

    fun load(days: Int = 7) {
        viewModelScope.launch {
            _error.value = null
            _loading.value = true
            try {
                val ctx = getApplication<Application>()
                val has = HealthConnectHelper.hasAllPermissions(client)
                if (!has) {
                    _items.value = emptyList()
                    _error.value = "Steps permission not granted."
                } else {
                    val list = HealthConnectHelper.readStepsLastNDays(client, days)
                        .map { DaySteps(it.first, it.second) }
                    _items.value = list
                }

                val dao = AppDb.get(ctx).sessions()
                val recent = dao.recent(50)
                _sessions.value = recent.map { it.toRow() }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Failed to load history."
            } finally {
                _loading.value = false
            }
        }
    }

    private fun SessionEntity.toRow(): SessionRow {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(startMs).atZone(zone)
        val end = Instant.ofEpochMilli(endMs).atZone(zone)
        val durSec = (endMs - startMs) / 1000
        val h = durSec / 3600
        val m = (durSec % 3600) / 60
        val s = durSec % 60

        val fmt = DateTimeFormatter.ofPattern("EEE, MMM d • HH:mm")
        return SessionRow(
            startText = start.format(fmt),
            durationText = "%d:%02d:%02d".format(h, m, s),
            steps = steps,
            savedToHc = savedToHc
        )
    }
}
