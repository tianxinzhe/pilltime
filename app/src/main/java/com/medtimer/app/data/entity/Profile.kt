package com.medtimer.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 服药人档案。
 */
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false
)