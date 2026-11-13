package com.medtimer.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.medtimer.app.data.entity.TaperStep
import kotlinx.coroutines.flow.Flow

@Dao
interface TaperStepDao {

    @Query("SELECT * FROM taper_step WHERE scheduleId = :scheduleId ORDER BY startDate")
    fun observeBySchedule(scheduleId: Long): Flow<List<TaperStep>>

    @Query("SELECT * FROM taper_step WHERE scheduleId = :scheduleId ORDER BY startDate")
    suspend fun getBySchedule(scheduleId: Long): List<TaperStep>

    @Upsert
    suspend fun upsertAll(steps: List<TaperStep>)

    @Query("DELETE FROM taper_step WHERE scheduleId = :scheduleId")
    suspend fun deleteBySchedule(scheduleId: Long)
}