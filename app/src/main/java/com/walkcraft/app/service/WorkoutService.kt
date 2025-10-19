package com.walkcraft.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.walkcraft.app.R
import com.walkcraft.app.domain.engine.EngineState
import com.walkcraft.app.domain.engine.WorkoutEngine
import com.walkcraft.app.domain.model.*
import kotlinx.coroutines.*
import java.util.UUID

class WorkoutService : Service() {

    companion object {
        const val CHANNEL_ID = "walkcraft.workouts"
        const val NOTIF_ID = 1001

        const val ACTION_START  = "com.walkcraft.app.action.START"
        const val ACTION_PAUSE  = "com.walkcraft.app.action.PAUSE"
        const val ACTION_RESUME = "com.walkcraft.app.action.RESUME"
        const val ACTION_SKIP   = "com.walkcraft.app.action.SKIP"
        const val ACTION_STOP   = "com.walkcraft.app.action.STOP"

        fun start(context: Context) {
            val i = Intent(context, WorkoutService::class.java).setAction(ACTION_START)
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

    override fun onCreate() {
        super.onCreate()
        notifMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()

        // Safe default capabilities for MVP; will be user-configured later.
        val caps = DeviceCapabilities(
            unit = SpeedUnit.MPH,
            mode = DeviceCapabilities.Mode.DISCRETE,
            allowed = listOf(2.0, 2.5, 3.0, 3.5)
        )
        engine = WorkoutEngine(caps, SpeedPolicy())

        // Post an immediate foreground notification (required on O+).
        startForeground(NOTIF_ID, buildNotification(initialText = "Ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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

    // -------- internals --------

    private fun handleStart() {
        val s = engine.current()
        if (s !is EngineState.Running && s !is EngineState.Paused) {
            engine.start(debugWorkout())
            startTicker()
        }
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
                    else -> {} // Paused/Idle
                }
            }
        }
    }

    private fun debugWorkout(): Workout {
        val blocks = listOf<Block>(
            SteadyBlock("Warmup", 120, 2.0),
            SteadyBlock("Steady", 300, 3.0),
            SteadyBlock("Cooldown", 120, 2.0)
        )
        return Workout(UUID.randomUUID().toString(), "Debug Quick", blocks)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "WalkCraft Workouts",
                NotificationManager.IMPORTANCE_LOW
            )
            ch.description = "Persistent notification while a workout runs"
            notifMgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(initialText: String? = null): Notification {
        // Use the app’s LAUNCH intent to avoid activity package mismatches.
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            androidx.core.app.PendingIntentCompat.getActivity(
                this, 0, launch, 0, false
            )
        }

        val title = "WalkCraft Workout"
        val text = initialText ?: runCatching {
            when (val s = engine.current()) {
                is EngineState.Running -> {
                    val label = s.workout.blocks[s.idx].label
                    "Running • $label • ${s.remaining}s @ ${"%.1f".format(s.speed)}"
                }
                is EngineState.Paused   -> "Paused"
                is EngineState.Finished -> "Finished"
                is EngineState.Idle     -> "Ready"
            }
        }.getOrDefault("Ready")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (contentIntent != null) builder.setContentIntent(contentIntent)

        fun action(label: String, action: String, icon: Int): NotificationCompat.Action {
            val pi = androidx.core.app.PendingIntentCompat.getService(
                this, action.hashCode(),
                Intent(this, WorkoutService::class.java).setAction(action),
                0, // flags handled by compat
                false
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
