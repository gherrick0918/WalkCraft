package com.walkcraft.app.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

class HealthConnectManager(private val context: Context) {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }
    private val permissionsController get() = client.permissionController

    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(
            "com.google.android.apps.healthdata",
            PackageManager.PackageInfoFlags.of(0)
        )
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    // FIX: Changed the type from Set<HealthPermission> to Set<String>
    val required: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    suspend fun hasAll(): Boolean {
        // The getGrantedPermissions() method correctly returns a Set<String>
        val granted: Set<String> = permissionsController.getGrantedPermissions()
        return required.all { it in granted }
    }

    fun requestContract() = PermissionController.createRequestPermissionResultContract()
}
