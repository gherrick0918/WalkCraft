package com.walkcraft.app.domain.model

data class CompletedSegment(
    val blockIndex: Int,
    val actualSpeed: Double,
    val durationSec: Int,
    val label: String? = null,
)

data class Session(
    val id: String,
    val workoutId: String?,
    val startedAt: Long,
    val endedAt: Long,
    val unit: SpeedUnit,
    val segments: List<CompletedSegment>,
    val notes: String? = null,
    val avgHr: Int? = null,
    val totalSteps: Int? = null,
    val workoutName: String? = null
)
