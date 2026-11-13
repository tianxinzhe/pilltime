package com.alex.pilltime.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.alex.pilltime.data.entity.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile ORDER BY id")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun getById(id: Long): Profile?

    @Upsert
    suspend fun upsert(profile: Profile): Long

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun count(): Int

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun deleteById(id: Long)
}