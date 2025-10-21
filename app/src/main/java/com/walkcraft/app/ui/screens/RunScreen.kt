package com.walkcraft.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walkcraft.app.domain.engine.EngineState
import com.walkcraft.app.ui.screens.run.RunUiState
import com.walkcraft.app.ui.screens.run.RunViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    onBack: () -> Unit,
    vm: RunViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()

    DisposableEffect(Unit) {
        vm.bind()
        onDispose { vm.unbind() }
    }

    LaunchedEffect(Unit) {
        var first = true
        vm.engineState.collect { state ->
            if (first) {
                first = false
                return@collect
            }
            if (state is EngineState.Finished || state is EngineState.Idle) {
                onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (val state = ui) {
                RunUiState.Idle -> Text("Not running", style = MaterialTheme.typography.titleMedium)
                is RunUiState.Running -> RunningPanel(state, vm)
                is RunUiState.Paused -> PausedPanel(state, vm)
                is RunUiState.Finished -> FinishedPanel(state, vm)
            }
        }
    }
}

@Composable
private fun RunningPanel(state: RunUiState.Running, vm: RunViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.blockLabel, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(state.remaining, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
        Text(state.speedText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        state.nextLabel?.let { next ->
            Text("Next: $next", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = vm::pause) { Text("Pause") }
            Button(onClick = vm::skip) { Text("Skip") }
            Button(onClick = vm::stop) { Text("Stop") }
        }
    }
}

@Composable
private fun PausedPanel(state: RunUiState.Paused, vm: RunViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.blockLabel, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(state.remaining, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = vm::resume) { Text("Resume") }
            Button(onClick = vm::stop) { Text("Stop") }
        }
    }
}

@Composable
private fun FinishedPanel(state: RunUiState.Finished, vm: RunViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Workout complete", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(state.summary, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Button(onClick = vm::stop) { Text("Done") }
    }
}
