package com.walkcraft.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.walkcraft.app.R
import com.walkcraft.app.data.prefs.DevicePrefsRepository
import com.walkcraft.app.data.prefs.DeviceSettings
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var tickerJob: Job? = null
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
        engine = WorkoutEngine(latestSettings.caps, latestSettings.policy)
        startForeground(NOTIF_ID, buildNotification(initialText = "Ready"))

        scope.launch {
            DevicePrefsRepository.from(this@WorkoutService).settingsFlow.collect { settings ->
                latestSettings = settings
                engine = WorkoutEngine(settings.caps, settings.policy)
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_QUICK -> handleStartQuick(
                minutes = intent.getIntExtra(EXTRA_MINUTES, 20),
                easy = intent.getDoubleExtra(EXTRA_EASY, 2.0),
                hard = intent.getDoubleExtra(EXTRA_HARD, 3.0)
            )
            ACTION_START  -> handleStart()
            ACTION_PAUSE  -> { engine.pause(); updateNotification() }
            ACTION_RESUME -> { engine.resume(); updateNotification() }
            ACTION_SKIP   -> { engine.skip(); updateNotification() }
            ACTION_STOP   -> stopSelf()
            else          -> updateNotification()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- internals ----

    private fun handleStart() {
        val s = engine.current()
        if (s !is EngineState.Running && s !is EngineState.Paused) {
            Log.d(TAG, "Starting debug workout")
            engine.start(debugWorkout())
            startTicker()
        }
        updateNotification()
    }

    private fun handleStartQuick(minutes: Int, easy: Double, hard: Double) {
        Log.d(TAG, "Starting quick: minutes=$minutes easy=$easy hard=$hard")
        val workout = Plans.quickStart(
            easy = easy,
            hard = hard,
            minutes = minutes,
            caps = latestSettings.caps,
            policy = latestSettings.policy
        )
        engine.start(workout)
        startTicker()
        updateNotification()
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
        val contentPI = PendingIntent.getActivity(
            this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "WalkCraft Workout"
        val text = initialText ?: when (val s = engine.current()) {
            is EngineState.Running -> {
                val label = s.workout.blocks[s.idx].label
                "Running • $label • ${s.remaining}s @ ${"%.1f".format(s.speed)}"
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

        fun action(label: String, action: String, icon: Int): NotificationCompat.Action {
            val pi = PendingIntent.getService(
                this, action.hashCode(),
                Intent(this, WorkoutService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
}
