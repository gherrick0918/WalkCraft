package com.walkcraft.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.walkcraft.app.service.WorkoutService
import com.walkcraft.app.ui.theme.WalkCraftTheme

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; user choice handled by system */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeAskNotifPermission()

        setContent {
            WalkCraftTheme {
                Scaffold { innerPadding ->
                    DebugControls(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun maybeAskNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Composable
    private fun DebugControls(modifier: Modifier = Modifier) {
        Column(modifier = modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    WorkoutService.start(this@MainActivity)
                    Toast.makeText(
                        this@MainActivity,
                        "Starting workout… check notification",
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Text("Start")
                }
                Button(onClick = {
                    WorkoutService.sendAction(this@MainActivity, WorkoutService.ACTION_PAUSE)
                }) {
                    Text("Pause")
                }
                Button(onClick = {
                    WorkoutService.sendAction(this@MainActivity, WorkoutService.ACTION_RESUME)
                }) {
                    Text("Resume")
                }
                Button(onClick = {
                    WorkoutService.sendAction(this@MainActivity, WorkoutService.ACTION_SKIP)
                }) {
                    Text("Skip")
                }
                Button(onClick = {
                    WorkoutService.sendAction(this@MainActivity, WorkoutService.ACTION_STOP)
                }) {
                    Text("Stop")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("WalkCraft debug controls (PR 2)")
        }
    }
}
