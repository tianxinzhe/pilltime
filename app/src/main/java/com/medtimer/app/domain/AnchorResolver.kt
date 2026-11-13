package com.medtimer.app.domain

import com.medtimer.app.data.entity.AppSettings
import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.domain.model.AnchorType
import com.medtimer.app.domain.model.MealType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** CLOCK 锚点缺省时刻。 */
private val DEFAULT_CLOCK_TIME: LocalTime = LocalTime.of(8, 0)

/** 餐次对应的饭点时刻。 */
fun AppSettings.timeOf(meal: MealType): LocalTime = when (meal) {
    MealType.BREAKFAST -> breakfast
    MealType.LUNCH -> lunch
    MealType.DINNER -> dinner
    MealType.BEDTIME -> bedtime
}

/**
 * 解析计划在 [date] 当天的计划时刻。
 * MEAL 锚点加偏移后允许跨日（如 23:50 + 30 分钟 → 次日 00:20），不得截断回当天。
 * [mealType] 为空时回退到 08:00。
 */
fun Schedule.resolveTime(date: LocalDate, settings: AppSettings): LocalDateTime {
    return when (anchorType) {
        AnchorType.CLOCK -> date.atTime(
            clockHour ?: DEFAULT_CLOCK_TIME.hour,
            clockMinute ?: DEFAULT_CLOCK_TIME.minute
        )
        AnchorType.MEAL -> date
            .atTime(mealType?.let { settings.timeOf(it) } ?: DEFAULT_CLOCK_TIME)
            .plusMinutes(offsetMin.toLong())
    }
}

/** 解析计划在 [date] 当天对应的绝对时刻。 */
fun Schedule.resolveInstant(date: LocalDate, settings: AppSettings, zone: ZoneId): Instant =
    resolveTime(date, settings).atZone(zone).toInstant()