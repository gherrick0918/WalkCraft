package com.walkcraft.app.session

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class SessionFgService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "walkcraft_session"
        const val NOTIF_ID = 1001
        const val EXTRA_START_MS = "start_ms"
        const val EXTRA_BASELINE = "baseline_steps"

        fun start(context: Context, startMs: Long, baseline: Long) {
            val i = Intent(context, SessionFgService::class.java)
                .putExtra(EXTRA_START_MS, startMs)
                .putExtra(EXTRA_BASELINE, baseline)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionFgService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var nm: NotificationManager
    private lateinit var sm: SensorManager
    private var stepDetector: Sensor? = null
    private var stepCounter: Sensor? = null

    private var startMs: Long = 0L
    private var baseline: Long = 0L

    // Local, fast-moving session steps (independent of Health Connect flush cadence)
    private var localSessionSteps = 0L
    private var bootCounterAtStart: Float? = null

    private fun hasActivityRecognition(): Boolean {
        return if (Build.VERSION.SDK_INT >= 29) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private suspend fun readTodayStepsHc(): Long {
        val client = HealthConnectClient.getOrCreate(this)
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val end = start.plus(1, ChronoUnit.DAYS)
        val resp = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return resp.records.sumOf { it.count }
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepDetector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        stepCounter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val ch = NotificationChannel(
            CHANNEL_ID, "WalkCraft Session", NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMs = intent?.getLongExtra(EXTRA_START_MS, 0L) ?: 0L
        baseline = intent?.getLongExtra(EXTRA_BASELINE, 0L) ?: 0L

        val initial = buildNotif(elapsedMs = 0L, steps = 0L)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIF_ID, initial)
        }

        val arGranted = hasActivityRecognition()
        if (arGranted) {
            stepDetector?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            stepCounter?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }

        scope.launch {
            var lastHcPoll = 0L
            while (isActive) {
                val now = System.currentTimeMillis()

                if (now - lastHcPoll >= 15_000L) {
                    try {
                        val today = readTodayStepsHc()
                        val delta = (today - baseline).coerceAtLeast(0)
                        if (delta > localSessionSteps) localSessionSteps = delta
                    } catch (_: Throwable) {
                    }
                    lastHcPoll = now
                }

                val elapsed = now - startMs
                nm.notify(NOTIF_ID, buildNotif(elapsed, steps = localSessionSteps))
                delay(1_000L)
            }
        }
        return START_STICKY
    }

    private fun buildNotif(elapsedMs: Long, steps: Long): Notification {
        val totalSeconds = elapsedMs / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val title = "Walking…  %d:%02d:%02d".format(h, m, s)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("Session steps: $steps")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setShowWhen(true)
            .setWhen(startMs)
            .setUsesChronometer(true)
            .build()
    }

    override fun onDestroy() {
        try {
            sm.unregisterListener(this)
        } catch (_: Throwable) {
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Sensor callbacks ---
    override fun onSensorChanged(e: SensorEvent?) {
        val ev = e ?: return
        when (ev.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                localSessionSteps += 1
            }
            Sensor.TYPE_STEP_COUNTER -> {
                if (bootCounterAtStart == null) bootCounterAtStart = ev.values[0]
                val delta = (ev.values[0] - (bootCounterAtStart ?: 0f)).toLong()
                if (delta > localSessionSteps) localSessionSteps = delta
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
