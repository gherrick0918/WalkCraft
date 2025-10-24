package com.walkcraft.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.walkcraft.app.data.prefs.DevicePrefsRepository
import com.walkcraft.app.data.prefs.QuickStartConfig
import com.walkcraft.app.data.prefs.UserPrefsRepository
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.domain.plan.Plans
import com.walkcraft.app.health.HealthConnectPermissionCard
import com.walkcraft.app.service.WorkoutService
import kotlinx.coroutines.launch

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()), // Make the Column scrollable
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
private fun PreRollToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("3–2–1 pre-roll", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
