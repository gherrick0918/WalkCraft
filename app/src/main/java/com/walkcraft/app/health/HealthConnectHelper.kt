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

    // ---- Permissions (steps-only required for core features right now) ----
    val PERM_READ_STEPS: String =
        HealthPermission.getReadPermission(StepsRecord::class)

    val PERM_WRITE_EXERCISE: String =
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)

    /** App-wide "required" set = Steps only (HR may remain in manifest but isn't required at runtime). */
    val REQUIRED_PERMISSIONS: Set<String> = setOf(PERM_READ_STEPS)

    // ---- Client / availability ----
    fun client(context: Context): HealthConnectClient =
        HealthConnectClient.getOrCreate(context)

    /**
     * Returns true if provider is ready. If not, opens Play to install/enable, and returns false.
     */
    fun ensureAvailableOrLaunchInstall(context: Context): Boolean {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_UNAVAILABLE -> return false
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                val pkg = "com.google.android.apps.healthdata"
                val uri = "market://details?id=$pkg&url=healthconnect%3A%2F%2Fonboarding"
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setPackage("com.android.vending")
                        data = Uri.parse(uri)
                        putExtra("overlay", true)
                        putExtra("callerId", context.packageName)
                    }
                )
                return false
            }
        }
        return true
    }

    // ---- Permission helpers ----
    fun permissionContract() =
        PermissionController.createRequestPermissionResultContract()

    fun launchPermissionUi(launcher: ActivityResultLauncher<Set<String>>) {
        launcher.launch(REQUIRED_PERMISSIONS)
    }

    suspend fun hasStepsPermission(client: HealthConnectClient): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return PERM_READ_STEPS in granted
    }

    suspend fun hasWriteExercisePermission(client: HealthConnectClient): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return PERM_WRITE_EXERCISE in granted
    }

    /** Kept for compatibility; this is also steps-only now. */
    suspend fun hasAllPermissions(client: HealthConnectClient): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    // ---- Reads (steps) ----
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
    ): List<Pair<LocalDate, Long>> { // Changed (LocalDate, Long) to <LocalDate, Long>
        val today = LocalDate.now(zoneId)
        val out = mutableListOf<Pair<LocalDate, Long>>()
        for (i in (days - 1) downTo 0) {
            val d = today.minusDays(i.toLong())
            val total = readStepsForDate(client, d, zoneId)
            out += d to total
        }
        return out
    }
}
