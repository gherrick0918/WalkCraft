package com.walkcraft.app.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

object HealthConnectManager {

    /** Minimal read set for MVP (expand later as needed). */
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    // NEW: Define the provider package name for the Play Store.
    private const val providerPackageName = "com.google.android.apps.healthdata"

    // UPDATED: sdkStatus now requires the provider package name.
    fun sdkStatus(context: Context): Int =
        HealthConnectClient.getSdkStatus(context, providerPackageName)

    /**
     * Open the Play Store (or app details) to install/update the provider.
     */
    fun openInstallOrUpdate(context: Context) {
        val marketUri = Uri.parse("market://details?id=$providerPackageName")
        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$providerPackageName")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, marketUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Open Health Connect’s App Info (useful if the user needs to toggle something manually).
     */
    fun openAppInfo(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", providerPackageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Compose/Activity uses this to build the ActivityResult contract.
     */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    fun client(context: Context): HealthConnectClient =
        HealthConnectClient.getOrCreate(context)
}
