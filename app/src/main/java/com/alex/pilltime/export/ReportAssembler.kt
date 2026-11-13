package com.alex.pilltime.export

import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.data.entity.TaperStep
import com.alex.pilltime.domain.buildDaySlots
import com.alex.pilltime.ui.history.HistoryDayItem
import com.alex.pilltime.ui.history.HistoryStatus
import com.alex.pilltime.ui.history.mergeDayRecords
import com.alex.pilltime.ui.history.summarize
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 报告内的一天：日期 + 当天全部记录。 */
data class ReportDay(
    val date: LocalDate,
    val rows: List<ReportRow>
)

/** 报告内的一条服药记录，文案已本地化。 */
data class ReportRow(
    val timeText: String,
    val medicineName: String,
    val doseText: String,
    val statusText: String
)

/** 当前用药：药名 + 规则整句 + 剩余量（无库存数据时为空）。 */
data class ReportMedicine(
    val name: String,
    val ruleText: String,
    val remainingText: String?
)

/** 服药概况：只给次数，不给百分比与评价。total 恒等于其余五项之和。 */
data class ReportCounts(
    val onTime: Int = 0,
    val late: Int = 0,
    val unconfirmed: Int = 0,
    val uncertain: Int = 0,
    val skipped: Int = 0,
    val total: Int = 0
)

/** PDF 生成所需的全部数据，纯 JVM 可构造，便于单元测试。 */
data class ReportData(
    val generatedAt: LocalDate,
    val rangeFrom: LocalDate,
    val rangeTo: LocalDate,
    val medicines: List<ReportMedicine>,
    val counts: ReportCounts,
    val days: List<ReportDay>,
    val anomalies: List<String>
)

/** 时段划分，仅用于生成事实性的未确认提示。 */
enum class ReportPeriod { MORNING, AFTERNOON, EVENING }

/**
 * 报告文案来源。实现放在 Android 层读取 strings.xml，组装逻辑本身保持纯 JVM。
 */
interface ReportTextProvider {
    fun ruleText(schedule: Schedule, doseUnit: String): String
    fun doseText(qtyX10: Int, doseUnit: String): String
    fun timeText(time: LocalTime): String
    fun statusText(status: HistoryStatus): String
    fun remaining(stockText: String): String
    fun ruleSeparator(): String
    fun unconfirmedAnomaly(period: ReportPeriod, count: Int): String
    fun backfilledAnomaly(count: Int): String
}

/** 组装结果：范围为空的信号，或可直接生成 PDF 的数据。 */
sealed class ReportAssembly {
    data object Empty : ReportAssembly()
    data class Ready(val data: ReportData) : ReportAssembly()
}

/**
 * 按范围逐日重建应服 slot，再与记录合并，口径与历史页完全一致。
 * 统计总数直接取合并后的条目数，保证五类次数之和 = 应服总次数。
 */
fun assembleReport(
    rangeDays: Int,
    today: LocalDate,
    zone: ZoneId,
    settings: AppSettings,
    medicines: List<Medicine>,
    schedules: List<Schedule>,
    taperSteps: Map<Long, List<TaperStep>>,
    logs: List<DoseLog>,
    stockX10ByMedicine: Map<Long, Int>,
    texts: ReportTextProvider,
    now: Instant
): ReportAssembly {
    val days = if (rangeDays > 0) rangeDays else 1
    val from = today.minusDays(days.toLong() - 1)
    val enabledSchedules = schedules.filter { it.enabled }
    val medicinesById = medicines.associateBy { it.id }

    val items = mutableListOf<HistoryDayItem>()
    var date = from
    while (!date.isAfter(today)) {
        val slots = buildDaySlots(date, enabledSchedules, taperSteps, settings, zone)
        items += mergeDayRecords(date, slots, logs, now)
        date = date.plusDays(1)
    }

    // 无任何应服时刻且无记录时不生成文件
    if (items.isEmpty()) return ReportAssembly.Empty

    val counts = summarize(items)
    val reportDays = items.groupBy { it.date }
        .toSortedMap()
        .map { (day, dayItems) ->
            ReportDay(
                date = day,
                rows = dayItems.sortedBy { it.plannedAt }.map { item ->
                    val medicine = medicinesById[item.medicineId]
                    ReportRow(
                        timeText = texts.timeText(
                            item.plannedAt.atZone(zone).toLocalTime()
                        ),
                        medicineName = medicine?.name.orEmpty(),
                        doseText = texts.doseText(item.doseQtyX10, medicine?.doseUnit.orEmpty()),
                        statusText = texts.statusText(item.status)
                    )
                }
            )
        }

    return ReportAssembly.Ready(
        ReportData(
            generatedAt = today,
            rangeFrom = from,
            rangeTo = today,
            medicines = buildReportMedicines(
                medicines = medicines,
                enabledSchedules = enabledSchedules,
                stockX10ByMedicine = stockX10ByMedicine,
                texts = texts
            ),
            counts = ReportCounts(
                onTime = counts.onTime,
                late = counts.late,
                unconfirmed = counts.unconfirmed,
                uncertain = counts.uncertain,
                skipped = counts.skipped,
                total = counts.total
            ),
            days = reportDays,
            anomalies = buildAnomalies(items, zone, texts)
        )
    )
}

private fun buildReportMedicines(
    medicines: List<Medicine>,
    enabledSchedules: List<Schedule>,
    stockX10ByMedicine: Map<Long, Int>,
    texts: ReportTextProvider
): List<ReportMedicine> = medicines.mapNotNull { medicine ->
    val rules = enabledSchedules
        .filter { it.medicineId == medicine.id }
        .map { texts.ruleText(it, medicine.doseUnit) }
    if (rules.isEmpty()) return@mapNotNull null

    val remainingText = stockX10ByMedicine[medicine.id]
        ?.takeIf { it >= 0 }
        ?.let { texts.remaining(texts.doseText(it, medicine.doseUnit)) }

    ReportMedicine(
        name = medicine.name,
        ruleText = rules.joinToString(texts.ruleSeparator()),
        remainingText = remainingText
    )
}

/** 异常提示只陈述事实：各时段未确认次数、事后补记次数，不做任何评价。 */
private fun buildAnomalies(
    items: List<HistoryDayItem>,
    zone: ZoneId,
    texts: ReportTextProvider
): List<String> {
    val anomalies = mutableListOf<String>()
    for (period in ReportPeriod.entries) {
        val count = items.count {
            it.status == HistoryStatus.UNCONFIRMED && periodOf(it.plannedAt, zone) == period
        }
        if (count > 0) anomalies += texts.unconfirmedAnomaly(period, count)
    }

    val backfilled = items.count {
        (it.status == HistoryStatus.ON_TIME || it.status == HistoryStatus.LATE) && !it.certain
    }
    if (backfilled > 0) anomalies += texts.backfilledAnomaly(backfilled)
    return anomalies
}

private fun periodOf(plannedAt: Instant, zone: ZoneId): ReportPeriod {
    val hour = plannedAt.atZone(zone).hour
    return when {
        hour < 12 -> ReportPeriod.MORNING
        hour < 18 -> ReportPeriod.AFTERNOON
        else -> ReportPeriod.EVENING
    }
}

/** 文件名只含本地化前缀与日期，不含病名、药名等敏感信息。 */
fun reportFileName(prefix: String, date: LocalDate): String =
    "${prefix}_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.pdf"