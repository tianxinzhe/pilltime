package com.alex.pilltime.domain

import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.data.entity.TaperStep
import java.time.LocalDate

/**
 * 取计划在 [date] 当天的剂量（最小单位 x10）。
 * 未开启递减或无步骤时用计划剂量；否则取已生效步骤中 startDate 最大的一步；
 * 所有步骤都晚于 date 时取最早一步的剂量。
 */
fun Schedule.doseQtyX10On(date: LocalDate, taperSteps: List<TaperStep>): Int {
    if (!taperEnabled || taperSteps.isEmpty()) return doseQtyX10
    val applicable = taperSteps
        .filter { !it.startDate.isAfter(date) }
        .maxByOrNull { it.startDate }
    return applicable?.doseQtyX10
        ?: taperSteps.minByOrNull { it.startDate }?.doseQtyX10
        ?: doseQtyX10
}

/** 界面回显用的递减摘要：当前阶段剂量与阶段总数。 */
data class TaperDisplay(val currentDoseX10: Int, val stageCount: Int)

/**
 * 取 [date] 当天的递减摘要；未启用或没有任何步骤时返回 null，界面不展示递减行。
 */
fun Schedule.taperDisplay(date: LocalDate, taperSteps: List<TaperStep>): TaperDisplay? =
    if (!taperEnabled || taperSteps.isEmpty()) {
        null
    } else {
        TaperDisplay(
            currentDoseX10 = doseQtyX10On(date, taperSteps),
            stageCount = taperSteps.size
        )
    }