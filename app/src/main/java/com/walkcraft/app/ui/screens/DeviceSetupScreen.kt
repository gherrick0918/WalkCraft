package com.walkcraft.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions // <-- FIX: Add this missing import
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.walkcraft.app.data.prefs.DevicePrefsRepository
import com.walkcraft.app.data.prefs.DeviceSettings
import com.walkcraft.app.data.prefs.UserPrefsRepository
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.service.health.HealthConnectAvailability
import com.walkcraft.app.service.health.HealthConnectManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { DevicePrefsRepository.from(ctx) }
    val userPrefs = remember { UserPrefsRepository.from(ctx) }
    val healthManager = remember { HealthConnectManager(ctx) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val settings by repo.settingsFlow.collectAsState(initial = null)
    val healthEnabled by userPrefs.healthConnectEnabledFlow.collectAsState(initial = false)
    var healthAvailability by remember { mutableStateOf<HealthConnectAvailability?>(null) }
    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        scope.launch {
            val granted = healthManager.hasPermissions()
            permissionsGranted = granted
            if (granted) {
                userPrefs.setHealthConnectEnabled(true)
                snack.showSnackbar("Health Connect connected")
                refreshHealthStatus()
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                snack.showSnackbar("Permissions were not granted")
                refreshHealthStatus()
            }
        }
    }

    var unit by remember { mutableStateOf(SpeedUnit.MPH) }
    var mode by remember { mutableStateOf(DeviceCapabilities.Mode.DISCRETE) }
    var allowedCsv by remember { mutableStateOf("2.0, 2.5, 3.0, 3.5") }
    var min by remember { mutableStateOf("1.0") }
    var max by remember { mutableStateOf("3.5") }
    var inc by remember { mutableStateOf("0.5") }
    var strategy by remember { mutableStateOf(SpeedPolicy.Strategy.NEAREST) }

    LaunchedEffect(settings) {
        settings?.let { s ->
            unit = s.caps.unit
            mode = s.caps.mode
            when (s.caps.mode) {
                DeviceCapabilities.Mode.DISCRETE ->
                    allowedCsv = (s.caps.allowed ?: emptyList()).joinToString(", ")
                DeviceCapabilities.Mode.INCREMENT -> {
                    min = s.caps.min?.toString() ?: ""
                    max = s.caps.max?.toString() ?: ""
                    inc = s.caps.increment?.toString() ?: ""
                }
            }
            strategy = s.policy.strategy
        }
    }

    suspend fun refreshHealthStatus() {
        val availability = healthManager.availability()
        healthAvailability = availability
        permissionsGranted = if (healthEnabled && availability == HealthConnectAvailability.Installed) {
            healthManager.hasPermissions()
        } else {
            false
        }
    }

    LaunchedEffect(healthEnabled) {
        refreshHealthStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Setup") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snack) }
    ) { inner ->
        Column(
            Modifier.padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Configure unit and your pad’s speeds. Saved to device (DataStore).")

            Spacer(Modifier.height(8.dp))
            HealthConnectRow(
                availability = healthAvailability,
                enabled = healthEnabled,
                permissionsGranted = permissionsGranted,
                onConnect = {
                    scope.launch {
                        val availability = healthAvailability ?: healthManager.availability()
                        healthAvailability = availability
                        when (availability) {
                            HealthConnectAvailability.NotSupported -> {
                                snack.showSnackbar("Health Connect is not supported on this device")
                            }
                            HealthConnectAvailability.NeedsInstall -> {
                                runCatching { ctx.startActivity(healthManager.installAppIntent()) }
                            }
                            HealthConnectAvailability.Installed -> {
                                val granted = healthManager.hasPermissions()
                                if (granted) {
                                    userPrefs.setHealthConnectEnabled(true)
                                    permissionsGranted = true
                                    snack.showSnackbar("Health Connect enabled")
                                } else {
                                    val intent = healthManager.permissionRequestIntent()
                                    if (intent != null) {
                                        permissionLauncher.launch(
                                            IntentSenderRequest.Builder(intent).build()
                                        )
                                    } else {
                                        snack.showSnackbar("Unable to request permissions")
                                    }
                                }
                            }
                            null -> Unit
                        }
                    }
                },
                onDisable = {
                    scope.launch {
                        userPrefs.setHealthConnectEnabled(false)
                        permissionsGranted = false
                        snack.showSnackbar("Health Connect disabled")
                    }
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = unit == SpeedUnit.MPH, onClick = { unit = SpeedUnit.MPH }, label = { Text("MPH") })
                FilterChip(selected = unit == SpeedUnit.KPH, onClick = { unit = SpeedUnit.KPH }, label = { Text("KPH") })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == DeviceCapabilities.Mode.DISCRETE,
                    onClick = { mode = DeviceCapabilities.Mode.DISCRETE },
                    label = { Text("Discrete list") }
                )
                FilterChip(
                    selected = mode == DeviceCapabilities.Mode.INCREMENT,
                    onClick = { mode = DeviceCapabilities.Mode.INCREMENT },
                    label = { Text("Increment") }
                )
            }

            if (mode == DeviceCapabilities.Mode.DISCRETE) {
                OutlinedTextField(
                    value = allowedCsv,
                    onValueChange = { allowedCsv = it },
                    label = { Text("Allowed speeds (e.g., 2.0, 2.5, 3.0)") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = min,
                        onValueChange = { min = it },
                        label = { Text("Min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = max,
                        onValueChange = { max = it },
                        label = { Text("Max") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = inc,
                        onValueChange = { inc = it },
                        label = { Text("Increment") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = strategy == SpeedPolicy.Strategy.NEAREST,
                    onClick = { strategy = SpeedPolicy.Strategy.NEAREST },
                    label = { Text("Nearest") }
                )
                FilterChip(
                    selected = strategy == SpeedPolicy.Strategy.DOWN,
                    onClick = { strategy = SpeedPolicy.Strategy.DOWN },
                    label = { Text("Down") }
                )
                FilterChip(
                    selected = strategy == SpeedPolicy.Strategy.UP,
                    onClick = { strategy = SpeedPolicy.Strategy.UP },
                    label = { Text("Up") }
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        val caps = if (mode == DeviceCapabilities.Mode.DISCRETE) {
                            val list = allowedCsv.split(",").mapNotNull { it.trim().toDoubleOrNull() }.sorted()
                            DeviceCapabilities(unit = unit, mode = mode, allowed = list)
                        } else {
                            val mn = min.toDoubleOrNull()
                            val mx = max.toDoubleOrNull()
                            val ic = inc.toDoubleOrNull()
                            if (mn == null || mx == null || ic == null) {
                                snack.showSnackbar("Please enter valid min/max/increment")
                                return@launch
                            }
                            DeviceCapabilities(unit = unit, mode = mode, min = mn, max = mx, increment = ic)
                        }
                        repo.save(DeviceSettings(caps, SpeedPolicy(strategy = strategy)))
                        snack.showSnackbar("Saved")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun HealthConnectRow(
    availability: HealthConnectAvailability?,
    enabled: Boolean,
    permissionsGranted: Boolean,
    onConnect: () -> Unit,
    onDisable: () -> Unit,
) {
    val status = when (availability) {
        null -> "Checking…"
        HealthConnectAvailability.NotSupported -> "Not supported on this device"
        HealthConnectAvailability.NeedsInstall -> "App not installed"
        HealthConnectAvailability.Installed -> when {
            enabled && permissionsGranted -> "Connected"
            enabled -> "Permissions required"
            else -> "Available"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        when (availability) {
            HealthConnectAvailability.Installed -> {
                if (enabled && permissionsGranted) {
                    TextButton(onClick = onDisable) { Text("Disable") }
                } else {
                    Button(onClick = onConnect) { Text(if (enabled) "Retry" else "Connect") }
                }
            }
            HealthConnectAvailability.NeedsInstall -> {
                Button(onClick = onConnect) { Text("Install") }
            }
            HealthConnectAvailability.NotSupported, null -> {
                // No action available.
            }
        }
    }
}
