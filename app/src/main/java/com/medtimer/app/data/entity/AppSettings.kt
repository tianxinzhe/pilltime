package com.medtimer.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalTime

/**
 * 用户设置（单行表，id 固定为 1）。
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val breakfast: LocalTime = LocalTime.of(7, 30),
    val lunch: LocalTime = LocalTime.of(12, 0),
    val dinner: LocalTime = LocalTime.of(18, 30),
    val bedtime: LocalTime = LocalTime.of(22, 0),
    val mergeWindowMinutes: Int = 10,
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(7, 0),
    val defaultEarlyMinutes: Int = 30,
    val defaultLateMinutes: Int = 60,
    val allPaused: Boolean = false,
    val pausedUntil: Instant? = null,
    val onboardingDone: Boolean = false,
    val activeProfileId: Long = 1,
    val lowStockDaysThreshold: Int = 7,
    val appLanguage: String = "system"   // system / en / zh-CN / es / pt-BR ...
)