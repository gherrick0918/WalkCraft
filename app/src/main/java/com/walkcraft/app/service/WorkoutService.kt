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
import com.walkcraft.app.data.prefs.DevicePrefsRepository
import com.walkcraft.app.data.prefs.DeviceSettings
import com.walkcraft.app.data.prefs.UserPrefsRepository
import com.walkcraft.app.domain.format.Spoken
import com.walkcraft.app.domain.engine.EngineState
import com.walkcraft.app.domain.engine.WorkoutEngine
import com.walkcraft.app.domain.model.Block
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SteadyBlock
import com.walkcraft.app.domain.model.Workout
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.domain.plan.Plans
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notifMgr: NotificationManager
    private lateinit var engine: WorkoutEngine
    private lateinit var userPrefs: UserPrefsRepository
    private var audioEnabled = true
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private lateinit var audioMgr: AudioManager
    private var focusReq: AudioFocusRequest? = null
    private var lastAnnouncedBlockIndex: Int = -1
    private var tickerJob: Job? = null
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

    override fun onCreate() {
        super.onCreate()
        notifMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()

        userPrefs = UserPrefsRepository.from(this)

        runBlocking {
            val deviceRepo = DevicePrefsRepository.from(this@WorkoutService)
            latestSettings = withContext(Dispatchers.IO) { deviceRepo.settingsFlow.first() }
            val audioMuted = withContext(Dispatchers.IO) { userPrefs.audioMutedFlow.first() }
            val preroll = withContext(Dispatchers.IO) { userPrefs.prerollEnabledFlow.first() }
            audioEnabled = !audioMuted
            preRollEnabled = preroll
        }

        engine = WorkoutEngine(latestSettings.caps, latestSettings.policy)
        startForeground(NOTIF_ID, buildNotification(initialText = "Ready"))

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val act = intent?.action
        Log.d(TAG, "onStartCommand action=$act extras=${intent?.extras?.keySet()?.joinToString()}")
        when {
            act == ACTION_START_QUICK || looksLikeQuickStart(intent) -> {
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
            act == ACTION_PAUSE  -> { engine.pause();  updateNotification(); Log.d(TAG, "pause") }
            act == ACTION_RESUME -> { engine.resume(); updateNotification(); onTickAnnounce(); Log.d(TAG, "resume") }
            act == ACTION_SKIP   -> { engine.skip();   updateNotification(); onTickAnnounce(); Log.d(TAG, "skip") }
            act == ACTION_STOP   -> { Log.d(TAG, "stopSelf"); stopSelf() }
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
                // Keep the notif fresh; no state change.
                updateNotification()
            }
        }
        // If system kills us after this, redeliver the intent so Quick Start still fires
        return START_REDELIVER_INTENT
    }

    private fun looksLikeQuickStart(i: Intent?): Boolean {
        if (i == null) return false
        return i.hasExtra(EXTRA_MINUTES) || i.hasExtra(EXTRA_EASY) || i.hasExtra(EXTRA_HARD)
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        preRollJob?.cancel()
        scope.cancel()
        ttsReady = false
        tts?.shutdown()
        tts = null
        tone?.stopTone()
        tone?.release()
        tone = null
        abandonFocus()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- internals ----

    private fun handleStart() {
        val s = engine.current()
        if (s !is EngineState.Running && s !is EngineState.Paused) {
            Log.d(TAG, "Starting debug workout")
            startWithOptionalPreroll(debugWorkout())
        } else {
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
        engine.start(w)
        startTicker()
        updateNotification()
        onTickAnnounce()
        Log.d(TAG, "started workout='${w.name}' blocks=${w.blocks.size}")
        preRollJob = null
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                when (engine.current()) {
                    is EngineState.Running -> {
                        engine.tick()
                        updateNotification()
                        onTickAnnounce()
                    }
                    is EngineState.Finished -> {
                        updateNotification("Finished")
                        stopSelf()
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

    private fun buildNotification(initialText: String? = null): Notification {
        // Use app launch intent (avoids hard-coding MainActivity path)
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val contentPI = PendingIntent.getActivity(
            this, 0, launch, pendingFlags
        )

        val title = "WalkCraft Workout"
        val text = initialText ?: when (val s = engine.current()) {
            is EngineState.Running -> {
                val label = s.workout.blocks[s.idx].label
                val remaining = formatMmSs(s.remaining)
                val speed = "%.1f".format(Locale.US, s.speed)
                "Walking • $label • $remaining @ $speed ${latestSettings.caps.unit}"
            }
            is EngineState.Paused   -> "Paused"
            is EngineState.Finished -> "Finished"
            is EngineState.Idle     -> "Ready"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentPI)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )

        val muteIntent = Intent(this, WorkoutService::class.java)
            .setAction(if (audioEnabled) ACTION_MUTE else ACTION_UNMUTE)
        val mutePi = PendingIntent.getService(this, 105, muteIntent, pendingFlags)
        val muteActionTitle = if (audioEnabled) "Mute" else "Unmute"
        builder.addAction(0, muteActionTitle, mutePi)

        fun action(label: String, action: String, icon: Int): NotificationCompat.Action {
            val pi = PendingIntent.getService(
                this, action.hashCode(),
                Intent(this, WorkoutService::class.java).setAction(action),
                pendingFlags
            )
            return NotificationCompat.Action.Builder(icon, label, pi).build()
        }

        when (engine.current()) {
            is EngineState.Running -> {
                builder.addAction(action("Pause", ACTION_PAUSE, android.R.drawable.ic_media_pause))
                builder.addAction(action("Skip",  ACTION_SKIP,  android.R.drawable.ic_media_next))
                builder.addAction(action("Stop",  ACTION_STOP,  android.R.drawable.ic_menu_close_clear_cancel))
            }
            is EngineState.Paused -> {
                builder.addAction(action("Resume", ACTION_RESUME, android.R.drawable.ic_media_play))
                builder.addAction(action("Stop",   ACTION_STOP,   android.R.drawable.ic_menu_close_clear_cancel))
            }
            else -> {
                builder.addAction(action("Start", ACTION_START, android.R.drawable.ic_media_play))
                builder.addAction(action("Stop",  ACTION_STOP,  android.R.drawable.ic_menu_close_clear_cancel))
            }
        }
        return builder.build()
    }

    private fun updateNotification(text: String? = null) {
        notifMgr.notify(NOTIF_ID, buildNotification(text))
    }

    private fun formatMmSs(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}
