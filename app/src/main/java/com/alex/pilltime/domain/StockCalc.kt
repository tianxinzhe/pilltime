package com.alex.pilltime.domain

import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.data.entity.TaperStep
import java.time.LocalDate

/**
 * 未来 [days] 天（含今天）按实际规则逐日求和的总消耗，单位 x10。
 * 隔天药不会被算成每天一次；PRN 不纳入预测。
 */
fun futureConsumptionX10(
    schedules: List<Schedule>,
    today: LocalDate,
    days: Int = 30,
    taperStepsBySchedule: Map<Long, List<TaperStep>> = emptyMap()
): Int {
    if (days <= 0) return 0
    var total = 0L
    for (offset in 0 until days) {
        val date = today.plusDays(offset.toLong())
        for (schedule in schedules) {
            if (schedule.isPrn || !schedule.isDueOn(date)) continue
            total += schedule.doseQtyX10On(date, taperStepsBySchedule[schedule.id].orEmpty())
        }
    }
    return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/**
 * 剩余可用天数 = 库存 / 日均消耗；日均消耗 = 未来 [horizonDays] 天总消耗 / horizonDays。
 * 库存为 0 返回 0.0，未来无消耗返回正无穷。
 */
fun daysLeft(
    stockQtyX10: Int,
    schedules: List<Schedule>,
    today: LocalDate,
    horizonDays: Int = 30,
    taperStepsBySchedule: Map<Long, List<TaperStep>> = emptyMap()
): Double {
    if (stockQtyX10 <= 0) return 0.0
    val consumption = futureConsumptionX10(schedules, today, horizonDays, taperStepsBySchedule)
    if (consumption <= 0) return Double.POSITIVE_INFINITY
    val perDay = consumption.toDouble() / horizonDays
    return stockQtyX10 / perDay
}

/** 剩余天数低于阈值即视为库存偏低；无消耗（无穷大）不算低库存。 */
fun isLowStock(daysLeft: Double, thresholdDays: Int): Boolean =
    daysLeft.isFinite() && daysLeft < thresholdDays