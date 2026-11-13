package com.alex.pilltime.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 逐步减量（taper）步骤。
 */
@Entity(
    tableName = "taper_step",
    foreignKeys = [ForeignKey(
        entity = Schedule::class,
        parentColumns = ["id"],
        childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("scheduleId")]
)
data class TaperStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val startDate: LocalDate,
    val doseQtyX10: Int
)