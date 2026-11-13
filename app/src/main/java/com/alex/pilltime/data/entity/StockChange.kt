package com.alex.pilltime.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alex.pilltime.domain.model.StockReason
import java.time.Instant

/**
 * 库存流水。当前库存 = SUM(deltaQtyX10)，不单独存库存字段。
 */
@Entity(tableName = "stock_change", indices = [Index("medicineId"), Index("refId")])
data class StockChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val deltaQtyX10: Int,
    val reason: StockReason,
    val refId: Long? = null,
    val at: Instant,
    val note: String? = null
)