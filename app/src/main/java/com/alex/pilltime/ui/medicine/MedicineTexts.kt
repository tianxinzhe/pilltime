package com.alex.pilltime.ui.medicine

import android.content.Context
import android.text.format.DateFormat
import com.alex.pilltime.R
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.data.entity.TaperStep
import com.alex.pilltime.domain.RuleTextInput
import com.alex.pilltime.domain.describeRule
import com.alex.pilltime.domain.model.AnchorType
import com.alex.pilltime.domain.taperDisplay
import com.alex.pilltime.ui.text.AndroidRuleTextResources
import com.alex.pilltime.ui.text.formatDoseText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 药品 / 计划页面共用的文案格式化：规则整句、时刻、日期、剂量。
 * 所有模板均取自 strings.xml，时间格式跟随系统 12/24 小时制。
 */
class MedicineTextFormatter(private val context: Context) {

    private val ruleResources = AndroidRuleTextResources(context)
    private val locale = context.resources.configuration.locales[0]

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
            locale
        )

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    fun timeText(hour: Int, minute: Int): String = timeFormatter.format(LocalTime.of(hour, minute))

    fun dateText(date: LocalDate): String = dateFormatter.format(date)

    fun doseText(qtyX10: Int, doseUnit: String): String = formatDoseText(context, qtyX10, doseUnit)

    /** 规则的整句回显；按需用药单独成句，避免显示成定时规则。 */
    fun ruleText(schedule: Schedule, doseUnit: String): String {
        val dose = doseText(schedule.doseQtyX10, doseUnit)
        if (schedule.isPrn) {
            return context.getString(R.string.rule_prn, dose)
        }
        val timeText = if (schedule.anchorType == AnchorType.CLOCK) {
            timeText(schedule.clockHour ?: 8, schedule.clockMinute ?: 0)
        } else {
            ""
        }
        return schedule.describeRule(ruleResources, RuleTextInput(dose, timeText))
    }

    /** 递减方案回显：当前阶段剂量与阶段数；未启用或没有步骤时返回 null。 */
    fun taperText(schedule: Schedule, steps: List<TaperStep>, doseUnit: String): String? {
        val display = schedule.taperDisplay(LocalDate.now(), steps) ?: return null
        return context.getString(
            R.string.medicine_taper_summary,
            doseText(display.currentDoseX10, doseUnit),
            display.stageCount
        )
    }
}