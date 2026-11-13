package com.medtimer.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.medtimer.app.data.entity.Schedule
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT s.* FROM schedule s INNER JOIN medicine m ON m.id = s.medicineId WHERE m.profileId = :profileId ORDER BY s.startDate, s.id")
    fun observeByProfile(profileId: Long): Flow<List<Schedule>>

    @Query("SELECT * FROM schedule WHERE medicineId = :medicineId ORDER BY id")
    fun observeByMedicine(medicineId: Long): Flow<List<Schedule>>

    @Query("SELECT * FROM schedule WHERE medicineId = :medicineId ORDER BY id")
    suspend fun getByMedicine(medicineId: Long): List<Schedule>

    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun getById(id: Long): Schedule?

    @Query("SELECT * FROM schedule WHERE enabled = 1 AND isPrn = 0")
    suspend fun getAllTimedEnabled(): List<Schedule>

    @Query("SELECT s.* FROM schedule s INNER JOIN medicine m ON m.id = s.medicineId WHERE m.profileId = :profileId AND s.enabled = 1 AND s.isPrn = 0")
    suspend fun getTimedEnabledForProfile(profileId: Long): List<Schedule>

    @Upsert
    suspend fun upsert(schedule: Schedule): Long

    @Query("DELETE FROM schedule WHERE id = :id")
    suspend fun deleteById(id: Long)
}