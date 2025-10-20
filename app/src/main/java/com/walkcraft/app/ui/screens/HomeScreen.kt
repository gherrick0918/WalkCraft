package com.walkcraft.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider // FIX: Import HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
                Text(
                    buildString {
                        append("Unit: ")
                        append(it.caps.unit)
                        append(", Mode: ")
                        append(it.caps.mode)
                        if (it.caps.mode == DeviceCapabilities.Mode.DISCRETE) {
                            append(", allowed: ")
                            append(it.caps.allowed?.joinToString())
                        } else {
                            append(", min: ")
                            append(it.caps.min)
                            append(", max: ")
                            append(it.caps.max)
                            append(", increment: ")
                            append(it.caps.increment)
                        }
                        append(", policy: ")
                        append(it.policy.strategy)
                    }
                )
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
            HorizontalDivider() // FIX: Renamed Divider to HorizontalDivider
            // Debug action (kept until Run is wired to service)
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
