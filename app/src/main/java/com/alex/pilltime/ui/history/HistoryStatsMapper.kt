package com.alex.pilltime.ui.history

import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.domain.DoseSlot
import com.alex.pilltime.domain.model.DoseStatus
import com.alex.pilltime.domain.windowEnd
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 历史统计口径：按时 / 延迟 / 未确认三类，记不清与跳过单列。
 * 五类之和恒等于应服总次数，不再额外给出百分比结论。
 */
enum class HistoryStatus { ON_TIME, LATE, UNCONFIRMED, UNCERTAIN, SKIPPED }

/** 一条历史记录（纯数据，不含文案）。 */
data class HistoryDayItem(
    /** 该 slot 所属的计划日期，按日分组时使用。 */
    val date: LocalDate,
    val scheduleId: Long,
    val medicineId: Long,
    val plannedAt: Instant,
    val doseQtyX10: Int,
    val status: HistoryStatus,
    /** 仅延迟记录有值，单位分钟。 */
    val delayMinutes: Int?,
    /** 补记或记不清为 false，用于界面区分来源。 */
    val certain: Boolean
)

/** 汇总结果。total 始终等于其余五项之和。 */
data class HistoryCounts(
    val onTime: Int = 0,
    val late: Int = 0,
    val unconfirmed: Int = 0,
    val uncertain: Int = 0,
    val skipped: Int = 0,
    val total: Int = 0
)

/** 历史页文案来源，由 UI 层用 strings.xml 实现，映射层不硬编码文案。 */
interface HistoryTextProvider {
    fun date(date: LocalDate): String
    fun time(plannedAt: Instant): String
    fun dose(qtyX10: Int, doseUnit: String): String
    fun status(status: HistoryStatus): String
    fun late(minutes: Int): String
}

/**
 * 把某一天的应服 slot 与服药记录按 (scheduleId, plannedAt) 合并。
 *
 * 口径：
 * - 记录状态直接映射；作废记录（voidedAt 非空）视为不存在。
 * - 无记录且已过提醒窗口 → 未确认；尚未到窗口终点 → 不属于任何统计项，直接丢弃。
 * - 未确认包含“已写入的 MISSED”和“已过窗口但没有记录”两种来源。
 * - 已知限制：slot 由当前启用的计划实时推导，若过去计划被修改或删除，
 *   历史时刻会按当前计划重算，未匹配到 slot 的旧记录不会出现在列表中。
 */
fun mergeDayRecords(
    day: LocalDate,
    slots: List<DoseSlot>,
    logs: List<DoseLog>,
    now: Instant
): List<HistoryDayItem> {
    // 同一 slot 只认一条未作废记录，重复写入时取最新一条
    val logBySlot = logs.asSequence()
        .filter { it.voidedAt == null }
        .groupBy { it.scheduleId to it.plannedAt }
        .mapValues { (_, entries) -> entries.maxByOrNull { it.id }!! }

    return slots.mapNotNull { slot ->
        val log = logBySlot[slot.scheduleId to slot.plannedAt]
        val windowPassed = !now.isBefore(windowEnd(slot.plannedAt, slot.lateMinutes))
        val status = when {
            log != null -> when (log.status) {
                DoseStatus.TAKEN -> HistoryStatus.ON_TIME
                DoseStatus.LATE -> HistoryStatus.LATE
                DoseStatus.MISSED -> HistoryStatus.UNCONFIRMED
                DoseStatus.SKIPPED -> HistoryStatus.SKIPPED
                DoseStatus.UNCERTAIN -> HistoryStatus.UNCERTAIN
                // PENDING / DUE 是计算态，窗口结束后按未确认处理
                DoseStatus.PENDING, DoseStatus.DUE -> {
                    if (windowPassed) HistoryStatus.UNCONFIRMED else return@mapNotNull null
                }
            }
            windowPassed -> HistoryStatus.UNCONFIRMED
            else -> return@mapNotNull null
        }

        val delay = when (status) {
            HistoryStatus.LATE -> log?.delayMinutes ?: log?.actedAt?.let {
                Duration.between(slot.plannedAt, it).toMinutes().toInt()
            }
            else -> null
        }

        HistoryDayItem(
            date = day,
            scheduleId = slot.scheduleId,
            medicineId = slot.medicineId,
            plannedAt = slot.plannedAt,
            doseQtyX10 = slot.doseQtyX10,
            status = status,
            delayMinutes = delay,
            certain = log?.certain ?: true
        )
    }.sortedBy { it.plannedAt }
}

/** 统计汇总；total 直接取条目数，保证三类之和与应服总次数一致（TC-EXP-09）。 */
fun summarize(items: List<HistoryDayItem>): HistoryCounts = HistoryCounts(
    onTime = items.count { it.status == HistoryStatus.ON_TIME },
    late = items.count { it.status == HistoryStatus.LATE },
    unconfirmed = items.count { it.status == HistoryStatus.UNCONFIRMED },
    uncertain = items.count { it.status == HistoryStatus.UNCERTAIN },
    skipped = items.count { it.status == HistoryStatus.SKIPPED },
    total = items.size
)