package com.medtimer.app.export

import android.content.Context
import android.text.format.DateFormat
import com.medtimer.app.R
import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.ui.history.HistoryStatus
import com.medtimer.app.ui.medicine.MedicineTextFormatter
import com.medtimer.app.ui.text.formatDoseText
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 报告文案的 Android 实现：规则整句复用 MedicineTextFormatter（内部走 describeRule），
 * 其余状态词、提示语全部取自 strings.xml。
 */
class AndroidReportTextProvider(private val context: Context) : ReportTextProvider {

    private val medicineTexts = MedicineTextFormatter(context)

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        context.resources.configuration.locales[0]
    )

    override fun ruleText(schedule: Schedule, doseUnit: String): String =
        medicineTexts.ruleText(schedule, doseUnit)

    override fun doseText(qtyX10: Int, doseUnit: String): String =
        formatDoseText(context, qtyX10, doseUnit)

    override fun timeText(time: LocalTime): String = timeFormatter.format(time)

    override fun statusText(status: HistoryStatus): String = context.getString(
        when (status) {
            HistoryStatus.ON_TIME -> R.string.export_status_on_time
            HistoryStatus.LATE -> R.string.export_status_late
            HistoryStatus.UNCONFIRMED -> R.string.export_status_unconfirmed
            HistoryStatus.UNCERTAIN -> R.string.export_status_uncertain
            HistoryStatus.SKIPPED -> R.string.export_status_skipped
        }
    )

    override fun remaining(stockText: String): String =
        context.getString(R.string.export_remaining, stockText)

    override fun ruleSeparator(): String = context.getString(R.string.export_rule_separator)

    override fun unconfirmedAnomaly(period: ReportPeriod, count: Int): String =
        context.getString(
            when (period) {
                ReportPeriod.MORNING -> R.string.export_anomaly_morning
                ReportPeriod.AFTERNOON -> R.string.export_anomaly_afternoon
                ReportPeriod.EVENING -> R.string.export_anomaly_evening
            },
            count
        )

    override fun backfilledAnomaly(count: Int): String =
        context.getString(R.string.export_anomaly_backfilled, count)
}