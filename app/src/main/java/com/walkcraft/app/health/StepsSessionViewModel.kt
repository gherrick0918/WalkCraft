package com.walkcraft.app.health

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel that manages a walking "session" using Health Connect.
 * - Steps-only runtime requirement.
 * - Persists essential session info (DataStore) so process death / app close can resume on next launch.
 * - Polls gently while the Session screen is visible; catches up on resume.
 * - Computes session steps as max(aggregate since start, today's total - baseline).
 */
class StepsSessionViewModel(app: Application) : AndroidViewModel(app) {

    // --- Health Connect client ---
    private val client: HealthConnectClient = HealthConnectClient.getOrCreate(app)

    // --- Public state ---
    private val _session = MutableStateFlow(StepSessionState())
    val session: StateFlow<StepSessionState> = _session

    private val _todaySteps = MutableStateFlow<Long?>(null)
    val todaySteps: StateFlow<Long?> = _todaySteps

    data class SaveResult(val success: Boolean, val message: String)
    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult

    // --- Internal session timing/state ---
    private var pollJob: Job? = null
    private var startWallTimeMs: Long = 0L                     // for elapsed clock
    private var startInstant: Instant = Instant.EPOCH          // for aggregates

    private val pollIntervalMs = 15_000L                       // battery-friendly / near write cadence
    private val autoStopMinutes = 120L                         // guardrail

    private val appContext = getApplication<Application>()

    init {
        // Restore any persisted, active session and immediately catch up.
        viewModelScope.launch {
            val stored = readStoredSession(appContext)
            if (stored.active && stored.startMs > 0L) {
                startWallTimeMs = stored.startMs
                startInstant = Instant.ofEpochMilli(stored.startMs)
                _session.value = StepSessionState(
                    active = true,
                    startEpochMs = stored.startMs,
                    baselineSteps = stored.baselineSteps,
                    latestSteps = stored.baselineSteps,
                    lastTickMs = System.currentTimeMillis()
                )
                onResume() // one-shot catch-up + (re)start polling if needed
            }
        }
    }

    // --- Public API ---

    fun start() {
        viewModelScope.launch {
            if (!HealthConnectHelper.hasStepsPermission(client)) return@launch
            val baseline = HealthConnectHelper.readTodaySteps(client)
            startWallTimeMs = System.currentTimeMillis()
            startInstant = Instant.ofEpochMilli(startWallTimeMs)

            _session.value = StepSessionState(
                active = true,
                startEpochMs = startWallTimeMs,
                baselineSteps = baseline,
                latestSteps = baseline,
                lastTickMs = System.currentTimeMillis()
            )

            // Persist so we can resume after process death / app close.
            writeStoredSession(
                appContext,
                StoredSession(active = true, startMs = startWallTimeMs, baselineSteps = baseline)
            )

            startPolling()
        }
    }

    fun stop(save: Boolean = true) {
        pollJob?.cancel()
        pollJob = null
        _session.value = _session.value.copy(active = false, lastTickMs = System.currentTimeMillis())
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

    fun reset() {
        stop(save = false)
        _session.value = StepSessionState(lastTickMs = System.currentTimeMillis())
    }

    fun refreshToday() {
        viewModelScope.launch {
            if (HealthConnectHelper.hasStepsPermission(client)) {
                _todaySteps.value = HealthConnectHelper.readTodaySteps(client)
            }
        }
    }

    /** Called by the screen when it resumes (and also from init after restore). */
    fun onResume() {
        viewModelScope.launch {
            if (_session.value.active) {
                if (HealthConnectHelper.hasStepsPermission(client)) {
                    val deltaAgg = stepsSinceStart()
                    val today = HealthConnectHelper.readTodaySteps(client)
                    val deltaToday = (today - _session.value.baselineSteps).coerceAtLeast(0)
                    val delta = maxOf(deltaAgg, deltaToday)
                    val latest = _session.value.baselineSteps + delta

                    _session.value = _session.value.copy(
                        latestSteps = latest,
                        lastTickMs = System.currentTimeMillis()
                    )
                    _todaySteps.value = today
                } else {
                    // Even without permission, tick so elapsed updates visually.
                    _session.value = _session.value.copy(lastTickMs = System.currentTimeMillis())
                }
                // Ensure polling is running if we returned from background.
                if (pollJob?.isActive != true) startPolling()
            } else if (HealthConnectHelper.hasStepsPermission(client)) {
                _todaySteps.value = HealthConnectHelper.readTodaySteps(client)
            }
        }
    }

    /** Used by UI to decide whether to prompt for WRITE_EXERCISE before saving. */
    suspend fun needsWriteExercisePermission(): Boolean {
        return !HealthConnectHelper.hasWriteExercisePermission(client)
    }

    /** Used by UI to decide whether to prompt for the core steps permission before starting. */
    suspend fun needsStepsPermission(): Boolean {
        return !HealthConnectHelper.hasStepsPermission(client)
    }

    // --- Internals ---

    private fun startPolling() {
        if (pollJob?.isActive == true) return

        pollJob = viewModelScope.launch {
            while (true) {
                val elapsedMin = TimeUnit.MILLISECONDS.toMinutes(
                    System.currentTimeMillis() - startWallTimeMs
                )

                // Auto-stop guard.
                if (!_session.value.active || elapsedMin >= autoStopMinutes) {
                    _session.value = _session.value.copy(active = false, lastTickMs = System.currentTimeMillis())
                    pollJob?.cancel()
                    break
                }

                if (HealthConnectHelper.hasStepsPermission(client)) {
                    // 1) Aggregated delta since start
                    val deltaAgg = stepsSinceStart()
                    // 2) Fallback delta using today's total
                    val today = HealthConnectHelper.readTodaySteps(client)
                    val deltaToday = (today - _session.value.baselineSteps).coerceAtLeast(0)
                    // 3) Choose the best signal
                    val delta = maxOf(deltaAgg, deltaToday)
                    val latest = _session.value.baselineSteps + delta

                    _session.value = _session.value.copy(
                        latestSteps = latest,
                        lastTickMs = System.currentTimeMillis()
                    )
                    _todaySteps.value = today
                } else {
                    _session.value = _session.value.copy(lastTickMs = System.currentTimeMillis())
                }

                delay(pollIntervalMs)
            }
        }
    }

    /** Aggregate steps from session start to "now". */
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

    /** Attempt to persist the current session as an ExerciseSessionRecord (WALKING). */
    private suspend fun saveCurrentSessionIfAllowed(): SaveResult {
        if (startInstant == Instant.EPOCH) {
            return SaveResult(false, "No session to save.")
        }
        val canWrite = HealthConnectHelper.hasWriteExercisePermission(client)
        if (!canWrite) return SaveResult(false, "Missing permission to save session.")

        val end = Instant.now()
        val zone = ZoneId.systemDefault()
        val record = ExerciseSessionRecord(
            startTime = startInstant,
            endTime = end,
            startZoneOffset = zone.rules.getOffset(startInstant),
            endZoneOffset = zone.rules.getOffset(end),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            title = "WalkCraft Session"
        )
        client.insertRecords(listOf(record))
        return SaveResult(true, "Session saved to Health Connect.")
    }
}
