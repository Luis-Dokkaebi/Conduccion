package com.dms.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MicroSleepEventDao {
    @Insert
    suspend fun insertEvent(event: MicroSleepEvent)

    @Query("SELECT * FROM MicroSleepEvents")
    suspend fun getAllEvents(): List<MicroSleepEvent>

    @Query("DELETE FROM MicroSleepEvents WHERE id IN (:ids)")
    suspend fun deleteEvents(ids: List<Int>)
}
