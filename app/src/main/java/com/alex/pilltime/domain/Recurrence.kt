package com.alex.pilltime.domain

import com.alex.pilltime.data.entity.Schedule
import java.time.LocalDate

/**
 * 计划在指定日期是否应服用。
 * 节奏互斥，优先级：weekdaysMask > monthlyDay > everyNDays。
 */
fun Schedule.isDueOn(date: LocalDate): Boolean {
    if (!enabled) return false
    if (date < startDate) return false
    endDate?.let { if (date > it) return false }

    weekdaysMask?.let { mask ->
        val bit = 1 shl (date.dayOfWeek.value - 1) // 周一 = bit0，周日 = bit6
        return (mask and bit) != 0
    }
    monthlyDay?.let { day ->
        // 31 号在小月取当月最后一天
        return date.dayOfMonth == minOf(day, date.lengthOfMonth())
    }
    // 隔天必须用 epochDay 差值取模，禁止 dayOfYear % N
    val step = everyNDays.coerceAtLeast(1)
    return Math.floorMod(date.toEpochDay() - startDate.toEpochDay(), step.toLong()) == 0L
}

/**
 * 从 [from]（含）起向后查找第一个应服日期，最多向后看 [maxDaysAhead] 天。
 * 找不到返回 null。
 */
fun Schedule.nextDueDate(from: LocalDate, maxDaysAhead: Int = 400): LocalDate? {
    if (maxDaysAhead < 0) return null
    for (offset in 0..maxDaysAhead) {
        val date = from.plusDays(offset.toLong())
        if (isDueOn(date)) return date
    }
    return null
}