package com.walkcraft.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startMs: Long,
    val endMs: Long,
    val steps: Long,
    val savedToHc: Boolean
)
