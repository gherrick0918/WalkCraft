package com.walkcraft.app.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val healthConnectClient: HealthConnectClient? by lazy {
        // Check if the Health Connect SDK is available on the device.
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE) {
            null
        } else {
            HealthConnectClient.getOrCreate(context)
        }
    }

    // --- FIX APPLIED IN PREVIOUS STEP ---
    // The permission-granting syntax has been updated from 'createReadPermission'
    // to 'getReadPermission' to match the newer Health Connect API.
    val permissions: Set<HealthPermission> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class)
        // TODO: Add any other permissions your app requires here.
    )

    /**
     * Returns the availability status of the Health Connect SDK.
     */
    fun getSdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    /**
     * Creates an ActivityResultContract to request Health Connect permissions.
     */
    // FIX: Update the permission request method
    fun requestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    /**
     * Determines if all the required permissions have been granted by the user.
     */
    suspend fun hasAllPermissions(): Boolean {
        return healthConnectClient?.permissionController?.getGrantedPermissions()
            ?.containsAll(permissions) == true
    }


}
