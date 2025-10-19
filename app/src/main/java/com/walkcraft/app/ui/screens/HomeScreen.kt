package com.walkcraft.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.walkcraft.app.service.WorkoutService

@Composable
fun HomeScreen(
    onDeviceSetup: () -> Unit,
    onRun: () -> Unit,
    onHistory: () -> Unit
) {
    val ctx = LocalContext.current
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
