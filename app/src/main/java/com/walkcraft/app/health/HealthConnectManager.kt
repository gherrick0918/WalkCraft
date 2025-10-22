package com.walkcraft.app.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

/**
 * Works with HC versions where permissions are Strings and there is no
 * createRequestPermissionIntent(). Use the activity result contract instead.
 */
class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val permissionController get() = client.permissionController

    /** HC app present? (no SDK call needed; avoids missing APIs) */
    fun isAvailable(): Boolean = isPackageInstalled(context, "com.google.android.apps.healthdata")

    /** Required permissions as Strings (matches getGrantedPermissions(): Set<String>) */
    val requiredPermissions: Set<String> = setOf(
        PermissionController.createReadPermission(StepsRecord::class),
        PermissionController.createReadPermission(HeartRateRecord::class),
        // Example for write: PermissionController.createWritePermission(StepsRecord::class)
    )

    /** Current grants (string-based) */
    suspend fun hasAllPermissions(): Boolean {
        val granted: Set<String> = permissionController.getGrantedPermissions()
        return requiredPermissions.all { it in granted }
    }

    /** Activity Result contract for requesting permissions (string-based). */
    fun requestPermissionsContract() =
        PermissionController.createRequestPermissionResultContract()

    private fun isPackageInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) { false }
}
