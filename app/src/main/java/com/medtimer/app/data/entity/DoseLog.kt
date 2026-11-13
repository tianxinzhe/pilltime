package com.medtimer.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medtimer.app.domain.model.DoseStatus
import java.time.Instant

/**
 * 服药记录。voidedAt 非空表示该条已作废（保留可追溯）。
 */
@Entity(
    tableName = "dose_log",
    indices = [Index(value = ["scheduleId", "plannedAt"]), Index("medicineId"), Index("plannedAt")]
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val medicineId: Long,
    val profileId: Long = 1,
    val plannedAt: Instant,
    val actedAt: Instant? = null,
    val status: DoseStatus,
    val delayMinutes: Int? = null,
    val certain: Boolean = true,
    val reason: String? = null,
    val voidedAt: Instant? = null   // 撤销标记：非空表示该条已作废
)