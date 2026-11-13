package com.medtimer.app.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Room 类型转换器。枚举由 Room 默认按 name 存储，无需转换器。
 * 转换器统一接受可空值，保证读写对称。
 */
class Converters {

    // LocalDate <-> epochDay
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    // LocalTime <-> 当天分钟数
    @TypeConverter
    fun localTimeToMinuteOfDay(value: LocalTime?): Int? = value?.let { it.hour * 60 + it.minute }

    @TypeConverter
    fun minuteOfDayToLocalTime(value: Int?): LocalTime? =
        value?.let { LocalTime.of(it / 60, it % 60) }

    // Instant <-> epochMilli
    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    // LocalDateTime <-> epochSecond（按 UTC，预留扩展）
    @TypeConverter
    fun localDateTimeToEpochSecond(value: LocalDateTime?): Long? =
        value?.toEpochSecond(ZoneOffset.UTC)

    @TypeConverter
    fun epochSecondToLocalDateTime(value: Long?): LocalDateTime? =
        value?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }
}