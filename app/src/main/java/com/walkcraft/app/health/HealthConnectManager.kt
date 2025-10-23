package com.walkcraft.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

/**
 * Thin wrapper over the official Health Connect client.
 * No project-specific facades or String-based permissions.
 */
class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    /** Availability per SDK constants (SDK_AVAILABLE, SDK_UNAVAILABLE_PROVIDER_NOT_INSTALLED, etc). */
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    /** The set of permissions we request for MVP. Extend as needed. */
    val requiredPermissions: Set<HealthPermission> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
        // Add write permissions later if/when we record to HC.
        // HealthPermission.getWritePermission(StepsRecord::class)
    )

    /** True if all required permissions are already granted. */
    suspend fun hasAllPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions(requiredPermissions)
        return granted.containsAll(requiredPermissions)
    }

    /**
     * Activity Result contract for requesting Health Connect permissions.
     * Usage (Compose):
     *   val launcher = rememberLauncherForActivityResult(
     *       PermissionController.createRequestPermissionResultContract()
     *   ) { granted -> ... }
     *   launcher.launch(manager.requiredPermissions)
     */
    fun permissionRequestContract() =
        PermissionController.createRequestPermissionResultContract()

    fun client(): HealthConnectClient = client
}
