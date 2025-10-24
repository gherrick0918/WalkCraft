package com.walkcraft.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walkcraft.app.health.HealthConnectHelper
import com.walkcraft.app.health.StepsSessionViewModel
import kotlinx.coroutines.launch

@Composable
fun SessionScreen() {
    val vm: StepsSessionViewModel = viewModel()
    val session = vm.session.collectAsStateWithLifecycle().value
    val stepsToday = vm.todaySteps.collectAsStateWithLifecycle().value
    val saveResult = vm.saveResult.collectAsStateWithLifecycle().value

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext as Context

    // ---- Permission sets ----
    val READ_STEPS = remember {
        setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }
    val WRITE_EXERCISE = remember {
        setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
    }

    // Prompt to READ steps; if granted, start immediately.
    val readStepsLauncher = rememberLauncherForActivityResult(
        HealthConnectHelper.permissionContract()
    ) { granted: Set<String> ->
        if (READ_STEPS.all { it in granted }) {
            vm.start()
        }
    }

    // Prompt to WRITE exercise; if granted, stop+save immediately.
    val writeExerciseLauncher = rememberLauncherForActivityResult(
        HealthConnectHelper.permissionContract()
    ) { granted: Set<String> ->
        if (WRITE_EXERCISE.all { it in granted }) {
            vm.stop(save = true)
        }
    }

    // One-time first composition catch-up (optional)
    LaunchedEffect(Unit) { vm.onResume() }

    // Call onResume() every time this screen becomes visible again
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onResume()
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // ---- UI ----
    var saveThisSession by remember { mutableStateOf(true) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Today’s steps: ${stepsToday ?: 0}")
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = saveThisSession, onCheckedChange = { saveThisSession = it })
            Spacer(Modifier.width(8.dp))
            Text("Save to Health Connect")
        }

        Spacer(Modifier.height(12.dp))

        Row {
            Button(
                onClick = {
                    // Ensure provider installed/enabled first (no-op if already fine)
                    if (!HealthConnectHelper.ensureAvailableOrLaunchInstall(appContext)) return@Button

                    scope.launch {
                        val client = HealthConnectHelper.client(appContext)
                        val hasSteps = HealthConnectHelper.hasStepsPermission(client)
                        if (hasSteps) {
                            vm.start()
                        } else {
                            readStepsLauncher.launch(READ_STEPS)
                        }
                    }
                },
                enabled = !session.active
            ) { Text("Start") }

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = {
                    if (saveThisSession) {
                        scope.launch {
                            val client = HealthConnectHelper.client(appContext)
                            val canWrite = HealthConnectHelper.hasWriteExercisePermission(client)
                            if (!canWrite) {
                                writeExerciseLauncher.launch(WRITE_EXERCISE)
                            } else {
                                vm.stop(save = true)
                            }
                        }
                    } else {
                        vm.stop(save = false)
                    }
                },
                enabled = session.active
            ) { Text("Stop") }

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = { vm.reset() },
                enabled = !session.active && (session.baselineSteps != 0L || session.latestSteps != 0L)
            ) { Text("Reset") }
        }

        Spacer(Modifier.height(12.dp))
        Text("Session steps: ${session.sessionSteps}")

        val mins = (session.elapsedMs / 1000 / 60)
        val secs = (session.elapsedMs / 1000 % 60)
        if (session.active) {
            Text("Elapsed: %d:%02d".format(mins, secs))
        } else if (session.sessionSteps > 0L) {
            Text("Elapsed: %d:%02d (stopped)".format(mins, secs))
        }

        saveResult?.let { res ->
            Spacer(Modifier.height(12.dp))
            Text(res.message)
            if (!res.success && res.message.contains("Missing permission", ignoreCase = true)) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { writeExerciseLauncher.launch(WRITE_EXERCISE) }) {
                    Text("Grant ‘Save session’ permission")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(12.dp))

        Button(onClick = { vm.refreshToday() }) { Text("Refresh Today") }
    }
}
