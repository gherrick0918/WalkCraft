package com.walkcraft.app.domain.model

data class Workout(
    val id: String,
    val name: String,
    val blocks: List<Block>,
    val createdAt: Long = System.currentTimeMillis()
)
