package com.facegate.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey val date : String,   // "yyyy-MM-dd"
    val name             : String,
    val createdAt        : Long,
)