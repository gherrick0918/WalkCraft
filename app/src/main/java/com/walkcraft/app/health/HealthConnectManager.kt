package com.walkcraft.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import jakarta.inject.Singleton

enum class HcStatus { AVAILABLE, UPDATE_REQUIRED, NOT_INSTALLED, NOT_SUPPORTED }

@Singleton
class HealthConnectManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }
    private val perms get() = client.permissionController

    // Corrected line: Changed the type from Set<HealthPermission> to Set<String>
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun status(): HcStatus = when (
        HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata")
    ) {
        HealthConnectClient.SDK_AVAILABLE -> HcStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcStatus.UPDATE_REQUIRED
        HealthConnectClient.SDK_UNAVAILABLE -> HcStatus.NOT_INSTALLED
        else -> HcStatus.NOT_SUPPORTED
    }

    suspend fun hasAll(): Boolean =
        perms.getGrantedPermissions().containsAll(requiredPermissions)

    // Important: Update the contract's generic type to <Set<String>, Set<String>> as well
    fun requestPermissionsContract() =
        PermissionController.createRequestPermissionResultContract()

    fun playStoreIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
