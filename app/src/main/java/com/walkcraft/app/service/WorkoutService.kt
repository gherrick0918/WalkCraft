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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.walkcraft.app.domain.engine.EngineState
import com.walkcraft.app.domain.engine.WorkoutEngine
import com.walkcraft.app.domain.model.Block
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.domain.model.SteadyBlock
import com.walkcraft.app.domain.model.Workout
import com.walkcraft.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class WorkoutService : Service() {

    companion object {
        const val CHANNEL_ID = "walkcraft.workouts"
        const val NOTIF_ID = 1001

        const val ACTION_START = "com.walkcraft.app.action.START"
        const val ACTION_PAUSE = "com.walkcraft.app.action.PAUSE"
        const val ACTION_RESUME = "com.walkcraft.app.action.RESUME"
        const val ACTION_SKIP = "com.walkcraft.app.action.SKIP"
        const val ACTION_STOP = "com.walkcraft.app.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, WorkoutService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, WorkoutService::class.java).setAction(action)
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notifMgr: NotificationManager
    private lateinit var engine: WorkoutEngine

    override fun onCreate() {
        super.onCreate()
        notifMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()

        val caps = DeviceCapabilities(
            unit = SpeedUnit.MPH,
            mode = DeviceCapabilities.Mode.DISCRETE,
            allowed = listOf(2.0, 2.5, 3.0, 3.5)
        )
        engine = WorkoutEngine(caps, SpeedPolicy())

        startForeground(NOTIF_ID, buildNotification("Ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> {
                engine.pause()
                updateNotification()
            }
            ACTION_RESUME -> {
                engine.resume()
                updateNotification()
            }
            ACTION_SKIP -> {
                engine.skip()
                updateNotification()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStart() {
        val state = engine.current()
        if (state !is EngineState.Running && state !is EngineState.Paused) {
            engine.start(defaultWorkout())
            startTicker()
        }
        updateNotification()
    }

    private fun startTicker() {
        scope.launch {
            while (isActive) {
                delay(1000)
                when (val state = engine.current()) {
                    is EngineState.Running -> {
                        engine.tick()
                        updateNotification()
                    }
                    is EngineState.Finished -> {
                        updateNotification("Finished")
                        stopSelf()
                        return@launch
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun defaultWorkout(): Workout {
        val blocks = listOf<Block>(
            SteadyBlock("Warmup", 120, 2.0),
            SteadyBlock("Steady", 300, 3.0),
            SteadyBlock("Cooldown", 120, 2.0)
        )
        return Workout(UUID.randomUUID().toString(), "Debug Quick", blocks)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WalkCraft Workouts",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows a persistent notification while a workout runs"
            }
            notifMgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(textOverride: String? = null): Notification {
        val title = "WalkCraft Workout"
        val contentText = textOverride ?: when (val state = engine.current()) {
            is EngineState.Running -> {
                val block = state.workout.blocks[state.idx]
                "Running • ${block.label} • ${state.remaining}s left @ ${"%.1f".format(state.speed)}"
            }
            is EngineState.Paused -> "Paused"
            is EngineState.Finished -> "Finished"
            is EngineState.Idle -> "Ready"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        fun action(label: String, action: String, icon: Int): NotificationCompat.Action {
            val pendingIntent = PendingIntent.getService(
                this,
                action.hashCode(),
                Intent(this, WorkoutService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
        }

        when (engine.current()) {
            is EngineState.Running -> {
                builder.addAction(action("Pause", ACTION_PAUSE, android.R.drawable.ic_media_pause))
                builder.addAction(action("Skip", ACTION_SKIP, android.R.drawable.ic_media_next))
                builder.addAction(
                    action("Stop", ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel)
                )
            }
            is EngineState.Paused -> {
                builder.addAction(action("Resume", ACTION_RESUME, android.R.drawable.ic_media_play))
                builder.addAction(
                    action("Stop", ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel)
                )
            }
            is EngineState.Idle, is EngineState.Finished -> {
                builder.addAction(action("Start", ACTION_START, android.R.drawable.ic_media_play))
                builder.addAction(
                    action("Stop", ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel)
                )
            }
        }

        return builder.build()
    }

    private fun updateNotification(text: String? = null) {
        notifMgr.notify(NOTIF_ID, buildNotification(text))
    }
}
