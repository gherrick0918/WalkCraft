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
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.walkcraft.app.data.prefs.DevicePrefsRepository
import com.walkcraft.app.domain.model.DeviceCapabilities
import com.walkcraft.app.service.WorkoutService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDeviceSetup: () -> Unit,
    onRun: () -> Unit,
    onHistory: () -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { DevicePrefsRepository.from(ctx) }
    val current by repo.settingsFlow.collectAsState(initial = null)

    var minutes by remember { mutableStateOf(20) }
    val (easyDefault, hardDefault) = remember(current) {
        val caps = current?.caps
        when (caps?.mode) {
            DeviceCapabilities.Mode.DISCRETE -> {
                val list = caps.allowed ?: listOf(2.0, 2.5, 3.0, 3.5)
                val first = list.firstOrNull() ?: 2.0
                val last = list.lastOrNull() ?: 3.0
                first to last
            }
            DeviceCapabilities.Mode.INCREMENT -> {
                val min = caps.min ?: 1.0
                val max = caps.max ?: 3.0
                min to max
            }
            else -> 2.0 to 3.0
        }
    }
    var easy by remember { mutableStateOf(easyDefault) }
    var hard by remember { mutableStateOf(hardDefault) }

    LaunchedEffect(easyDefault) { easy = easyDefault }
    LaunchedEffect(hardDefault) { hard = hardDefault }

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

            Divider()

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Quick Start", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = easy.toString(),
                            onValueChange = { it.toDoubleOrNull()?.let { v -> easy = v } },
                            label = { Text("Easy speed") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = hard.toString(),
                            onValueChange = { it.toDoubleOrNull()?.let { v -> hard = v } },
                            label = { Text("Hard speed") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minutes.toString(),
                            onValueChange = { it.toIntOrNull()?.let { m -> minutes = m.coerceIn(5, 120) } },
                            label = { Text("Minutes") },
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    Button(
                        onClick = {
                            WorkoutService.startQuick(ctx, minutes, easy, hard)
                            Toast.makeText(
                                ctx,
                                "Starting: $minutes min (easy $easy, hard $hard)…",
                                Toast.LENGTH_SHORT
                            ).show()
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
                        "Starting workout… check notification",
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
