package com.walkcraft.app.service.health

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permissions.HealthPermission
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class HealthConnectManagerTest {
    private lateinit var context: Context
    private lateinit var fakeClient: FakeHealthClientFacade
    private var status: Int = HealthConnectClient.SDK_AVAILABLE

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeClient = FakeHealthClientFacade(context)
        status = HealthConnectClient.SDK_AVAILABLE
    }

    private fun manager(): HealthConnectManager = HealthConnectManager(
        context = context,
        sdkStatusProvider = { status },
        clientProvider = { fakeClient },
        ioDispatcher = Dispatchers.Unconfined
    )

    @Test
    fun availabilityReflectsSdkStatus() = runTest {
        status = HealthConnectClient.SDK_AVAILABLE
        assertEquals(HealthConnectAvailability.Installed, manager().availability())

        status = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_NOT_INSTALLED
        assertEquals(HealthConnectAvailability.NeedsInstall, manager().availability())

        status = HealthConnectClient.SDK_UNAVAILABLE_DEVICE_NOT_SUPPORTED
        assertEquals(HealthConnectAvailability.NotSupported, manager().availability())
    }

    @Test
    fun hasPermissionsChecksAgainstRequiredSet() = runTest {
        fakeClient.granted = emptySet()
        assertFalse(manager().hasPermissions())

        fakeClient.granted = manager().requiredPermissions
        assertTrue(manager().hasPermissions())
    }

    @Test
    fun readSummaryPassesThroughClientValues() = runTest {
        fakeClient.summary = HealthSummary(averageHeartRateBpm = 128, totalSteps = 1534)
        val start = Instant.now()
        val summary = manager().readSummary(start, start.plusSeconds(30))
        assertEquals(128, summary.averageHeartRateBpm)
        assertEquals(1534, summary.totalSteps)
    }

    private class FakeHealthClientFacade(context: Context) : HealthClientFacade {
        var granted: Set<HealthPermission> = emptySet()
        var summary: HealthSummary = HealthSummary()
        private val intentSender: IntentSender = PendingIntent.getActivity(
            context,
            0,
            Intent(context, Any::class.java),
            PendingIntent.FLAG_IMMUTABLE
        ).intentSender

        override suspend fun grantedPermissions(): Set<HealthPermission> = granted

        override suspend fun createPermissionRequestIntent(permissions: Set<HealthPermission>) = intentSender

        override suspend fun readSummary(start: Instant, end: Instant): HealthSummary = summary
    }
}
