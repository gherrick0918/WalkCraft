package com.walkcraft.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walkcraft.app.history.HistoryViewModel
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen() {
    val vm: HistoryViewModel = viewModel()
    val items = vm.items.collectAsStateWithLifecycle().value
    val loading = vm.loading.collectAsStateWithLifecycle().value
    val error = vm.error.collectAsStateWithLifecycle().value

    val required = remember {
        setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }
    val permLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        if (required.all { it in granted }) vm.load(7)
    }

    LaunchedEffect(Unit) { vm.load(7) }

    val fmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Button(onClick = { vm.load(7) }, enabled = !loading) {
                Text("Refresh")
            }
            Spacer(Modifier.width(12.dp))
            if (error != null) {
                Button(onClick = { permLauncher.launch(required) }) {
                    Text("Grant Steps")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            loading -> Text("Loading…")
            error != null -> Text("History unavailable: $error")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(items) { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(row.date.format(fmt), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text("${row.steps}", style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )
                    }
                }
            }
        }
    }
}
