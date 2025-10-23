package com.walkcraft.app.ui.screens

import android.app.Application
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.walkcraft.app.data.prefs.DevicePrefsRepository
import com.walkcraft.app.data.prefs.DeviceSettings
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SpeedUnit
import com.walkcraft.app.health.HealthConnectManager
import com.walkcraft.app.health.HealthConnectUiState
import com.walkcraft.app.health.HealthConnectViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSetupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hcVm: HealthConnectViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val app = context.applicationContext as Application
            HealthConnectViewModel(app)
        }
    })
    val repo = remember { DevicePrefsRepository.from(context) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val healthConnectManager = remember { HealthConnectManager(context) }

    val settings by repo.settingsFlow.collectAsState(initial = null)
    val hcUi by hcVm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        hcVm.refreshAvailability()
        hcVm.checkPermissions()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = healthConnectManager.permissionRequestContract()
    ) { granted: Set<HealthPermission> ->
        hcVm.onPermissionsResult(granted)
        hcVm.checkPermissions()
        val message = if (granted.containsAll(healthConnectManager.requiredPermissions)) {
            "Health Connect permissions granted"
        } else {
            "Some permissions were not granted"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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

    val status = HealthConnectClient.getSdkStatus(context)
    val isAvailable = status == HealthConnectClient.SDK_AVAILABLE

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
            Modifier
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Configure unit and your pad’s speeds. Saved to device (DataStore).")

            HealthConnectSection(
                state = hcUi,
                status = status,
                isAvailable = isAvailable,
                onUnavailable = { unavailableStatus ->
                    val message = when (unavailableStatus) {
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_NOT_INSTALLED,
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_OUTDATED,
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                            "Install or update Health Connect from the Play Store."
                        else -> "Health Connect is unavailable on this device."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                },
                onGrant = {
                    permissionLauncher.launch(healthConnectManager.requiredPermissions)
                },
                onCheckStatus = {
                    hcVm.refreshAvailability()
                    hcVm.checkPermissions()
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
private fun HealthConnectSection(
    state: HealthConnectUiState,
    status: Int,
    isAvailable: Boolean,
    onUnavailable: (Int) -> Unit,
    onGrant: () -> Unit,
    onCheckStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val statusText = when (state.sdkStatus) {
                HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect unavailable on this device."
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_NOT_INSTALLED,
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_OUTDATED,
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    "Health Connect requires install or update."
                else -> if (state.hasAllPermissions) "All permissions granted." else "Permissions needed."
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))

            Row {
                Button(
                    enabled = isAvailable,
                    onClick = {
                        if (status != HealthConnectClient.SDK_AVAILABLE) {
                            onUnavailable(status)
                            return@Button
                        }
                        onGrant()
                    }
                ) {
                    Text(if (state.hasAllPermissions) "Recheck / Manage" else "Grant")
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onCheckStatus) {
                    Text("Check status")
                }
            }
        }
    }
}
