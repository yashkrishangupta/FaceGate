package com.facegate.storage.dao

import androidx.room.*
import com.facegate.storage.entity.OverrideEntity

@Dao
interface OverrideDao {
    @Insert
    suspend fun insert(override: OverrideEntity)
    @Query("SELECT * FROM timetable_overrides WHERE sessionId = :sessionId")
    suspend fun getForSession(sessionId: String): List<OverrideEntity>
    @Query("SELECT * FROM timetable_overrides ORDER BY changedAt DESC")
    suspend fun getAll(): List<OverrideEntity>
}
