package com.walkcraft.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.walkcraft.app.data.export.CsvExporter
import com.walkcraft.app.data.export.idOrHash
import com.walkcraft.app.data.export.workoutDisplayName
import com.walkcraft.app.data.history.HistoryRepository
import com.walkcraft.app.domain.format.SpeedFmt
import com.walkcraft.app.domain.format.TimeFmt
import com.walkcraft.app.domain.metric.Distance
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.ui.share.ShareCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        HistoryRepository.from(context.applicationContext)
    }
    val session by repository.observeSession(sessionId).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = session?.workoutDisplayName() ?: "Session"
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share (.csv)") },
                            enabled = session != null,
                            onClick = {
                                menuExpanded = false
                                val current = session ?: return@DropdownMenuItem
                                scope.launch {
                                    val csv = withContext(Dispatchers.Default) {
                                        CsvExporter.sessionToCsv(current)
                                    }
                                    val fileName = "session-${current.idOrHash()}.csv"
                                    ShareCsv.shareTextAsCsv(
                                        context = context,
                                        fileName = fileName,
                                        csv = csv,
                                        chooserTitle = "Share session"
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (session == null) {
            Text(
                text = "Session not found",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
            )
        } else {
            SessionDetailContent(
                session = session!!,
                contentPadding = padding
            )
        }
    }
}

@Composable
private fun SessionDetailContent(session: Session, contentPadding: PaddingValues) {
    val distance = Distance.of(session)
    val distanceText = Distance.pretty(distance) + " " + Distance.label(session.unit)
    val totalSeconds = ((session.endedAt - session.startedAt) / 1000L).toInt().coerceAtLeast(0)
    val durationText = TimeFmt.hMmSs(totalSeconds)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = session.workoutDisplayName(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(session.startedAt)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Duration: $durationText",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Distance: $distanceText",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            Text(
                text = "Blocks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        items(session.segments) { segment ->
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Block ${segment.blockIndex}",
                    style = MaterialTheme.typography.bodyMedium
                )
                val speedText = SpeedFmt.pretty(segment.actualSpeed, session.unit, null)
                val duration = TimeFmt.mmSs(segment.durationSec)
                Text(
                    text = "$speedText @ $duration",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
