package com.walkcraft.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission // This import might become unused
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

class HealthConnectManager(private val context: Context) {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /** SDK status: SDK_UNAVAILABLE / PROVIDER_UPDATE_REQUIRED / AVAILABLE, etc. */
    fun sdkStatus(): Int =
        HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata")

    /** Permissions our MVP cares about. */
    // Change the type from Set<HealthPermission> to Set<String>
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    /** Check if all required permissions are already granted. */
    suspend fun hasAllPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }
}
