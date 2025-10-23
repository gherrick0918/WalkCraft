package com.walkcraft.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permissions.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

object HealthConnectHelper {
    // Read-only for MVP
    val REQUIRED_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    /** Returns SDK status and, if needed, launches Play to install/enable provider. */
    fun ensureAvailableOrLaunchInstall(context: Context): Boolean {
        val providerPkg = "com.google.android.apps.healthdata"
        val status = HealthConnectClient.getSdkStatus(context, providerPkg)
        if (status == HealthConnectClient.SDK_UNAVAILABLE) return false
        if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            // Open Play with the Health Connect onboarding URL
            val uri = "market://details?id=$providerPkg&url=healthconnect%3A%2F%2Fonboarding"
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uri)
                    putExtra("overlay", true)
                    putExtra("callerId", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return false
        }
        return true
    }

    fun client(context: Context): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(client: HealthConnectClient): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    fun launchPermissionUi(
        launcher: ActivityResultLauncher<Set<androidx.health.connect.client.permissions.HealthPermission>>
    ) {
        launcher.launch(REQUIRED_PERMISSIONS)
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()
}
