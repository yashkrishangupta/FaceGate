package com.facegate.storage.dao

import androidx.room.*
import com.facegate.storage.entity.HolidayEntity

@Dao
interface HolidayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(holiday: HolidayEntity)
    @Query("DELETE FROM holidays WHERE date = :date")
    suspend fun delete(date: String)
    @Query("SELECT COUNT(*) FROM holidays WHERE date = :date")
    suspend fun isHoliday(date: String): Int
    @Query("SELECT * FROM holidays ORDER BY date ASC")
    suspend fun getAll(): List<HolidayEntity>
    @Query("SELECT * FROM holidays WHERE date >= :today ORDER BY date ASC")
    suspend fun getUpcoming(today: String): List<HolidayEntity>
}
