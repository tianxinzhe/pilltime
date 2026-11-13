package com.medtimer.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.medtimer.app.data.entity.DoseLog
import com.medtimer.app.domain.model.DoseStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {

    // 有效记录：voidedAt IS NULL

    @Query("SELECT * FROM dose_log WHERE scheduleId = :scheduleId AND plannedAt = :plannedAt AND voidedAt IS NULL LIMIT 1")
    suspend fun findActive(scheduleId: Long, plannedAt: Instant): DoseLog?

    @Query("SELECT * FROM dose_log WHERE plannedAt >= :from AND plannedAt < :to AND voidedAt IS NULL ORDER BY plannedAt")
    fun observeInRange(from: Instant, to: Instant): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_log WHERE plannedAt >= :from AND plannedAt < :to AND voidedAt IS NULL ORDER BY plannedAt DESC")
    suspend fun getInRange(from: Instant, to: Instant): List<DoseLog>

    @Query("SELECT * FROM dose_log WHERE profileId = :profileId AND plannedAt >= :from AND plannedAt < :to AND voidedAt IS NULL ORDER BY plannedAt")
    fun observeInRangeForProfile(profileId: Long, from: Instant, to: Instant): Flow<List<DoseLog>>

    // PENDING / DUE 是计算态，一般不落库；此方法仅作补算兜底
    @Query("SELECT * FROM dose_log WHERE plannedAt < :now AND voidedAt IS NULL AND status IN ('PENDING','DUE')")
    suspend fun getUnresolvedBefore(now: Instant): List<DoseLog>

    @Insert
    suspend fun insert(log: DoseLog): Long

    @Update
    suspend fun update(log: DoseLog)

    @Query("SELECT * FROM dose_log WHERE id = :id")
    suspend fun getById(id: Long): DoseLog?

    @Query("SELECT COUNT(*) FROM dose_log WHERE status = :status AND plannedAt >= :from AND plannedAt < :to AND voidedAt IS NULL")
    suspend fun countByStatus(status: DoseStatus, from: Instant, to: Instant): Int

    @Query("DELETE FROM dose_log WHERE medicineId = :medicineId")
    suspend fun deleteByMedicine(medicineId: Long)
}