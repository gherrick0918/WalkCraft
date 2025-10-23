package com.walkcraft.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

object HealthConnectManager {
    /** The provider package name used by Health Connect on Android 13 and below. */
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    /** Set of permissions we want. Extend as needed. */
    val PERMISSIONS: Set<HealthPermission> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    /**
     * Returns SDK status:
     *  - SDK_AVAILABLE
     *  - SDK_UNAVAILABLE
     *  - SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
     */
    fun sdkStatus(context: Context): Int =
        HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)

    /** Create or obtain the client (only call if status is not UNAVAILABLE). */
    fun client(context: Context): HealthConnectClient =
        HealthConnectClient.getOrCreate(context)

    /** Open Play Store to install/update the Health Connect provider (Android 13 and below). */
    fun launchProviderInstall(context: Context) {
        val uri = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse(uri)
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
