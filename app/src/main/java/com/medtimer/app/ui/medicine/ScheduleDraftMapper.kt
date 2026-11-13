package com.medtimer.app.ui.medicine

import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.domain.formatDoseQty
import com.medtimer.app.domain.model.AnchorType
import com.medtimer.app.domain.model.MealType
import java.time.LocalDate
import java.util.Locale

/** 计划节奏：界面上四选一。 */
enum class RecurrenceKind { DAILY, EVERY_N_DAYS, WEEKLY, MONTHLY }

/** 计划表单校验错误。 */
enum class ScheduleFormError {
    DOSE_INVALID,
    DOSE_NOT_INTEGER,
    EVERY_N_DAYS_INVALID,
    WEEKDAYS_EMPTY,
    MONTHLY_DAY_INVALID,
    OFFSET_INVALID,
    WINDOW_INVALID,
    END_DATE_INVALID,
    PRN_LIMIT_INVALID,
    TAPER_EMPTY,
    TAPER_DATE_DUPLICATE,
    TAPER_DOSE_INVALID,
    TAPER_DOSE_NOT_INTEGER
}

/** 递减方案的一步草稿：起始日期 + 剂量文本。 */
data class TaperStepDraft(
    val startDate: LocalDate,
    val doseText: String
)

/** 递减步骤解析结果：成功时 [steps] 按日期升序；失败时 [errors] 非空。 */
data class TaperBuildResult(
    val steps: List<TaperStepInput> = emptyList(),
    val errors: Set<ScheduleFormError> = emptySet()
)

/** 校验通过的递减步骤（剂量已转成 ×10 整数）。 */
data class TaperStepInput(
    val startDate: LocalDate,
    val doseQtyX10: Int
)

/**
 * 计划编辑表单的原始输入。数值字段保留为文本，便于边输入边校验。
 * [weekdays] 用 ISO 星期编号（周一 = 1 … 周日 = 7）。
 */
data class ScheduleForm(
    val anchorType: AnchorType = AnchorType.CLOCK,
    val clockHour: Int = 8,
    val clockMinute: Int = 0,
    val mealType: MealType = MealType.BREAKFAST,
    val offsetMinText: String = "0",
    val recurrence: RecurrenceKind = RecurrenceKind.DAILY,
    val everyNDaysText: String = "2",
    val weekdays: Set<Int> = emptySet(),
    val monthlyDayText: String = "1",
    val startDate: LocalDate = LocalDate.now(),
    val longTerm: Boolean = true,
    val endDate: LocalDate? = null,
    val doseText: String = "1",
    val earlyText: String = "30",
    val lateText: String = "60",
    val isPrn: Boolean = false,
    val prnMaxPerDayText: String = "",
    val enabled: Boolean = true
)

/** 转换结果：成功时 [schedule] 非空，失败时 [errors] 非空。 */
data class ScheduleBuildResult(
    val schedule: Schedule?,
    val errors: Set<ScheduleFormError> = emptySet()
)

private val DOSE_PATTERN = Regex("^(\\d{1,7})(?:\\.(\\d))?$")

/**
 * 剂量文本转 ×10 整数：只接受最多一位小数，全部用整数运算，避免浮点误差。
 * "0.5" → 5，"1" → 10，"1.5" → 15；空串、负数、多位小数与 0 都返回 null。
 */
fun parseDoseX10(input: String): Int? {
    val text = input.trim().replace(',', '.')
    val match = DOSE_PATTERN.matchEntire(text) ?: return null
    val whole = match.groupValues[1].toLongOrNull() ?: return null
    val tenth = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
    val total = whole * 10 + tenth
    if (total <= 0) return null
    return total.toInt()
}

/**
 * 递减步骤校验：至少 1 步、日期唯一、剂量合法；不可掰开药品要求整单位。
 * 返回的步骤按起始日期升序，便于存储与回显。
 */
fun buildTaperSteps(
    drafts: List<TaperStepDraft>,
    splittable: Boolean
): TaperBuildResult {
    if (drafts.isEmpty()) {
        return TaperBuildResult(errors = setOf(ScheduleFormError.TAPER_EMPTY))
    }
    val errors = mutableSetOf<ScheduleFormError>()
    val steps = mutableListOf<TaperStepInput>()
    for (draft in drafts) {
        val dose = parseDoseX10(draft.doseText)
        when {
            dose == null -> errors += ScheduleFormError.TAPER_DOSE_INVALID
            !splittable && dose % 10 != 0 -> errors += ScheduleFormError.TAPER_DOSE_NOT_INTEGER
            else -> steps += TaperStepInput(draft.startDate, dose)
        }
    }
    if (drafts.map { it.startDate }.distinct().size != drafts.size) {
        errors += ScheduleFormError.TAPER_DATE_DUPLICATE
    }
    return TaperBuildResult(steps = steps.sortedBy { it.startDate }, errors = errors)
}

/** 星期集合转周掩码：周一 = 1，周二 = 2 … 周日 = 64；空集合或越界返回 null。 */
fun toWeekdaysMask(days: Set<Int>): Int? {
    if (days.isEmpty()) return null
    if (days.any { it !in 1..7 }) return null
    return days.fold(0) { mask, day -> mask or (1 shl (day - 1)) }
}

/** 周掩码转星期集合。 */
fun maskToWeekdays(mask: Int?): Set<Int> =
    if (mask == null) {
        emptySet()
    } else {
        (1..7).filter { mask and (1 shl (it - 1)) != 0 }.toSet()
    }

/**
 * 表单转计划实体。校验失败时返回 null 与错误集合，不做任何静默取整或截断。
 * [splittable] 为 false 时剂量必须是整单位（×10 后能被 10 整除）。
 */
fun draftToSchedule(
    form: ScheduleForm,
    medicineId: Long,
    splittable: Boolean,
    scheduleId: Long = 0L,
    taperEnabled: Boolean = false
): ScheduleBuildResult {
    val errors = mutableSetOf<ScheduleFormError>()

    val dose = parseDoseX10(form.doseText)
    if (dose == null) {
        errors += ScheduleFormError.DOSE_INVALID
    } else if (!splittable && dose % 10 != 0) {
        errors += ScheduleFormError.DOSE_NOT_INTEGER
    }

    val offset = form.offsetMinText.trim().toIntOrNull()
    if (!form.isPrn && form.anchorType == AnchorType.MEAL && offset == null) {
        errors += ScheduleFormError.OFFSET_INVALID
    }

    val early = form.earlyText.trim().toIntOrNull()?.takeIf { it >= 0 }
    val late = form.lateText.trim().toIntOrNull()?.takeIf { it >= 0 }
    if (early == null || late == null) {
        errors += ScheduleFormError.WINDOW_INVALID
    }

    val prnMax = if (form.prnMaxPerDayText.isBlank()) {
        null
    } else {
        form.prnMaxPerDayText.trim().toIntOrNull()?.takeIf { it > 0 }
    }
    if (form.isPrn && form.prnMaxPerDayText.isNotBlank() && prnMax == null) {
        errors += ScheduleFormError.PRN_LIMIT_INVALID
    }

    var everyNDays = 1
    var weekdaysMask: Int? = null
    var monthlyDay: Int? = null
    // PRN 与定时节奏互斥：按需用药时不写入任何节奏字段
    if (!form.isPrn) {
        when (form.recurrence) {
            RecurrenceKind.DAILY -> Unit
            RecurrenceKind.EVERY_N_DAYS -> {
                val n = form.everyNDaysText.trim().toIntOrNull()
                if (n == null || n < 2) {
                    errors += ScheduleFormError.EVERY_N_DAYS_INVALID
                } else {
                    everyNDays = n
                }
            }
            RecurrenceKind.WEEKLY -> {
                val mask = toWeekdaysMask(form.weekdays)
                if (mask == null) {
                    errors += ScheduleFormError.WEEKDAYS_EMPTY
                } else {
                    weekdaysMask = mask
                }
            }
            RecurrenceKind.MONTHLY -> {
                val day = form.monthlyDayText.trim().toIntOrNull()?.takeIf { it in 1..31 }
                if (day == null) {
                    errors += ScheduleFormError.MONTHLY_DAY_INVALID
                } else {
                    monthlyDay = day
                }
            }
        }
    }

    val endDate = if (form.longTerm) null else form.endDate
    if (endDate == null && !form.longTerm) {
        errors += ScheduleFormError.END_DATE_INVALID
    }
    if (endDate != null && endDate.isBefore(form.startDate)) {
        errors += ScheduleFormError.END_DATE_INVALID
    }

    val doseX10 = dose ?: return ScheduleBuildResult(null, errors)
    if (errors.isNotEmpty()) return ScheduleBuildResult(null, errors)

    val isClock = form.isPrn || form.anchorType == AnchorType.CLOCK
    val schedule = Schedule(
        id = scheduleId,
        medicineId = medicineId,
        anchorType = if (form.isPrn) AnchorType.CLOCK else form.anchorType,
        clockHour = if (isClock) form.clockHour else null,
        clockMinute = if (isClock) form.clockMinute else null,
        mealType = if (!isClock) form.mealType else null,
        offsetMin = if (!isClock) offset ?: 0 else 0,
        doseQtyX10 = doseX10,
        startDate = form.startDate,
        endDate = endDate,
        everyNDays = everyNDays,
        weekdaysMask = weekdaysMask,
        monthlyDay = monthlyDay,
        earlyMinutes = early ?: 0,
        lateMinutes = late ?: 0,
        enabled = form.enabled,
        isPrn = form.isPrn,
        prnMaxPerDay = if (form.isPrn) prnMax else null,
        taperEnabled = taperEnabled
    )
    return ScheduleBuildResult(schedule)
}

/** 计划实体回填表单；与 [draftToSchedule] 互逆。 */
fun scheduleToDraft(schedule: Schedule): ScheduleForm {
    val recurrence = when {
        schedule.weekdaysMask != null -> RecurrenceKind.WEEKLY
        schedule.monthlyDay != null -> RecurrenceKind.MONTHLY
        schedule.everyNDays > 1 -> RecurrenceKind.EVERY_N_DAYS
        else -> RecurrenceKind.DAILY
    }
    return ScheduleForm(
        anchorType = schedule.anchorType,
        clockHour = schedule.clockHour ?: 8,
        clockMinute = schedule.clockMinute ?: 0,
        mealType = schedule.mealType ?: MealType.BREAKFAST,
        offsetMinText = schedule.offsetMin.toString(),
        recurrence = recurrence,
        everyNDaysText = schedule.everyNDays.coerceAtLeast(2).toString(),
        weekdays = maskToWeekdays(schedule.weekdaysMask),
        monthlyDayText = (schedule.monthlyDay ?: 1).toString(),
        startDate = schedule.startDate,
        longTerm = schedule.endDate == null,
        endDate = schedule.endDate,
        doseText = formatDoseQty(schedule.doseQtyX10, Locale.US),
        earlyText = schedule.earlyMinutes.toString(),
        lateText = schedule.lateMinutes.toString(),
        isPrn = schedule.isPrn,
        prnMaxPerDayText = schedule.prnMaxPerDay?.toString().orEmpty(),
        enabled = schedule.enabled
    )
}

/**
 * 规则预览用：对非法输入按默认值兜底，只求能立刻拼出一句话，不做持久化。
 */
fun draftToPreviewSchedule(form: ScheduleForm, medicineId: Long): Schedule {
    val recurrence = if (form.isPrn) RecurrenceKind.DAILY else form.recurrence
    val isClock = form.isPrn || form.anchorType == AnchorType.CLOCK
    val offset = form.offsetMinText.trim().toIntOrNull() ?: 0
    val everyN = form.everyNDaysText.trim().toIntOrNull()?.coerceAtLeast(2) ?: 2
    return Schedule(
        medicineId = medicineId,
        anchorType = if (form.isPrn) AnchorType.CLOCK else form.anchorType,
        clockHour = if (isClock) form.clockHour else null,
        clockMinute = if (isClock) form.clockMinute else null,
        mealType = if (isClock) null else form.mealType,
        offsetMin = if (isClock) 0 else offset,
        doseQtyX10 = parseDoseX10(form.doseText) ?: 10,
        startDate = form.startDate,
        endDate = if (form.longTerm) null else form.endDate,
        everyNDays = if (recurrence == RecurrenceKind.EVERY_N_DAYS) everyN else 1,
        weekdaysMask = if (recurrence == RecurrenceKind.WEEKLY) {
            toWeekdaysMask(form.weekdays) ?: 1
        } else {
            null
        },
        monthlyDay = if (recurrence == RecurrenceKind.MONTHLY) {
            form.monthlyDayText.trim().toIntOrNull()?.takeIf { it in 1..31 } ?: 1
        } else {
            null
        },
        earlyMinutes = form.earlyText.trim().toIntOrNull()?.takeIf { it >= 0 } ?: 30,
        lateMinutes = form.lateText.trim().toIntOrNull()?.takeIf { it >= 0 } ?: 60,
        enabled = form.enabled,
        isPrn = form.isPrn,
        prnMaxPerDay = if (form.isPrn) form.prnMaxPerDayText.trim().toIntOrNull() else null
    )
}