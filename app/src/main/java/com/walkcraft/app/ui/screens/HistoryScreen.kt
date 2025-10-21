package com.walkcraft.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.walkcraft.app.ui.Routes
import com.walkcraft.app.ui.share.ShareCsv
import com.walkcraft.app.domain.format.SpeedFmt
import com.walkcraft.app.domain.format.TimeFmt
import com.walkcraft.app.domain.metric.Distance
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private typealias Session = com.walkcraft.app.domain.model.Session

internal const val SNACKBAR_SAVED_SESSION_ID_KEY = "snackbar_saved_session_id"

internal fun consumeSavedSessionMessage(handle: SavedStateHandle?): String? {
    val pending = handle?.get<String>(SNACKBAR_SAVED_SESSION_ID_KEY)
    if (pending != null) {
        handle.remove<String>(SNACKBAR_SAVED_SESSION_ID_KEY)
        return "Workout saved"
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: HistoryViewModel = viewModel()
) {
    val items by vm.sessions.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val previousEntry = navController.previousBackStackEntry
    val snackbarMessage = remember(previousEntry) {
        consumeSavedSessionMessage(previousEntry?.savedStateHandle)
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Clear history") },
                            onClick = {
                                showMenu = false
                                scope.launch { vm.clearAll() }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export all (.csv)") },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    val csv = withContext(Dispatchers.Default) { vm.exportCsv() }
                                    val fileName = "walkcraft-sessions-${System.currentTimeMillis()}.csv"
                                    ShareCsv.shareTextAsCsv(
                                        context = ctx,
                                        fileName = fileName,
                                        csv = csv,
                                        chooserTitle = "Export history"
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No sessions yet.")
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(items, key = { it.id }) { session ->
                    HistoryRow(
                        session = session,
                        onClick = { navController.navigate(Routes.historyDetail(session.id)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(session: Session, onClick: () -> Unit) {
    val title = session.workoutName
        ?: DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.endedAt))
    val totalSec = session.segments.sumOf { it.durationSec }
    val dur = TimeFmt.hMmSs(totalSec)
    val dist = Distance.of(session)
    val distText = Distance.pretty(dist) + " " + Distance.label(session.unit)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("$dur • $distText", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        session.segments.take(6).forEach { seg ->
            val segDur = TimeFmt.mmSs(seg.durationSec)
            val speedText = SpeedFmt.pretty(seg.actualSpeed, session.unit, null)
            Text("Block ${seg.blockIndex}: $speedText @ $segDur", style = MaterialTheme.typography.bodySmall)
        }
    }
}
