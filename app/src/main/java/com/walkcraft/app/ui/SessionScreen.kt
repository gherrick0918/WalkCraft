package com.walkcraft.app.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walkcraft.app.health.StepsSessionViewModel

@Composable
fun SessionScreen() {
    val vm: StepsSessionViewModel = viewModel()
    val session by vm.session.collectAsStateWithLifecycle()
    val stepsToday by vm.todaySteps.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.onResume() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Today’s steps: ${stepsToday ?: 0}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))

        Row {
            Button(onClick = { vm.start() }, enabled = !session.active) { Text("Start") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { vm.stop() }, enabled = session.active) { Text("Stop") }
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

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.refreshToday() }) { Text("Refresh Today") }
    }
}
