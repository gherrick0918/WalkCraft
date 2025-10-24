package com.walkcraft.app.health

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StepsSessionViewModel(app: Application) : AndroidViewModel(app) {

    private var startInstant: Instant = Instant.EPOCH

    private val client = HealthConnectClient.getOrCreate(app)

    private val appContext = getApplication<Application>()

    private val _session = MutableStateFlow(StepSessionState())
    val session: StateFlow<StepSessionState> = _session

    private val _todaySteps = MutableStateFlow<Long?>(null)
    val todaySteps: StateFlow<Long?> = _todaySteps

    data class SaveResult(val success: Boolean, val message: String)

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult

    private var pollJob: Job? = null
    private var startWallTimeMs: Long = 0L
    private val pollIntervalMs = 5_000L
    private val autoStopMinutes = 120L

    init {
        viewModelScope.launch {
            val stored = readStoredSession(appContext)
            if (stored.active && stored.startMs > 0L) {
                startWallTimeMs = stored.startMs
                startInstant = Instant.ofEpochMilli(stored.startMs)
                _session.value = StepSessionState(
                    active = true,
                    startEpochMs = stored.startMs,
                    baselineSteps = stored.baselineSteps,
                    latestSteps = stored.baselineSteps
                ).ticked()
                _todaySteps.value = stored.baselineSteps
                onResume()
            }
        }
    }

    fun reset() {
        stop(save = false)
        _session.value = StepSessionState().ticked()
    }

    fun stop(save: Boolean = true) {
        pollJob?.cancel()
        pollJob = null

        _session.value = _session.value.copy(active = false).ticked()

        viewModelScope.launch { clearStoredSession(appContext) }

        if (save) {
            viewModelScope.launch {
                try {
                    _saveResult.value = saveCurrentSessionIfAllowed()
                } catch (t: Throwable) {
                    _saveResult.value = SaveResult(false, "Save failed: ${t.message}")
                }
            }
        }
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
            startInstant = Instant.ofEpochMilli(startWallTimeMs)
            _saveResult.value = null

            _session.value = StepSessionState(
                active = true,
                startEpochMs = startWallTimeMs,
                baselineSteps = baseline,
                latestSteps = baseline,
            ).ticked()
            _todaySteps.value = baseline

            writeStoredSession(
                appContext,
                StoredSession(true, startWallTimeMs, baseline)
            )

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

    private suspend fun saveCurrentSessionIfAllowed(): SaveResult {
        if (startInstant == Instant.EPOCH) {
            return SaveResult(false, "No session to save.")
        }
        val canWrite = HealthConnectHelper.hasWriteExercisePermission(client)
        if (!canWrite) return SaveResult(false, "Missing permission to save session.")

        val end = Instant.now()
        val zoneRules = ZoneId.systemDefault().rules
        val startOffset: ZoneOffset = zoneRules.getOffset(startInstant)
        val endOffset: ZoneOffset = zoneRules.getOffset(end)
        val record = ExerciseSessionRecord(
            startTime = startInstant,
            endTime = end,
            startZoneOffset = startOffset,
            endZoneOffset = endOffset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            title = "WalkCraft Session"
        )
        client.insertRecords(listOf(record))
        return SaveResult(true, "Session saved to Health Connect.")
    }

    fun StepSessionState.ticked(now: Long = System.currentTimeMillis()) =
        copy(lastTickMs = now)
}

