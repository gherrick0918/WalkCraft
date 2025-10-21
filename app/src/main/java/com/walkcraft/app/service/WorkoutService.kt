package com.walkcraft.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.walkcraft.app.R
import com.walkcraft.app.data.history.HistoryRepository
import com.walkcraft.app.data.prefs.DevicePrefsRepository
import com.walkcraft.app.data.prefs.DeviceSettings
import com.walkcraft.app.data.prefs.UserPrefsRepository
import com.walkcraft.app.domain.format.Spoken
import com.walkcraft.app.domain.engine.EngineState
import com.walkcraft.app.domain.engine.WorkoutEngine
import com.walkcraft.app.domain.model.Block
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SteadyBlock
import com.walkcraft.app.domain.model.Workout
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.domain.plan.Plans
import com.walkcraft.app.health.HealthConnectAvailability
import com.walkcraft.app.health.HealthConnectManager
import com.walkcraft.app.health.HealthSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import java.util.UUID

class WorkoutService : Service() {

    companion object {
        // NEW CHANNEL ID to avoid old importance: once set, system won't let us raise it in code
        const val CHANNEL_ID = "walkcraft.workouts.high"
        const val NOTIF_ID = 1001

        const val ACTION_START  = "com.walkcraft.app.action.START"
        const val ACTION_START_QUICK = "com.walkcraft.app.action.START_QUICK"
        const val ACTION_PAUSE  = "com.walkcraft.app.action.PAUSE"
        const val ACTION_RESUME = "com.walkcraft.app.action.RESUME"
        const val ACTION_SKIP   = "com.walkcraft.app.action.SKIP"
        const val ACTION_STOP   = "com.walkcraft.app.action.STOP"
        const val ACTION_MUTE   = "com.walkcraft.app.action.MUTE"
        const val ACTION_UNMUTE = "com.walkcraft.app.action.UNMUTE"

        private const val EXTRA_MINUTES = "extra_minutes"
        private const val EXTRA_EASY = "extra_easy"
        private const val EXTRA_HARD = "extra_hard"

        private const val TAG = "WorkoutService"

        fun start(context: Context) {
            val i = Intent(context, WorkoutService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }
        fun startQuick(context: Context, minutes: Int, easy: Double, hard: Double) {
            val i = Intent(context, WorkoutService::class.java)
                .setAction(ACTION_START_QUICK)
                .putExtra(EXTRA_MINUTES, minutes)
                .putExtra(EXTRA_EASY, easy)
                .putExtra(EXTRA_HARD, hard)
            ContextCompat.startForegroundService(context, i)
        }
        fun sendAction(context: Context, action: String) {
            context.startService(Intent(context, WorkoutService::class.java).setAction(action))
        }
    }

    sealed interface HealthTelemetry {
        data object Inactive : HealthTelemetry
        data object PermissionsNeeded : HealthTelemetry
        data class Active(val heartRateBpm: Int?, val totalSteps: Int?) : HealthTelemetry
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notifMgr: NotificationManager
    private lateinit var engine: WorkoutEngine
    private lateinit var userPrefs: UserPrefsRepository
    private val _states = MutableStateFlow<EngineState>(EngineState.Idle(null))
    fun states(): StateFlow<EngineState> = _states.asStateFlow()
    private val healthTelemetryState = MutableStateFlow<HealthTelemetry>(HealthTelemetry.Inactive)
    fun healthTelemetry(): StateFlow<HealthTelemetry> = healthTelemetryState.asStateFlow()
    private var audioEnabled = true
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private lateinit var audioMgr: AudioManager
    private var focusReq: AudioFocusRequest? = null
    private var lastAnnouncedBlockIndex: Int = -1
    private var timerJob: Job? = null
    private var hasPersisted: Boolean = false
    private var currentSessionId: String? = null
    private var preRollEnabled = false
    private var preRollJob: Job? = null
    private var latestSettings: DeviceSettings = DeviceSettings(
        caps = DeviceCapabilities(
            unit = SpeedUnit.MPH,
            mode = DeviceCapabilities.Mode.DISCRETE,
            allowed = listOf(2.0, 2.5, 3.0, 3.5)
        ),
        policy = SpeedPolicy()
    )
    private val history: HistoryRepository by lazy {
        HistoryRepository.from(applicationContext)
    }
    private val healthManager by lazy { HealthConnectManager(applicationContext) }
    private var healthJob: Job? = null
    private var healthConnectEnabled = false
    private var workoutStartInstant: Instant? = null
    private var latestHealthSummary: HealthSummary = HealthSummary()

    inner class LocalBinder : Binder() {
        fun service(): WorkoutService = this@WorkoutService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        notifMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()

        userPrefs = UserPrefsRepository.from(this)

        runBlocking {
            val deviceRepo = DevicePrefsRepository.from(this@WorkoutService)
            latestSettings = withContext(Dispatchers.IO) { deviceRepo.settingsFlow.first() }
            val audioMuted = withContext(Dispatchers.IO) { userPrefs.audioMutedFlow.first() }
            val quickConfig = withContext(Dispatchers.IO) { userPrefs.quickStartConfigFlow.first() }
            audioEnabled = !audioMuted
            preRollEnabled = quickConfig.preRoll
        }

        engine = WorkoutEngine(latestSettings.caps, latestSettings.policy)
        publishState(engine.current())
        startForeground(NOTIF_ID, buildNotification())

        audioMgr = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        ttsReady = false
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    engine.language = Locale.getDefault()
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}

                        override fun onDone(utteranceId: String?) {
                            abandonFocus()
                        }

                        override fun onError(utteranceId: String?) {
                            abandonFocus()
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            abandonFocus()
                        }
                    })
                }
                ttsReady = true
                Log.d(TAG, "TTS ready")
            } else {
                ttsReady = false
                Log.w(TAG, "TTS init failed: $status")
            }
        }
        tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

        scope.launch {
            DevicePrefsRepository.from(this@WorkoutService).settingsFlow.collect { settings ->
                val currentState = engine.current()
                val wasRunning = currentState is EngineState.Running || currentState is EngineState.Paused
                latestSettings = settings
                if (!wasRunning) {
                    engine = WorkoutEngine(settings.caps, settings.policy)
                    publishState(engine.current())
                    updateNotification()
                }
                Log.d(
                    TAG,
                    "settings updated; whileRunning=$wasRunning; caps=${latestSettings.caps}; policy=${latestSettings.policy}"
                )
            }
        }

        scope.launch {
            userPrefs.audioMutedFlow.collect { muted ->
                audioEnabled = !muted
                if (muted) {
                    tts?.stop()
                    tone?.stopTone()
                    abandonFocus()
                }
                updateNotification()
                Log.d(TAG, "audioEnabled=$audioEnabled (persisted)")
            }
        }
        scope.launch {
            userPrefs.prerollEnabledFlow.collect { enabled ->
                preRollEnabled = enabled
                Log.d(TAG, "preRollEnabled=$preRollEnabled (persisted)")
            }
        }
        scope.launch {
            userPrefs.healthConnectEnabledFlow.collect { enabled ->
                healthConnectEnabled = enabled
                if (!enabled) {
                    stopHealthCollection(resetSummary = true)
                    healthTelemetryState.value = HealthTelemetry.Inactive
                } else {
                    refreshHealthStatus()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val act = intent?.action
        Log.d(TAG, "onStartCommand action=$act extras=${intent?.extras?.keySet()?.joinToString()}")
        when {
            act == ACTION_START_QUICK || looksLikeQuickStart(intent) -> {
                preRollEnabled = runBlocking { userPrefs.quickStartConfigFlow.first().preRoll }
                val minutes = intent?.getIntExtra(EXTRA_MINUTES, 20) ?: 20
                val easy    = intent?.getDoubleExtra(EXTRA_EASY, 2.0) ?: 2.0
                val hard    = intent?.getDoubleExtra(EXTRA_HARD, 3.0) ?: 3.0
                Log.d(TAG, "handleStartQuick minutes=$minutes easy=$easy hard=$hard caps=${latestSettings.caps} policy=${latestSettings.policy}")
                handleStartQuick(minutes, easy, hard)
            }
            act == ACTION_START -> {
                Log.d(TAG, "handleStart (debug workout)")
                handleStart()
            }
            act == ACTION_PAUSE  -> {
                engine.pause()
                publishState()
                updateNotification()
                Log.d(TAG, "pause")
            }
            act == ACTION_RESUME -> {
                engine.resume()
                publishState()
                updateNotification()
                onTickAnnounce()
                Log.d(TAG, "resume")
            }
            act == ACTION_SKIP   -> {
                engine.skip()
                publishState()
                updateNotification()
                onTickAnnounce()
                Log.d(TAG, "skip")
            }
            act == ACTION_STOP   -> {
                Log.d(TAG, "stop requested")
                stopEngine(userInitiated = true)
                return START_NOT_STICKY
            }
            act == ACTION_MUTE   -> {
                scope.launch { userPrefs.setAudioMuted(true) }
                tts?.stop()
                tone?.stopTone()
                abandonFocus()
                updateNotification()
                Log.d(TAG, "audio=muted (persisted)")
            }
            act == ACTION_UNMUTE -> {
                scope.launch { userPrefs.setAudioMuted(false) }
                updateNotification()
                Log.d(TAG, "audio=unmuted (persisted)")
            }
            else -> {
                updateNotification()
            }
        }
        updateNotification()
        return START_NOT_STICKY
    }

    private fun looksLikeQuickStart(i: Intent?): Boolean {
        if (i == null) return false
        return i.hasExtra(EXTRA_MINUTES) || i.hasExtra(EXTRA_EASY) || i.hasExtra(EXTRA_HARD)
    }

    override fun onDestroy() {
        timerJob?.cancel()
        timerJob = null
        preRollJob?.cancel()
        stopHealthCollection(resetSummary = false)
        if (!hasPersisted && engine.isStarted()) {
            val session = engine.finishNow()
            persistOnce(session)
            resetHealthTelemetryAfterSession()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifMgr.cancel(NOTIF_ID)
        scope.cancel()
        ttsReady = false
        tts?.shutdown()
        tts = null
        tone?.stopTone()
        tone?.release()
        tone = null
        abandonFocus()
        publishState(EngineState.Idle(null))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // ---- internals ----

    private fun handleStart() {
        val s = engine.current()
        if (s !is EngineState.Running && s !is EngineState.Paused) {
            Log.d(TAG, "Starting debug workout")
            startWithOptionalPreroll(debugWorkout())
        } else {
            publishState(s)
            updateNotification()
        }
    }

    private fun handleStartQuick(minutes: Int, easy: Double, hard: Double) {
        val w = Plans.quickStart(
            easy = easy, hard = hard, minutes = minutes,
            caps = latestSettings.caps, policy = latestSettings.policy
        )
        startWithOptionalPreroll(w)
    }

    private fun startWithOptionalPreroll(workout: Workout) {
        preRollJob?.cancel()
        if (preRollEnabled && audioEnabled) {
            preRollJob = scope.launch {
                speakWhenReady("Starting in", flush = true, id = "preroll")
                delay(350)
                for (n in listOf(3, 2, 1)) {
                    if (!isActive) return@launch
                    speakWhenReady(n.toString(), id = "preroll-$n")
                    beep()
                    vibrate(60)
                    delay(850)
                }
                startWorkout(workout)
            }
        } else {
            startWorkout(workout)
        }
    }

    private fun startWorkout(w: Workout) {
        lastAnnouncedBlockIndex = -1
        hasPersisted = false
        engine.start(w)
        currentSessionId = engine.currentSessionId()
        publishState()
        startTimer()
        beginHealthCollectionIfPossible()
        updateNotification()
        onTickAnnounce()
        Log.d(TAG, "started workout='${w.name}' blocks=${w.blocks.size}")
        preRollJob = null
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                when (val state = engine.current()) {
                    is EngineState.Running -> {
                        engine.tick()
                        val newState = engine.current()
                        publishState(newState)
                        when (newState) {
                            is EngineState.Running -> {
                                updateNotification()
                                onTickAnnounce()
                            }
                            is EngineState.Finished -> {
                                stopEngine(userInitiated = false)
                                return@launch
                            }
                            else -> { /* Paused */ }
                        }
                    }
                    is EngineState.Finished -> {
                        publishState(state)
                        stopEngine(userInitiated = false)
                        return@launch
                    }
                    else -> { /* Paused/Idle */ }
                }
            }
        }
    }

    private fun onTickAnnounce() {
        if (!audioEnabled) return
        val state = engine.current()
        if (state is EngineState.Running) {
            if (state.idx != lastAnnouncedBlockIndex) {
                lastAnnouncedBlockIndex = state.idx
                speakBlockIntro(state)
            }
            if (state.remaining in 1..3) {
                beep()
                vibrate(60)
            }
        }
    }

    private fun speakBlockIntro(state: EngineState.Running) {
        val block = state.workout.blocks[state.idx]
        val phrase = Spoken.blockIntro(block, latestSettings.caps.unit)
        scope.launch {
            speakWhenReady(phrase, flush = true, id = "intro-${state.idx}")
        }
    }

    private suspend fun awaitTtsReady(timeoutMs: Long = 2000L): Boolean {
        if (ttsReady) return true
        val start = android.os.SystemClock.uptimeMillis()
        while (!ttsReady && android.os.SystemClock.uptimeMillis() - start < timeoutMs) {
            kotlinx.coroutines.delay(50)
        }
        return ttsReady
    }

    private suspend fun speakWhenReady(text: String, flush: Boolean = false, id: String = "speak") {
        if (!audioEnabled) return
        if (!awaitTtsReady()) return
        if (!requestFocus()) return
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = if (flush) "$id-f" else "$id-a"
        val result = tts?.speak(text, queueMode, null, utteranceId) ?: TextToSpeech.ERROR
        if (result != TextToSpeech.SUCCESS) {
            abandonFocus()
        }
    }

    private fun beep() {
        val generator = tone ?: return
        if (!requestFocus()) return
        if (generator.startTone(ToneGenerator.TONE_PROP_BEEP, 130)) {
            scope.launch {
                delay(200)
                abandonFocus()
            }
        } else {
            abandonFocus()
        }
    }

    private fun vibrate(ms: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        v.vibrate(effect)
    }

    private fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusReq = req
        return audioMgr.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusReq?.let { audioMgr.abandonAudioFocusRequest(it) }
        focusReq = null
    }

    private fun debugWorkout(): Workout {
        val blocks = listOf<Block>(
            SteadyBlock("Warmup",   120, 2.0),
            SteadyBlock("Steady",   300, 3.0),
            SteadyBlock("Cooldown", 120, 2.0)
        )
        return Workout(UUID.randomUUID().toString(), "Debug Quick", blocks)
    }

    private fun stopEngine(userInitiated: Boolean) {
        timerJob?.cancel()
        timerJob = null
        preRollJob?.cancel()
        val session = if (engine.isStarted()) engine.finishNow() else null
        stopHealthCollection(resetSummary = session == null)
        if (session != null) {
            persistOnce(session)
        }
        resetHealthTelemetryAfterSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifMgr.cancel(NOTIF_ID)
        publishState(EngineState.Idle(null))
        Log.d(TAG, "engine stopped userInitiated=$userInitiated sessionId=${session?.id}")
        stopSelf()
    }

    private fun persistOnce(session: Session) {
        if (hasPersisted && currentSessionId == session.id) return
        hasPersisted = true
        currentSessionId = session.id
        val enriched = attachHealthSummary(session)
        latestHealthSummary = HealthSummary()
        runBlocking { history.insertIgnore(enriched) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "WalkCraft Workouts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Foreground notification while a workout runs"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }
            notifMgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val contentPI = PendingIntent.getActivity(this, 0, launch, pendingFlags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("WalkCraft Workout")
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPI)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        fun action(label: String, action: String, icon: Int): NotificationCompat.Action {
            val pi = PendingIntent.getService(
                this,
                action.hashCode(),
                Intent(this, WorkoutService::class.java).setAction(action),
                pendingFlags
            )
            return NotificationCompat.Action.Builder(icon, label, pi).build()
        }

        val state = engine.current()
        val unitLabel = latestSettings.caps.unit.name
        val text: String
        var ongoing = false

        when (state) {
            is EngineState.Running -> {
                val label = state.workout.blocks[state.idx].label
                val remaining = formatDuration(state.remaining)
                val speed = "%.1f".format(Locale.US, state.speed)
                text = "Running • $label • $remaining @ $speed $unitLabel"
                ongoing = true
                val muteIntent = Intent(this, WorkoutService::class.java)
                    .setAction(if (audioEnabled) ACTION_MUTE else ACTION_UNMUTE)
                val mutePi = PendingIntent.getService(this, 105, muteIntent, pendingFlags)
                val muteActionTitle = if (audioEnabled) "Mute" else "Unmute"
                builder.addAction(0, muteActionTitle, mutePi)
                builder.addAction(action("Pause", ACTION_PAUSE, android.R.drawable.ic_media_pause))
                builder.addAction(action("Skip", ACTION_SKIP, android.R.drawable.ic_media_next))
                builder.addAction(action("Stop", ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel))
            }
            is EngineState.Paused -> {
                val label = state.workout.blocks[state.idx].label
                val remaining = formatDuration(state.remaining)
                val speed = "%.1f".format(Locale.US, state.speed)
                text = "Paused • $label • $remaining @ $speed $unitLabel"
                ongoing = true
                val muteIntent = Intent(this, WorkoutService::class.java)
                    .setAction(if (audioEnabled) ACTION_MUTE else ACTION_UNMUTE)
                val mutePi = PendingIntent.getService(this, 105, muteIntent, pendingFlags)
                val muteActionTitle = if (audioEnabled) "Mute" else "Unmute"
                builder.addAction(0, muteActionTitle, mutePi)
                builder.addAction(action("Resume", ACTION_RESUME, android.R.drawable.ic_media_play))
                builder.addAction(action("Skip", ACTION_SKIP, android.R.drawable.ic_media_next))
                builder.addAction(action("Stop", ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel))
            }
            is EngineState.Finished -> {
                text = "Workout complete"
                ongoing = false
                builder.addAction(action("Stop", ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel))
            }
            is EngineState.Idle -> {
                text = "Tap Start to begin workout"
                ongoing = false
                builder.addAction(action("Start", ACTION_START, android.R.drawable.ic_media_play))
                builder.addAction(action("Stop", ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel))
            }
        }

        builder.setContentText(text)
        builder.setOngoing(ongoing)
        return builder.build()
    }

    private fun updateNotification() {
        notifMgr.notify(NOTIF_ID, buildNotification())
    }

    private fun publishState(state: EngineState = engine.current()) {
        _states.value = state
    }

    private fun beginHealthCollectionIfPossible() {
        if (!healthConnectEnabled) {
            healthTelemetryState.value = HealthTelemetry.Inactive
            return
        }
        healthJob?.cancel()
        healthJob = scope.launch {
            val availability = healthManager.availability()
            if (availability != HealthConnectAvailability.Installed) {
                healthTelemetryState.value = HealthTelemetry.Inactive
                return@launch
            }
            val hasPermissions = healthManager.hasAllPermissions()
            if (!hasPermissions) {
                healthTelemetryState.value = HealthTelemetry.PermissionsNeeded
                return@launch
            }
            workoutStartInstant = Instant.now()
            latestHealthSummary = HealthSummary()
            healthTelemetryState.value = HealthTelemetry.Active(null, null)
            collectHealthSnapshot()
            while (isActive) {
                delay(15_000)
                collectHealthSnapshot()
            }
        }
    }

    private fun stopHealthCollection(resetSummary: Boolean) {
        healthJob?.cancel()
        healthJob = null
        workoutStartInstant = null
        if (resetSummary) {
            latestHealthSummary = HealthSummary()
        }
    }

    private suspend fun collectHealthSnapshot() {
        val start = workoutStartInstant ?: return
        val summary = healthManager.readSummary(start, Instant.now())
        latestHealthSummary = summary
        if (healthConnectEnabled) {
            healthTelemetryState.value = HealthTelemetry.Active(
                summary.averageHeartRateBpm,
                summary.totalSteps
            )
        }
    }

    private fun resetHealthTelemetryAfterSession() {
        scope.launch {
            if (!healthConnectEnabled) {
                healthTelemetryState.value = HealthTelemetry.Inactive
                return@launch
            }
            val availability = healthManager.availability()
            if (availability != HealthConnectAvailability.Installed) {
                healthTelemetryState.value = HealthTelemetry.Inactive
                return@launch
            }
            val hasPermissions = healthManager.hasAllPermissions()
            healthTelemetryState.value = if (hasPermissions) {
                HealthTelemetry.Inactive
            } else {
                HealthTelemetry.PermissionsNeeded
            }
        }
    }

    private suspend fun refreshHealthStatus() {
        if (!healthConnectEnabled) {
            healthTelemetryState.value = HealthTelemetry.Inactive
            return
        }
        val availability = healthManager.availability()
        if (availability != HealthConnectAvailability.Installed) {
            healthTelemetryState.value = HealthTelemetry.Inactive
            return
        }
        val hasPermissions = healthManager.hasAllPermissions()
        if (!hasPermissions) {
            healthTelemetryState.value = HealthTelemetry.PermissionsNeeded
        } else if (healthJob == null) {
            healthTelemetryState.value = HealthTelemetry.Inactive
        }
    }

    private fun attachHealthSummary(session: Session): Session {
        val summary = latestHealthSummary
        if (summary.averageHeartRateBpm == null && summary.totalSteps == null) {
            return session
        }
        return session.copy(
            avgHr = summary.averageHeartRateBpm ?: session.avgHr,
            totalSteps = summary.totalSteps ?: session.totalSteps
        )
    }

    private fun formatDuration(seconds: Int): String {
        val total = seconds.coerceAtLeast(0)
        val minutes = total / 60
        val secs = total % 60
        return if (minutes > 0) "%d:%02d".format(minutes, secs) else "%ds".format(secs)
    }
}
