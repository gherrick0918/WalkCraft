package com.walkcraft.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions // <-- FIX: Add this missing import
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.walkcraft.app.data.prefs.DevicePrefsRepository
import com.walkcraft.app.data.prefs.DeviceSettings
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.model.SpeedPolicy
import com.walkcraft.app.domain.model.SpeedUnit
import androidx.hilt.navigation.compose.hiltViewModel
import com.walkcraft.app.ui.viewmodel.DeviceSetupViewModel
import com.walkcraft.app.ui.viewmodel.HealthUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSetupScreen(
    onBack: () -> Unit,
    vm: DeviceSetupViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val repo = remember { DevicePrefsRepository.from(ctx) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val settings by repo.settingsFlow.collectAsState(initial = null)
    val health by vm.health.collectAsState()

    LaunchedEffect(Unit) {
        vm.refreshHealthState()
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
            HealthConnectSection(
                healthState = health,
                onGrant = { vm.buildHealthPermissionIntent() },
                onRefresh = vm::refreshHealthState
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
    healthState: HealthUiState,
    onGrant: suspend () -> Intent,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefresh()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            when {
                healthState.checking -> {
                    Text(
                        "Checking permissions…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                !healthState.available -> {
                    Text(
                        "Not installed on this device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                healthState.granted -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Granted")
                        TextButton(onClick = onRefresh) { Text("Re-check") }
                    }
                }
                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Permission required")
                        Button(onClick = {
                            scope.launch {
                                val intent = onGrant()
                                launcher.launch(intent)
                            }
                        }) { Text("Grant") }
                    }
                }
            }
        }
    }
}
