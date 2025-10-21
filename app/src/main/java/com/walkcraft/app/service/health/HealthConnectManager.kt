package com.walkcraft.app.service.health

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class HealthConnectAvailability {
    Installed,
    NeedsInstall,
    NotSupported,
}

data class HealthSummary(
    val averageHeartRateBpm: Int? = null,
    val totalSteps: Int? = null,
)

interface HealthClientFacade {
    suspend fun grantedPermissions(): Set<HealthPermission>
    suspend fun createPermissionRequestIntent(permissions: Set<HealthPermission>): IntentSender
    suspend fun readSummary(start: Instant, end: Instant): HealthSummary
}

private class RealHealthClientFacade(
    private val client: HealthConnectClient,
) : HealthClientFacade {
    override suspend fun grantedPermissions(): Set<HealthPermission> =
        client.permissionController.getGrantedPermissions()

    override suspend fun createPermissionRequestIntent(permissions: Set<HealthPermission>): IntentSender =
        client.permissionController.createRequestPermissionIntent(permissions)

    override suspend fun readSummary(start: Instant, end: Instant): HealthSummary {
        val request = AggregateRequest(
            metrics = setOf(
                StepsRecord.COUNT_TOTAL,
                HeartRateRecord.BPM_AVG,
            ),
            timeRangeFilter = TimeRangeFilter.between(start, end),
        )
        val response = client.aggregate(request)
        val steps = response[StepsRecord.COUNT_TOTAL]?.toInt()
        val bpm = response[HeartRateRecord.BPM_AVG]?.roundToInt()
        return HealthSummary(averageHeartRateBpm = bpm, totalSteps = steps)
    }
}

private const val HEALTH_CONNECT_PACKAGE_NAME = "com.google.android.apps.healthdata"

class HealthConnectManager(
    private val context: Context,
    private val sdkStatusProvider: (Context) -> Int = { ctx ->
        HealthConnectClient.getSdkStatus(ctx, HEALTH_CONNECT_PACKAGE_NAME)
    },
    private val clientProvider: () -> HealthClientFacade? = {
        runCatching { HealthConnectClient.getOrCreate(context) }
            .getOrNull()
            ?.let { RealHealthClientFacade(it) }
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val requiredPermissions: Set<HealthPermission> = setOf(
        HealthPermission.createReadPermission(StepsRecord::class),
        HealthPermission.createReadPermission(HeartRateRecord::class),
    )

    suspend fun availability(): HealthConnectAvailability = withContext(ioDispatcher) {
        when (val status = sdkStatusProvider(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Installed
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NeedsInstall
            HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailability.NotSupported
            else -> HealthConnectAvailability.NeedsInstall
        }
    }

    suspend fun hasPermissions(): Boolean = withContext(ioDispatcher) {
        val client = clientProvider() ?: return@withContext false
        val granted = runCatching { client.grantedPermissions() }.getOrElse { emptySet() }
        requiredPermissions.all { it in granted }
    }

    suspend fun permissionRequestIntent(): IntentSender? = withContext(ioDispatcher) {
        val client = clientProvider() ?: return@withContext null
        runCatching { client.createPermissionRequestIntent(requiredPermissions) }.getOrNull()
    }

    suspend fun readSummary(start: Instant, end: Instant): HealthSummary = withContext(ioDispatcher) {
        val client = clientProvider() ?: return@withContext HealthSummary()
        runCatching { client.readSummary(start, end) }.getOrElse { HealthSummary() }
    }

    fun installAppIntent(): Intent {
        val marketUri = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE_NAME")
        val marketIntent = Intent(Intent.ACTION_VIEW, marketUri)
        val pm = context.packageManager
        return if (marketIntent.resolveActivity(pm) != null) {
            marketIntent
        } else {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE_NAME")
            )
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
