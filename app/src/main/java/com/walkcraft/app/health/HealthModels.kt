package com.walkcraft.app.health

enum class HealthConnectAvailability { NOT_INSTALLED, INSTALLED_NO_PERMISSION, READY }

data class HealthSummary(
    val averageHeartRateBpm: Int? = null,
    val totalSteps: Int? = null
)
