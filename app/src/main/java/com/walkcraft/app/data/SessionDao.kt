package com.walkcraft.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(e: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY startMs DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<SessionEntity>
}
