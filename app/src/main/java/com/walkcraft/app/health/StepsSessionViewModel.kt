package com.walkcraft.app.health

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.time.Instant
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.StepsRecord

class StepsSessionViewModel(app: Application) : AndroidViewModel(app) {

    private var startInstant: Instant = Instant.EPOCH

    private val client = HealthConnectClient.getOrCreate(app)

    private val _session = MutableStateFlow(StepSessionState())
    val session: StateFlow<StepSessionState> = _session

    private val _todaySteps = MutableStateFlow<Long?>(null)
    val todaySteps: StateFlow<Long?> = _todaySteps

    private var pollJob: Job? = null
    private var startWallTimeMs: Long = 0L
    private val pollIntervalMs = 5_000L
    private val autoStopMinutes = 120L

    fun reset() {
        stop()
        _session.value = StepSessionState()
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null

        val elapsed = if (_session.value.active) {
            System.currentTimeMillis() - startWallTimeMs
        } else {
            _session.value.elapsedMs
        }

        _session.value = _session.value.copy(active = false).ticked()
    }

    fun refreshToday() {
        viewModelScope.launch {
            if (HealthConnectHelper.hasAllPermissions(client)) {
                _todaySteps.value = HealthConnectHelper.readTodaySteps(client)
            }
        }
    }

    fun start() {
        viewModelScope.launch {
            if (!HealthConnectHelper.hasAllPermissions(client)) return@launch
            val baseline = HealthConnectHelper.readTodaySteps(client)
            startWallTimeMs = System.currentTimeMillis()
            startInstant = Instant.now()

            _session.value = StepSessionState(
                active = true,
                startEpochMs = startWallTimeMs,
                baselineSteps = baseline,
                latestSteps = baseline,
            ).ticked()
            _todaySteps.value = baseline

            startPolling()
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return

        pollJob = viewModelScope.launch {
            while (true) {
                val elapsedMs = System.currentTimeMillis() - startWallTimeMs
                val elapsedMin = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)

                if (!_session.value.active || elapsedMin >= autoStopMinutes) {
                    _session.value = _session.value.copy(active = false).ticked()
                    pollJob?.cancel()
                    break
                }

                if (HealthConnectHelper.hasAllPermissions(client)) {
                    val delta = stepsSinceStart() // <- aggregate since startInstant
                    val latest = _session.value.baselineSteps + delta
                    _session.value = _session.value.copy(latestSteps = latest).ticked()

                    // keep the “Today’s steps” label up to date (optional but nice)
                    _todaySteps.value = HealthConnectHelper.readTodaySteps(client)
                }

                delay(pollIntervalMs)
            }
        }
    }

    fun onResume() {
        viewModelScope.launch {
            if (_session.value.active) {
                val elapsedMs = System.currentTimeMillis() - startWallTimeMs
                if (_session.value.active && HealthConnectHelper.hasAllPermissions(client)) {
                    val delta = stepsSinceStart()
                    val latest = _session.value.baselineSteps + delta
                    _session.value = _session.value.copy(latestSteps = latest).ticked()
                    _todaySteps.value = HealthConnectHelper.readTodaySteps(client)
                    if (pollJob?.isActive != true) startPolling()
                } else {
                    _session.value = _session.value.copy().ticked()
                }
            } else if (HealthConnectHelper.hasAllPermissions(client)) {
                _todaySteps.value = HealthConnectHelper.readTodaySteps(client)
            }
        }
    }

    private suspend fun stepsSinceStart(): Long {
        if (startInstant == Instant.EPOCH) return 0L
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startInstant, Instant.now())
            )
        )
        return result[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    fun StepSessionState.ticked(now: Long = System.currentTimeMillis()) =
        copy(lastTickMs = now)
}

