package com.dms.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "MicroSleepEvents")
data class MicroSleepEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val earValue: Float,
    val durationSeconds: Float,
    val gpsLat: Double,
    val gpsLng: Double
)
