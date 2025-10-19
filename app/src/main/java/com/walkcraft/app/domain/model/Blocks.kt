package com.walkcraft.app.domain.model

sealed interface Block {
    val label: String
    val durationSec: Int
}

data class SteadyBlock(
    override val label: String,
    override val durationSec: Int,
    val targetSpeed: Double
) : Block

data class RampBlock(
    override val label: String,
    override val durationSec: Int,
    val fromSpeed: Double,
    val toSpeed: Double
) : Block
