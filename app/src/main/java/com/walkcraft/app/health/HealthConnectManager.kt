package com.walkcraft.app.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

object HealthConnectManager {

    /** Typed permissions we need for MVP. */
    val requiredPermissions: Set<HealthPermission> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    private const val PROVIDER = "com.google.android.apps.healthdata"

    /** Status of Health Connect provider in THIS profile. */
    fun sdkStatus(context: Context): Int =
        HealthConnectClient.getSdkStatus(context, PROVIDER)

    /** Correct ActivityResult contract for requesting HC permissions (typed). */
    fun permissionContract():
            ActivityResultContract<Set<HealthPermission>, Set<HealthPermission>> =
        PermissionController.createRequestPermissionResultContract()

    /** Open Play Store to install/update the provider. */
    fun openProviderOnPlay(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun client(context: Context): HealthConnectClient =
        HealthConnectClient.getOrCreate(context)
}
