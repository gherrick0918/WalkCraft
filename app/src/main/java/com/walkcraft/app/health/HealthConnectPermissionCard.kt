package com.walkcraft.app.health

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun HealthConnectPermissionCard(appContext: Context) {
    val scope = rememberCoroutineScope()
    val hcClient = remember { HealthConnectHelper.client(appContext) }

    val vm: StepsSessionViewModel = viewModel()
    val session by vm.session.collectAsStateWithLifecycle()
    val stepsToday by vm.todaySteps.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (HealthConnectHelper.hasAllPermissions(hcClient)) {
            vm.refreshToday()
            vm.onResume()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshToday()
                vm.onResume()
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var msg by remember { mutableStateOf<String?>(null) }

    val required = remember {
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        val allGranted = required.all { it in granted }
        msg = if (allGranted) "Permissions granted ✅" else "Not all permissions granted ⚠️"
        if (allGranted) {
            vm.refreshToday()
            vm.onResume()
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
                    permissionLauncher.launch(required)
                }) { Text("Grant Permissions") }

                Spacer(Modifier.width(12.dp))

                Button(onClick = {
                    scope.launch {
                        if (HealthConnectHelper.hasAllPermissions(hcClient)) {
                            vm.refreshToday()
                        } else msg = "Grant permissions first"
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
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("Last 7 days:")
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
                            if (HealthConnectHelper.hasAllPermissions(hcClient)) {
                                vm.start()
                            } else msg = "Grant permissions first"
                        }
                    },
                    enabled = !session.active
                ) { Text("Start") }

                Spacer(Modifier.width(12.dp))

                Button(onClick = { vm.stop() }, enabled = session.active) { Text("Stop") }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = { vm.reset() },
                    enabled = !session.active && (session.baselineSteps != 0L || session.latestSteps != 0L)
                ) { Text("Reset") }
            }

            Spacer(Modifier.height(8.dp))
            stepsToday?.let { Text("Today’s steps: $it") }
            Spacer(Modifier.height(16.dp))
            Text("Session steps: ${session.sessionSteps}")
            msg?.let { Text(it) }
        }
    }
}
