package com.alex.pilltime.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alex.pilltime.domain.model.MedicineForm

/**
 * 药品。
 * 剂量一律使用最小单位 x10 的整数（doseQtyX10），不存浮点剂量。
 */
@Entity(tableName = "medicine", indices = [Index("profileId")])
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1,
    val name: String,
    val form: MedicineForm,
    val specValue: Double,          // 规格含量，如 500
    val specUnit: String,           // mg
    val doseUnit: String,           // 片 / 粒 / ml / 滴 / U
    val splittable: Boolean,
    val packSize: Int,
    val packUnit: String,
    val imagePath: String? = null,
    val thumbPath: String? = null,
    val autoDeductStock: Boolean = true,
    val note: String? = null
)