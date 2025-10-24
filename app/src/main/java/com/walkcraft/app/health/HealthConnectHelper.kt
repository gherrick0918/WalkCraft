package com.walkcraft.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object HealthConnectHelper {
    // Read-only for MVP
    val REQUIRED_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    // Write permission for saving sessions
    val PERM_WRITE_EXERCISE: String =
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)

    suspend fun hasWriteExercisePermission(client: HealthConnectClient): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.contains(PERM_WRITE_EXERCISE)
    }

    /** Returns SDK status and, if needed, launches Play to install/enable provider. */
    fun ensureAvailableOrLaunchInstall(context: Context): Boolean {
        val providerPkg = "com.google.android.apps.healthdata"
        // Using default provider name avoids signature drift across minor versions
        val status = HealthConnectClient.getSdkStatus(context)
        if (status == HealthConnectClient.SDK_UNAVAILABLE) return false
        if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            // Open Play with the Health Connect onboarding URL
            val uri = "market://details?id=$providerPkg&url=healthconnect%3A%2F%2Fonboarding"
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uri)
                    putExtra("overlay", true)
                    putExtra("callerId", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return false
        }
        return true
    }

    fun client(context: Context): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(client: HealthConnectClient): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    fun launchPermissionUi(
        launcher: ActivityResultLauncher<Set<String>>
    ) {
        launcher.launch(REQUIRED_PERMISSIONS)
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun readTodaySteps(
        client: HealthConnectClient,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val start = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
        val end = start.plus(1, ChronoUnit.DAYS)
        val resp = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return resp.records.sumOf { it.count }
    }

    suspend fun readStepsForDate(
        client: HealthConnectClient,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val start = date.atStartOfDay(zoneId).toInstant()
        val end = start.plus(1, ChronoUnit.DAYS)
        val resp = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return resp.records.sumOf { it.count }
    }

    suspend fun readStepsLastNDays(
        client: HealthConnectClient,
        days: Int = 7,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Pair<LocalDate, Long>> {
        val today = LocalDate.now(zoneId)
        val results = mutableListOf<Pair<LocalDate, Long>>()
        for (i in (days - 1) downTo 0) {
            val d = today.minusDays(i.toLong())
            val total = readStepsForDate(client, d, zoneId)
            results += d to total
        }
        return results
    }
}
