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

class StepsSessionViewModel(app: Application) : AndroidViewModel(app) {

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

        _session.value = _session.value.copy(active = false, elapsedMs = elapsed.coerceAtLeast(0))
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

            _session.value = StepSessionState(
                active = true,
                startEpochMs = startWallTimeMs,
                baselineSteps = baseline,
                latestSteps = baseline,
                elapsedMs = 0L,
            )
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
                    _session.value = _session.value.copy(active = false, elapsedMs = elapsedMs.coerceAtLeast(0))
                    pollJob?.cancel()
                    break
                }

                if (HealthConnectHelper.hasAllPermissions(client)) {
                    val current = HealthConnectHelper.readTodaySteps(client)
                    _session.value = _session.value.copy(latestSteps = current, elapsedMs = elapsedMs)
                    _todaySteps.value = current
                } else {
                    _session.value = _session.value.copy(elapsedMs = elapsedMs)
                }

                delay(pollIntervalMs)
            }
        }
    }

    fun onResume() {
        viewModelScope.launch {
            if (_session.value.active) {
                val elapsedMs = System.currentTimeMillis() - startWallTimeMs
                if (HealthConnectHelper.hasAllPermissions(client)) {
                    val current = HealthConnectHelper.readTodaySteps(client)
                    _session.value = _session.value.copy(latestSteps = current, elapsedMs = elapsedMs)
                    _todaySteps.value = current
                    if (pollJob?.isActive != true) startPolling()
                } else {
                    _session.value = _session.value.copy(elapsedMs = elapsedMs)
                }
            } else if (HealthConnectHelper.hasAllPermissions(client)) {
                _todaySteps.value = HealthConnectHelper.readTodaySteps(client)
            }
        }
    }
}

