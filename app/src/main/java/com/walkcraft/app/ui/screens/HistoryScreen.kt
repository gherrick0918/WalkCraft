package com.walkcraft.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.walkcraft.app.domain.format.TimeFmt
import com.walkcraft.app.domain.metric.Distance
import com.walkcraft.app.domain.model.CompletedSegment
import com.walkcraft.app.domain.model.Session
import com.walkcraft.app.domain.model.SpeedUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val sample = remember {
        Session(
            workoutId = "sample",
            startedAt = 0L,
            endedAt = 0L,
            unit = SpeedUnit.MPH,
            segments = listOf(
                CompletedSegment(0, 3.0, 600),
                CompletedSegment(1, 2.5, 900)
            )
        )
    }
    val totalSec = sample.segments.sumOf { it.durationSec }
    val durationText = TimeFmt.hMmSs(totalSec)
    val dist = Distance.of(sample)
    val distText = Distance.pretty(dist) + " " + Distance.label(sample.unit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sample Session", fontWeight = FontWeight.Bold)
                    Text("$durationText • $distText")
                    sample.segments.forEach { segment ->
                        val segDuration = TimeFmt.mmSs(segment.durationSec)
                        Text("Block ${segment.blockIndex}: ${segment.actualSpeed} @ $segDuration")
                    }
                }
            }
        }
    }
}
