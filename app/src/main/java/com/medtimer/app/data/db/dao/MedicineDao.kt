package com.medtimer.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.medtimer.app.data.entity.Medicine
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {

    @Query("SELECT * FROM medicine WHERE profileId = :profileId ORDER BY name")
    fun observeByProfile(profileId: Long): Flow<List<Medicine>>

    @Query("SELECT * FROM medicine WHERE id = :id")
    fun observeById(id: Long): Flow<Medicine?>

    @Query("SELECT * FROM medicine WHERE id = :id")
    suspend fun getById(id: Long): Medicine?

    @Query("SELECT * FROM medicine WHERE profileId = :profileId")
    suspend fun getAllOnce(profileId: Long): List<Medicine>

    @Upsert
    suspend fun upsert(medicine: Medicine): Long

    @Query("DELETE FROM medicine WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM medicine WHERE profileId = :profileId")
    suspend fun count(profileId: Long): Int
}