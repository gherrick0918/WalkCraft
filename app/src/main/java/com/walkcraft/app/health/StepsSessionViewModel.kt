package com.walkcraft.app.health

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walkcraft.app.data.AppDb
import com.walkcraft.app.data.SessionEntity
import com.walkcraft.app.session.SessionFgService
import com.walkcraft.app.session.StepBus
import com.walkcraft.app.session.liveDeltaFlow
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private var busJob: Job? = null
    private var liveJob: Job? = null
    private var startWallTimeMs: Long = 0L                     // for elapsed clock
    private var startInstant: Instant = Instant.EPOCH          // for aggregates
    private var uiVisible = false
    private var lastHcReadMs = 0L
    private var lastLiveSteps: Long? = null

    private val pollIntervalMs = 15_000L                       // battery-friendly / near write cadence
    private val autoStopMinutes = 120L                         // guardrail

    private val appContext = getApplication<Application>()
    private var tickerJob: Job? = null

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
                lastLiveSteps = stored.baselineSteps
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
            lastLiveSteps = baseline

            // Persist so we can resume after process death / app close.
            writeStoredSession(
                appContext,
                StoredSession(active = true, startMs = startWallTimeMs, baselineSteps = baseline)
            )

            // Kick off live lock-screen/notification updates
            SessionFgService.start(appContext, startWallTimeMs, baseline)

            startTickerIfNeeded()
            startLiveCollector()
            startBusCollector()
            startPolling()
        }
    }

    fun stop(save: Boolean = true) {
        pollJob?.cancel()
        pollJob = null
        _session.value = _session.value.copy(active = false, lastTickMs = System.currentTimeMillis())
        stopBusCollector()
        stopLiveCollector()
        stopTicker()
        lastLiveSteps = null
        viewModelScope.launch { clearStoredSession(appContext) }

        // Stop the foreground service
        SessionFgService.stop(appContext)

        // snapshot values
        val start = startWallTimeMs
        val end = System.currentTimeMillis()
        val steps = _session.value.sessionSteps

        viewModelScope.launch {
            var savedToHc = false
            if (save) {
                try {
                    val res = saveCurrentSessionIfAllowed()
                    _saveResult.value = res
                    savedToHc = res.success
                } catch (t: Throwable) {
                    _saveResult.value = SaveResult(false, "Save failed: ${t.message}")
                }
            }
            // Insert local history row regardless
            AppDb.get(appContext).sessions().insert(
                SessionEntity(
                    startMs = start,
                    endMs = end,
                    steps = steps,
                    savedToHc = savedToHc
                )
            )
        }
    }

    fun reset() {
        stop(save = false)
        _session.value = StepSessionState(lastTickMs = System.currentTimeMillis())
        stopBusCollector()
        stopLiveCollector()
        lastLiveSteps = null
    }

    fun refreshToday() {
        viewModelScope.launch {
            if (HealthConnectHelper.hasStepsPermission(client)) {
                _todaySteps.value = HealthConnectHelper.readTodaySteps(client)
            }
        }
    }

    private fun startBusCollector() {
        if (busJob?.isActive == true) return
        busJob = viewModelScope.launch {
            StepBus.delta.collectLatest { localDelta ->
                if (_session.value.active) {
                    applyLiveDelta(localDelta)
                }
            }
        }
    }

    private fun stopBusCollector() {
        busJob?.cancel()
        busJob = null
    }

    private fun startLiveCollector() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            liveDeltaFlow(appContext).collectLatest { localDelta ->
                if (_session.value.active) {
                    applyLiveDelta(localDelta)
                }
            }
        }
    }

    private fun stopLiveCollector() {
        liveJob?.cancel()
        liveJob = null
    }

    /** Called by the screen when it resumes (and also from init after restore). */
    fun onResume() {
        uiVisible = true
        startTickerIfNeeded()
        viewModelScope.launch {
            lastHcReadMs = 0L
            if (_session.value.active) {
                startLiveCollector()
                startBusCollector()
                val baseline = _session.value.baselineSteps
                val liveLatest = baseline + StepBus.delta.value
                applyLiveSnapshot(liveLatest)
                if (HealthConnectHelper.hasStepsPermission(client)) {
                    val deltaAgg = stepsSinceStart()
                    val today = HealthConnectHelper.readTodaySteps(client)
                    val deltaToday = (today - _session.value.baselineSteps).coerceAtLeast(0)
                    val delta = maxOf(deltaAgg, deltaToday)
                    val latest = _session.value.baselineSteps + delta
                    applyAggregateCandidate(latest, today)
                    lastHcReadMs = System.currentTimeMillis()
                } else {
                    _session.value = _session.value.copy(lastTickMs = System.currentTimeMillis())
                }
                if (pollJob?.isActive != true) startPolling()
                startTickerIfNeeded()
            } else if (HealthConnectHelper.hasStepsPermission(client)) {
                _todaySteps.value = HealthConnectHelper.readTodaySteps(client)
            }
        }
    }

    fun onPause() {
        uiVisible = false
        stopTicker()
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
                val now = System.currentTimeMillis()
                val elapsedMin = TimeUnit.MILLISECONDS.toMinutes(now - startWallTimeMs)

                if (!_session.value.active || elapsedMin >= autoStopMinutes) {
                    _session.value = _session.value.copy(active = false, lastTickMs = now)
                    pollJob?.cancel()
                    break
                }

                if (HealthConnectHelper.hasStepsPermission(client)) {
                    val doHeavy = now - lastHcReadMs >= pollIntervalMs
                    if (doHeavy) {
                        val deltaAgg = stepsSinceStart()
                        val today = HealthConnectHelper.readTodaySteps(client)
                        val deltaToday = (today - _session.value.baselineSteps).coerceAtLeast(0)
                        val delta = maxOf(deltaAgg, deltaToday)
                        val latest = _session.value.baselineSteps + delta
                        applyAggregateCandidate(latest, today, now)
                        lastHcReadMs = now
                    } else if (!uiVisible) {
                        _session.value = _session.value.copy(lastTickMs = now)
                    }
                } else if (!uiVisible) {
                    _session.value = _session.value.copy(lastTickMs = now)
                }

                delay(if (uiVisible) 1_000L else pollIntervalMs)
            }
        }
    }

    private fun startTickerIfNeeded() {
        if (!uiVisible) return
        if (!_session.value.active) return
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            try {
                while (uiVisible && _session.value.active) {
                    val now = System.currentTimeMillis()
                    _session.value = _session.value.copy(lastTickMs = now)
                    delay(1_000L)
                }
            } finally {
                tickerJob = null
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun applyLiveDelta(localDelta: Long) {
        val now = System.currentTimeMillis()
        val baseline = _session.value.baselineSteps
        val latest = baseline + localDelta
        val newLatest = maxOf(latest, _session.value.latestSteps)
        if (newLatest != _session.value.latestSteps) {
            _session.value = _session.value.copy(latestSteps = newLatest, lastTickMs = now)
        } else {
            _session.value = _session.value.copy(lastTickMs = now)
        }
        lastLiveSteps = newLatest
    }

    private fun applyLiveSnapshot(candidate: Long) {
        if (!_session.value.active) return
        val now = System.currentTimeMillis()
        val newLatest = maxOf(candidate, _session.value.latestSteps)
        if (newLatest != _session.value.latestSteps) {
            _session.value = _session.value.copy(latestSteps = newLatest, lastTickMs = now)
        } else {
            _session.value = _session.value.copy(lastTickMs = now)
        }
        lastLiveSteps = newLatest
    }

    private fun applyAggregateCandidate(candidate: Long, today: Long?, now: Long = System.currentTimeMillis()) {
        val liveFloor = lastLiveSteps ?: Long.MIN_VALUE
        val monotonic = maxOf(candidate, liveFloor, _session.value.latestSteps)
        if (monotonic != _session.value.latestSteps) {
            _session.value = _session.value.copy(latestSteps = monotonic, lastTickMs = now)
        } else {
            _session.value = _session.value.copy(lastTickMs = now)
        }
        if (today != null && monotonic == candidate) {
            _todaySteps.value = today
        }
        if (monotonic > liveFloor) {
            lastLiveSteps = monotonic
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
