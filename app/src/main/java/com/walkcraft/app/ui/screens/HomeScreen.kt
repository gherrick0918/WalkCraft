package com.walkcraft.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider // FIX: Import HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import com.walkcraft.app.data.prefs.DevicePrefsRepository
import com.walkcraft.app.data.prefs.QuickStartConfig
import com.walkcraft.app.data.prefs.UserPrefsRepository
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.plan.Plans
import com.walkcraft.app.health.HealthConnectHelper
import com.walkcraft.app.health.StepSessionState
import com.walkcraft.app.service.WorkoutService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDeviceSetup: () -> Unit,
    onRun: () -> Unit,
    onHistory: () -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { DevicePrefsRepository.from(ctx) }
    val userPrefs = remember { UserPrefsRepository.from(ctx) }
    val current by repo.settingsFlow.collectAsState(initial = null)
    val quickConfig by userPrefs.quickStartConfigFlow.collectAsState(initial = QuickStartConfig())
    val scope = rememberCoroutineScope()

    var minutes by remember { mutableStateOf(quickConfig.minutes) }
    var easy by remember { mutableStateOf(quickConfig.easy) }
    var hard by remember { mutableStateOf(quickConfig.hard) }
    var preRoll by remember { mutableStateOf(quickConfig.preRoll) }

    LaunchedEffect(quickConfig) {
        minutes = quickConfig.minutes
        easy = quickConfig.easy
        hard = quickConfig.hard
        preRoll = quickConfig.preRoll
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("WalkCraft") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Indoor walking, built for discrete speeds.",
                style = MaterialTheme.typography.bodyLarge
            )
            current?.let {
                val caps = it.caps
                val policy = it.policy.strategy
                val summary = if (caps.mode == DeviceCapabilities.Mode.DISCRETE) {
                    "Unit: ${caps.unit}, Mode: DISCRETE, allowed: ${caps.allowed?.joinToString()}, policy: $policy"
                } else {
                    "Unit: ${caps.unit}, Mode: INCREMENT, min: ${caps.min}, max: ${caps.max}, increment: ${caps.increment}, policy: $policy"
                }
                Text(summary)
            }
            Button(onClick = onDeviceSetup, modifier = Modifier.fillMaxWidth()) {
                Text("Device Setup")
            }
            Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                Text("Run")
            }
            Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                Text("History")
            }

            HealthConnectPermissionCard(ctx)

            HorizontalDivider() // FIX: Renamed Divider to HorizontalDivider

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Quick Start", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = easy.toString(),
                            onValueChange = { text ->
                                text.toDoubleOrNull()?.let { v ->
                                    easy = v
                                    scope.launch { userPrefs.updateQuickStartConfig { it.copy(easy = v) } }
                                }
                            },
                            label = { Text("Easy speed") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = hard.toString(),
                            onValueChange = { text ->
                                text.toDoubleOrNull()?.let { v ->
                                    hard = v
                                    scope.launch { userPrefs.updateQuickStartConfig { it.copy(hard = v) } }
                                }
                            },
                            label = { Text("Hard speed") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minutes.toString(),
                            onValueChange = { text ->
                                text.toIntOrNull()?.let { m ->
                                    val clamped = m.coerceIn(1, 120)
                                    minutes = clamped
                                    scope.launch { userPrefs.updateQuickStartConfig { it.copy(minutes = clamped) } }
                                }
                            },
                            label = { Text("Minutes") },
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    val preview = remember(easy, hard, minutes, current) {
                        current?.let { settings ->
                            Plans.previewForQuickStart(
                                easy = easy,
                                hard = hard,
                                minutes = minutes,
                                caps = settings.caps,
                                policy = settings.policy
                            )
                        }
                    }
                    if (preview != null) {
                        Text(preview, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "Set up your device to preview the plan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PreRollToggleRow(preRoll) { enabled ->
                        preRoll = enabled
                        scope.launch { userPrefs.updateQuickStartConfig { it.copy(preRoll = enabled) } }
                    }
                    Button(
                        onClick = {
                            WorkoutService.startQuick(ctx, minutes, easy, hard)
                            Toast.makeText(
                                ctx,
                                "Starting Quick Start…",
                                Toast.LENGTH_SHORT
                            ).show()
                            onRun()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Quick Start")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    WorkoutService.start(ctx)
                    Toast.makeText(
                        ctx,
                        "Starting workout…",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Debug Workout")
            }
        }
    }
}

@Composable
fun HealthConnectPermissionCard(appContext: android.content.Context) {
    val scope = rememberCoroutineScope()
    val hcClient = remember { HealthConnectHelper.client(appContext) }

    var stepsToday by remember { mutableStateOf<Long?>(null) }

    // ---- Session state ----
    var session by remember { mutableStateOf(StepSessionState()) }
    var pollJob by remember { mutableStateOf<Job?>(null) }
    val POLL_INTERVAL_MS = 5_000L
    val AUTO_STOP_MINUTES = 120L

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            while (true) {
                val elapsedMin = session.elapsedMs / 1000 / 60
                if (!session.active || elapsedMin >= AUTO_STOP_MINUTES) {
                    session = session.copy(active = false)
                    stopPolling()
                    break
                }

                val has = HealthConnectHelper.hasAllPermissions(hcClient)
                if (has) {
                    val current = HealthConnectHelper.readTodaySteps(hcClient)
                    session = session.copy(latestSteps = current)
                    stepsToday = current
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    LaunchedEffect(Unit) {
        // If the user already granted permission earlier, fetch steps immediately.
        val has = HealthConnectHelper.hasAllPermissions(hcClient)
        if (has) {
            stepsToday = HealthConnectHelper.readTodaySteps(hcClient)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    if (HealthConnectHelper.hasAllPermissions(hcClient)) {
                        stepsToday = HealthConnectHelper.readTodaySteps(hcClient)
                    }
                }
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { stopPolling() }
    }

    var msg by remember { mutableStateOf<String?>(null) }

    val REQUIRED = remember {
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        val allGranted = REQUIRED.all { it in granted }
        msg = if (allGranted) "Permissions granted ✅" else "Not all permissions granted ⚠️"
        if (allGranted) {
            scope.launch {
                stepsToday = HealthConnectHelper.readTodaySteps(hcClient)
            }
        }
    }

    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Grant permission to read Steps and Heart Rate, then fetch today’s steps.")
            Spacer(Modifier.height(12.dp))

            Row {
                Button(onClick = {
                    if (!HealthConnectHelper.ensureAvailableOrLaunchInstall(appContext)) return@Button
                    permissionLauncher.launch(REQUIRED)
                }) { Text("Grant Permissions") }

                Spacer(Modifier.width(12.dp))

                Button(onClick = {
                    scope.launch {
                        // Skip if provider missing; you already have a toast in your optional patch
                        val has = HealthConnectHelper.hasAllPermissions(hcClient)
                        if (has) stepsToday = HealthConnectHelper.readTodaySteps(hcClient)
                        else msg = "Grant permissions first"
                    }
                }) { Text("Refresh Today") }
            }

            Row {
                var last7 by remember { mutableStateOf<List<Pair<java.time.LocalDate, Long>>>(emptyList()) }
                Button(onClick = {
                    scope.launch {
                        if (HealthConnectHelper.hasAllPermissions(hcClient)) {
                            last7 = HealthConnectHelper.readStepsLastNDays(hcClient, days = 7)
                        } else msg = "Grant permissions first"
                    }
                }) { Text("Last 7 days") }

                if (last7.isNotEmpty()) {
                    // Use a Column to arrange Text vertically
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("Last 7 days:")
                        // Now the \n in a single Text composable will work correctly
                        Text(
                            last7.joinToString(separator = "\n") { (date, steps) ->
                                "$date: $steps"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Session", style = MaterialTheme.typography.titleMedium)

            Row {
                Button(
                    onClick = {
                        scope.launch {
                            val has = HealthConnectHelper.hasAllPermissions(hcClient)
                            if (!has) {
                                msg = "Grant permissions first"
                                return@launch
                            }
                            val current = HealthConnectHelper.readTodaySteps(hcClient)
                            session = StepSessionState(
                                active = true,
                                startEpochMs = System.currentTimeMillis(),
                                baselineSteps = current,
                                latestSteps = current
                            )
                            startPolling()
                        }
                    },
                    enabled = !session.active
                ) { Text("Start") }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        session = session.copy(active = false)
                        stopPolling()
                    },
                    enabled = session.active
                ) { Text("Stop") }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        session = StepSessionState()
                        stopPolling()
                    },
                    enabled = !session.active && (session.baselineSteps != 0L || session.latestSteps != 0L)
                ) { Text("Reset") }
            }

            Spacer(Modifier.height(8.dp))
            Text("Session steps: ${'$'}{session.sessionSteps}")

            val mins = (session.elapsedMs / 1000 / 60)
            val secs = (session.elapsedMs / 1000 % 60)
            if (session.active) {
                Text("Elapsed: %d:%02d".format(mins, secs))
            } else if (session.sessionSteps > 0L) {
                Text("Elapsed: %d:%02d (stopped)".format(mins, secs))
            }

            Spacer(Modifier.height(8.dp))
            msg?.let { Text(it) }
            stepsToday?.let { Text("Today’s steps: $it") }
        }
    }
}

@Composable
private fun PreRollToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("3–2–1 pre-roll", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
