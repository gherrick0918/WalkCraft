package com.walkcraft.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

/**
 * Self-contained Health Connect permission card.
 * - Detects availability
 * - Shows status
 * - Launches the official permission sheet via ActivityResult
 */
@Composable
fun HealthConnectPermissionCard(
    modifier: Modifier = Modifier,
    onPermissionsChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    // Status of the provider (installed / needs update / unavailable)
    var sdkStatus by remember { mutableStateOf(HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata")) }

    // Client & required typed permissions
    val client = remember { HealthConnectClient.getOrCreate(context) }
    val requiredPermissions = remember {
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )
    }

    var granted by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    // Initial permission check when available
    LaunchedEffect(sdkStatus) {
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            val already = client.permissionController.getGrantedPermissions()
            granted = already.containsAll(requiredPermissions)
            onPermissionsChanged(granted)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        loading = false
        granted = grantedPermissions.containsAll(requiredPermissions)
        onPermissionsChanged(granted)
    }


    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val subtitle = when (sdkStatus) {
                HealthConnectClient.SDK_AVAILABLE ->
                    if (granted) "Permission granted" else "Permission required"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    "Health Connect needs an update"
                HealthConnectClient.SDK_UNAVAILABLE ->
                    "Health Connect not available on this device"
                else -> "Health Connect unavailable"
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))

            val (buttonText, onClick) = when (sdkStatus) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (granted) {
                        "Granted" to ({ /* no-op */ })
                    } else {
                        "Grant" to ({
                            loading = true
                            permissionLauncher.launch(requiredPermissions)
                        })
                    }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    "Update" to ({
                        openPlayStore(context, "com.google.android.apps.healthdata")
                        // When returning from Play, recompute status
                        sdkStatus = HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata")
                    })
                }
                else -> {
                    "Learn more" to ({
                        openBrowser(context, "https://support.google.com/fit/answer/12131752")
                    })
                }
            }

            Button(
                onClick = onClick,
                enabled = !(sdkStatus == HealthConnectClient.SDK_AVAILABLE && granted) && !loading
            ) { Text(buttonText) }
        }
    }
}

private fun openPlayStore(context: Context, pkg: String) {
    val play = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(play)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openBrowser(context: Context, url: String) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
