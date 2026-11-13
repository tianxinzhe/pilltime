package com.alex.pilltime.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alex.pilltime.domain.model.AnchorType
import com.alex.pilltime.domain.model.MealType
import java.time.LocalDate

/**
 * 用药计划。服药时刻 Slot 由计划实时推导，不落库。
 */
@Entity(
    tableName = "schedule",
    foreignKeys = [ForeignKey(
        entity = Medicine::class,
        parentColumns = ["id"],
        childColumns = ["medicineId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("medicineId")]
)
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val anchorType: AnchorType,
    val clockHour: Int? = null,
    val clockMinute: Int? = null,
    val mealType: MealType? = null,
    val offsetMin: Int = 0,
    val doseQtyX10: Int,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val everyNDays: Int = 1,
    val weekdaysMask: Int? = null,
    val monthlyDay: Int? = null,
    val earlyMinutes: Int = 30,
    val lateMinutes: Int = 60,
    val enabled: Boolean = true,
    val isPrn: Boolean = false,
    val prnMaxPerDay: Int? = null,
    val taperEnabled: Boolean = false
)