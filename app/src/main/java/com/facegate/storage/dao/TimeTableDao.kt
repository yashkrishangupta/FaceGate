package com.facegate.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.facegate.storage.entity.TimetableEntity

@Dao
interface TimetableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimetableEntity)

    @Update
    suspend fun update(entry: TimetableEntity)

    @Query("DELETE FROM timetable WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM timetable WHERE dayOfWeek = :dayOfWeek ORDER BY periodNumber ASC")
    suspend fun getForDay(dayOfWeek: Int): List<TimetableEntity>

    @Query("SELECT * FROM timetable ORDER BY dayOfWeek ASC, periodNumber ASC")
    suspend fun getAll(): List<TimetableEntity>

    @Query("SELECT DISTINCT batch FROM timetable ORDER BY batch ASC")
    suspend fun getAllBatches(): List<String>

    @Query("SELECT DISTINCT subject FROM timetable ORDER BY subject ASC")
    suspend fun getAllSubjects(): List<String>
}