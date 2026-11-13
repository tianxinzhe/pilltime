package com.alex.pilltime.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.alex.pilltime.data.entity.StockChange
import kotlinx.coroutines.flow.Flow

@Dao
interface StockChangeDao {

    @Query("SELECT COALESCE(SUM(deltaQtyX10), 0) FROM stock_change WHERE medicineId = :medicineId")
    fun observeStock(medicineId: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(deltaQtyX10), 0) FROM stock_change WHERE medicineId = :medicineId")
    suspend fun sumByMedicine(medicineId: Long): Int

    @Query("SELECT COUNT(*) FROM stock_change WHERE reason = 'TAKEN' AND refId = :refId")
    suspend fun countTakenByRef(refId: Long): Int

    @Insert
    suspend fun insert(change: StockChange): Long

    @Query("SELECT * FROM stock_change WHERE medicineId = :medicineId ORDER BY at DESC LIMIT :limit")
    fun observeRecent(medicineId: Long, limit: Int): Flow<List<StockChange>>

    @Query("DELETE FROM stock_change WHERE medicineId = :medicineId")
    suspend fun deleteByMedicine(medicineId: Long)
}