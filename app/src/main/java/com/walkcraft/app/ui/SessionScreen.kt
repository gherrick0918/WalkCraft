package com.walkcraft.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.core.content.ContextCompat
import com.walkcraft.app.health.HealthConnectHelper
import com.walkcraft.app.health.StepsSessionViewModel
import kotlinx.coroutines.launch

@Composable
fun SessionScreen() {
    val vm: StepsSessionViewModel = viewModel()
    val session by vm.session.collectAsStateWithLifecycle()
    val stepsToday by vm.todaySteps.collectAsStateWithLifecycle()
    val saveResult = vm.saveResult.collectAsStateWithLifecycle().value

    val context = LocalContext.current

    val WRITE_EXERCISE = remember {
        setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
    }
    val REQUIRED_PERMISSIONS = remember { HealthConnectHelper.REQUIRED_PERMISSIONS }
    val writePermLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        // If granted, try saving again immediately
        if (WRITE_EXERCISE.all { it in granted }) {
            vm.stop(save = true)
        }
    }
    var saveThisSession by remember { mutableStateOf(true) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; we just attempt to show the notif either way */ }

    val arPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* if denied, service still runs but won't get sensor steps */ }

    val readPermLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        if (REQUIRED_PERMISSIONS.all { it in granted }) {
            permissionMessage = null
            vm.start()
        } else {
            permissionMessage = "Grant ‘Activity’ permission to start a session."
        }
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.onResume() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Today’s steps: ${stepsToday ?: 0}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = saveThisSession, onCheckedChange = { saveThisSession = it })
            Spacer(Modifier.width(8.dp))
            Text("Save to Health Connect")
        }
        Spacer(Modifier.height(8.dp))

        Row {
            Button(
                onClick = {
                    scope.launch {
                        permissionMessage = null
                        if (!HealthConnectHelper.ensureAvailableOrLaunchInstall(context)) {
                            permissionMessage = "Install Health Connect to start a session."
                            return@launch
                        }

                        // Request POST_NOTIFICATIONS on Android 13+ (so foreground service notif can show)
                        if (Build.VERSION.SDK_INT >= 33) {
                            val granted =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        // Request ACTIVITY_RECOGNITION on Android 10+ (for step detector/counter)
                        if (Build.VERSION.SDK_INT >= 29) {
                            val grantedAR =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACTIVITY_RECOGNITION
                                ) == PackageManager.PERMISSION_GRANTED
                            if (!grantedAR) arPermLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }

                        if (vm.needsStepsPermission()) {
                            readPermLauncher.launch(REQUIRED_PERMISSIONS)
                        } else {
                            vm.start()
                        }
                    }
                },
                enabled = !session.active
            ) { Text("Start") }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    // If we want to save, make sure we prompt first
                    if (saveThisSession) {
                        // use a coroutine scope already in this composable
                        scope.launch {
                            if (vm.needsWriteExercisePermission()) {
                                writePermLauncher.launch(WRITE_EXERCISE)
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

        permissionMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(msg, color = MaterialTheme.colorScheme.error)
        }

        saveResult?.let { res ->
            Spacer(Modifier.height(8.dp))
            Text(res.message)

            if (!res.success && res.message.contains("Missing permission")) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { writePermLauncher.launch(WRITE_EXERCISE) }) {
                    Text("Grant ‘Save session’ permission")
                }
            }
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

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.refreshToday() }) { Text("Refresh Today") }
    }
}
