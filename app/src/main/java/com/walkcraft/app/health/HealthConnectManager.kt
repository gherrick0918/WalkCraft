package com.walkcraft.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val healthConnectClient: HealthConnectClient? by lazy {
        if (sdkStatus() == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    // --- FIX APPLIED HERE ---
    // The requiredPermissions set is now correctly typed as Set<HealthPermission>
    // instead of Set<String>.
    val requiredPermissions: Set<HealthPermission> = setOf(
        HealthPermission.createReadPermission(StepsRecord::class),
        HealthPermission.createWritePermission(StepsRecord::class)
        // TODO: Add any other permissions your app requires here.
    )

    /**
     * Returns the availability status of the Health Connect SDK.
     */
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    /**
     * Creates an ActivityResultContract to request Health Connect permissions.
     */
    fun requestPermissionsContract() =
        healthConnectClient?.permissionController?.createRequestPermissionResultContract()

    /**
     * Determines if all the required permissions have been granted by the user.
     */
    suspend fun hasAllPermissions(): Boolean {
        // The 'healthConnectClient' can be null if the SDK is not installed,
        // in which case we can safely return false.
        val grantedPermissions = healthConnectClient?.permissionController?.getGrantedPermissions()
        return grantedPermissions?.containsAll(requiredPermissions) == true
    }
}
